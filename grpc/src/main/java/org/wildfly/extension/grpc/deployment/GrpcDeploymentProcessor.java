/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.grpc.BindableService;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.modules.Module;
import org.wildfly.extension.grpc.GrpcConfigurationConstants;
import org.wildfly.extension.grpc.GrpcHttpHandler;
import org.wildfly.extension.grpc.GrpcServerService;
import org.wildfly.extension.grpc._private.GrpcLogger;
import org.wildfly.extension.requestcontroller.ControlPointService;
import org.wildfly.extension.requestcontroller.RequestControllerActivationMarker;
import org.wildfly.extension.undertow.deployment.UndertowAttachments;

/**
 * Creates a gRPC server for deployments containing gRPC services.
 *
 * <p>This processor:
 * <ul>
 *   <li>Scans for classes implementing {@link BindableService}</li>
 *   <li>Creates a dedicated {@link GrpcServerService} instance for the deployment</li>
 *   <li>Instantiates and registers discovered services</li>
 *   <li>Attaches the gRPC HTTP handler to the Undertow handler chain</li>
 *   <li>Ensures clean shutdown and classloader leak prevention on undeploy</li>
 * </ul>
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class GrpcDeploymentProcessor implements DeploymentUnitProcessor {

    private static final DotName BINDABLE_SERVICE = DotName.createSimple(BindableService.class.getName());

    // Track active gRPC deployments - only one allowed at a time
    // TODO (jrp) this should not be static and there is likely a better way to do this.
    private static final AtomicReference<String> GRPC_DEPLOYMENT = new AtomicReference<>(null);

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        // Only process WAR deployments
        if (!DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
            return;
        }

        final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        if (module == null) {
            return;
        }

        final CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        // TODO (jrp) is this ever going to be true and if so, should we throw an exception instead of silently ignoring?
        if (index == null) {
            return;
        }

        // Find all gRPC service classes
        final List<ClassInfo> serviceClasses = findGrpcServices(index);
        // There are no gRPC services, we can safely skip the rest of the processing
        if (serviceClasses.isEmpty()) {
            return;
        }
        final DeploymentUnit parentDeploymentUnit = deploymentUnit.getParent();
        // TODO (jrp) this looks important, but we need to understand what it does. It's the request controller for graceful shutdown
        // TODO (jrp) this was taken from UndertowDeploymentProcessor
        final RequirementServiceTarget serviceTarget = phaseContext.getRequirementServiceTarget();
        //install the control point for the top level deployment no matter what
        if (RequestControllerActivationMarker.isRequestControllerEnabled(deploymentUnit) && parentDeploymentUnit == null) {
            ControlPointService.install(serviceTarget, deploymentUnit.getName(), GrpcConfigurationConstants.SUBSYSTEM_NAME);
        }

        if (GrpcLogger.LOGGER.isDebugEnabled()) {
        GrpcLogger.LOGGER.debugf("Found %d gRPC service(s) in deployment %s", serviceClasses.size(), deploymentUnit.getName());
        }
        final WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if (warMetaData == null) {
            return;
        }
        final JBossWebMetaData metaData = warMetaData.getMergedJBossWebMetaData();

        // Get deployment context path
        // TODO (jrp) We may not end up needing this. AIUI gRPC doesn't have a the concept of a context path so we might
        // TODO (jrp) alwasy need the root context.
        final String contextPath = pathNameOfDeployment(deploymentUnit, metaData);

        GrpcLogger.LOGGER.debugf("Deployment %s has context path: %s", deploymentUnit.getName(), contextPath);

        // Validate single deployment constraint and register it
        if (!GRPC_DEPLOYMENT.compareAndSet(null, deploymentUnit.getName())) {
            final String existingDeployment = GRPC_DEPLOYMENT.get();
            // TODO (jrp) if we really only end up allowing
            throw new DeploymentUnitProcessingException(
                    String.format(
                            "Only one gRPC deployment is allowed per server. " +
                                    "Existing gRPC deployment: '%1$s', attempted deployment: '%2$s'. " +
                                    "Undeploy '%1$s' before deploying '%2$s'.",
                            existingDeployment,
                            deploymentUnit.getName()
                    )
            );
        }

        GrpcLogger.LOGGER.infof("Registered gRPC deployment: %s (context path: %s)",
                deploymentUnit.getName(), contextPath);

        // Inject WildFly's default executor for async gRPC operations
        final Supplier<Executor> executorSupplier;
        try {
            // TODO (jrp) this is not correct, but I'm just testing
            final Executor executor = Executors.newCachedThreadPool();
            executorSupplier = () -> executor;
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(
                    "Failed to get default executor capability for gRPC", e);
        }

        // Create gRPC server with context path and executor
        final GrpcServerService grpcServerService = new GrpcServerService(contextPath, executorSupplier);

        // Instantiate and register services
        for (ClassInfo serviceClass : serviceClasses) {
            try {
                final BindableService service = instantiateService(serviceClass, module);
                grpcServerService.addService(service);

                GrpcLogger.LOGGER.debugf("Registered gRPC service %s in deployment %s",
                        serviceClass.name(), deploymentUnit.getName());

            } catch (Exception e) {
                throw new DeploymentUnitProcessingException(
                        "Failed to instantiate gRPC service: " + serviceClass.name(), e);
            }
        }

        // Start the server
        try {
            grpcServerService.start();
            GrpcLogger.LOGGER.debugf("Started gRPC server for deployment %s",
                    deploymentUnit.getName());
        } catch (final Exception e) {
            throw new DeploymentUnitProcessingException(
                    "Failed to start gRPC server for: " + deploymentUnit.getName(), e);
        }

        // Store in attachment for cleanup
        deploymentUnit.putAttachment(GrpcAttachments.GRPC_SERVER_SERVICE, grpcServerService);

        // Attach the handler with proper delegation
        if (grpcServerService.getHttpHandler() != null) {
            deploymentUnit.addToAttachmentList(
                    //UndertowAttachments.UNDERTOW_INNER_HANDLER_CHAIN_WRAPPERS,
                    UndertowAttachments.UNDERTOW_INITIAL_HANDLER_CHAIN_WRAPPERS,
                    (final HttpHandler next) -> new DelegatingGrpcHandler(grpcServerService.getHttpHandler(), next)
            );

            GrpcLogger.LOGGER.debugf("Attached gRPC handler for deployment %s",
                    deploymentUnit.getName());
        }
    }

    @Override
    public void undeploy(final DeploymentUnit deploymentUnit) {
        final GrpcServerService grpcServerService = deploymentUnit.getAttachment(GrpcAttachments.GRPC_SERVER_SERVICE);

        if (grpcServerService != null) {
            try {
                grpcServerService.stop();
            } catch (final Exception e) {
                GrpcLogger.LOGGER.failedToStopServer(e, deploymentUnit.getName());
            } finally {
                deploymentUnit.removeAttachment(GrpcAttachments.GRPC_SERVER_SERVICE);

                // Unregister from active deployments
                GRPC_DEPLOYMENT.set(null);
                GrpcLogger.LOGGER.debugf("Unregistered gRPC deployment: %s", deploymentUnit.getName());
            }
        }
    }

    private List<ClassInfo> findGrpcServices(final CompositeIndex index) {
        final List<ClassInfo> services = new ArrayList<>();
        for (final ClassInfo classInfo : index.getAllKnownImplementors(BINDABLE_SERVICE)) {
            if (!classInfo.isInterface() && !classInfo.isAbstract()) {
                services.add(classInfo);
            }
        }
        return services;
    }

    private BindableService instantiateService(final ClassInfo classInfo, final Module module)
            throws Exception {
        final String className = classInfo.name().toString();
        final Class<?> serviceClass = module.getClassLoader().loadClass(className);

        if (!BindableService.class.isAssignableFrom(serviceClass)) {
            throw GrpcLogger.LOGGER.notBindableService(className);
        }

        return (BindableService) serviceClass.getDeclaredConstructor().newInstance();
    }

    private static String pathNameOfDeployment(final DeploymentUnit deploymentUnit, final JBossWebMetaData metaData) {
        String pathName;
        // TODO (jrp) this will not work as this is configurable, but for now we'll leave it until we figure something out
        if ("ROOT.war".equalsIgnoreCase(deploymentUnit.getName())) {
            pathName = "/";
        } else if (metaData.getContextRoot() == null) {
            final EEModuleDescription description = deploymentUnit.getAttachment(org.jboss.as.ee.component.Attachments.EE_MODULE_DESCRIPTION);
            if (description != null) {
                // if there is an EEModuleDescription we need to take into account that the module name may have been overridden
                pathName = "/" + description.getModuleName();
            } else {
                pathName = "/" + deploymentUnit.getName().substring(0, deploymentUnit.getName().length() - 4);
            }
        } else {
            pathName = metaData.getContextRoot();
            if (!pathName.isEmpty() && pathName.charAt(0) != '/') {
                pathName = "/" + pathName;
            }
        }
        return pathName;
    }

    /**
     * Wrapper that delegates to gRPC handler for gRPC requests,
     * and passes through to next handler for non-gRPC requests.
     */
    private static class DelegatingGrpcHandler implements HttpHandler {
        private final GrpcHttpHandler grpcHandler;
        private final HttpHandler nextHandler;

        DelegatingGrpcHandler(final GrpcHttpHandler grpcHandler,
                              final HttpHandler nextHandler) {
            this.grpcHandler = grpcHandler;
            this.nextHandler = nextHandler;
        }

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            final boolean isGrpc = isGrpcRequest(exchange);
            GrpcLogger.LOGGER.debugf("DelegatingGrpcHandler - path='%s', isGRPC=%s, protocol=%s, contentType=%s",
                    exchange.getRequestPath(), isGrpc, exchange.getProtocol(),
                    exchange.getRequestHeaders().getFirst(io.undertow.util.Headers.CONTENT_TYPE));

            // Check if this is a gRPC request
            if (isGrpc) {
                GrpcLogger.LOGGER.debugf("Delegating to gRPC handler");
                grpcHandler.handleRequest(exchange);
            } else {
                GrpcLogger.LOGGER.debugf("Delegating to next handler");
                nextHandler.handleRequest(exchange);
            }
        }

        private boolean isGrpcRequest(final HttpServerExchange exchange) {
            // Same logic as GrpcHttpHandler.isGrpcRequest()
            if (!io.undertow.util.Protocols.HTTP_2_0.equals(exchange.getProtocol())) {
                return false;
            }

            final String contentType = exchange.getRequestHeaders().getFirst(io.undertow.util.Headers.CONTENT_TYPE);
            if (contentType == null) {
                return false;
            }

            final String lowerContentType = contentType.toLowerCase(java.util.Locale.ROOT);
            // TODO (jrp) there is probably a better way to do this
            return lowerContentType.startsWith("application/grpc");
        }
    }
}
