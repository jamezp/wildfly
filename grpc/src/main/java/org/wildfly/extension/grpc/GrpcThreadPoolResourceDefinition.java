/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.threads.ManagedQueueExecutorService;
import org.jboss.as.threads.PoolAttributeDefinitions;
import org.jboss.as.threads.ThreadFactoryResolver;
import org.jboss.as.threads.ThreadsServices;
import org.jboss.as.threads.UnboundedQueueThreadPoolAdd;
import org.jboss.as.threads.UnboundedQueueThreadPoolMetricsHandler;
import org.jboss.as.threads.UnboundedQueueThreadPoolRemove;
import org.jboss.as.threads.UnboundedQueueThreadPoolWriteAttributeHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Resource definition for gRPC thread pools.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class GrpcThreadPoolResourceDefinition extends SimpleResourceDefinition {

    public static final String NAME = "thread-pool";
    static final PathElement PATH = PathElement.pathElement(NAME);
    private static final ServiceName BASE_SERVICE_NAME = ServiceName.JBOSS.append("grpc", "thread-pool");

    private final boolean registerRuntimeOnly;

    public GrpcThreadPoolResourceDefinition(final boolean registerRuntimeOnly) {
        super(new SimpleResourceDefinition.Parameters(PATH, GrpcSubsystemRegistrar.RESOLVER.createChildResolver(PATH))
                .setAddHandler(GrpcThreadPoolAdd.INSTANCE)
                .setRemoveHandler(GrpcThreadPoolRemove.INSTANCE)
                .addCapabilities(Capabilities.THREAD_POOL_CAPABILITY));
        this.registerRuntimeOnly = registerRuntimeOnly;
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        new UnboundedQueueThreadPoolWriteAttributeHandler(Capabilities.THREAD_POOL_CAPABILITY, BASE_SERVICE_NAME).registerAttributes(resourceRegistration);
        if (registerRuntimeOnly) {
            new UnboundedQueueThreadPoolMetricsHandler(BASE_SERVICE_NAME).registerAttributes(resourceRegistration);
        }
    }

    static class GrpcThreadPoolAdd extends UnboundedQueueThreadPoolAdd {
        static final GrpcThreadPoolAdd INSTANCE = new GrpcThreadPoolAdd(GrpcThreadFactoryResolver.INSTANCE, BASE_SERVICE_NAME);

        private final ServiceName serviceNameBase;

        public GrpcThreadPoolAdd(final ThreadFactoryResolver threadFactoryResolver, final ServiceName serviceNameBase) {
            super(threadFactoryResolver, serviceNameBase);
            this.serviceNameBase = serviceNameBase;
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
            final String name = context.getCurrentAddressValue();
            final ServiceTarget target = context.getCapabilityServiceTarget();
            final ServiceName serviceName = context.getCapabilityServiceName(Capabilities.THREAD_POOL_CAPABILITY.getName(), name, Executor.class);
            final ServiceBuilder<?> serviceBuilder = target.addService(serviceName);
            final Consumer<Executor> executorConsumer = serviceBuilder.provides(serviceName);
            final Supplier<ManagedQueueExecutorService> threadPoolSupplier = serviceBuilder.requires(serviceNameBase.append(name));
            final GrpcExecutorService service = new GrpcExecutorService(executorConsumer, threadPoolSupplier);
            serviceBuilder.setInstance(service);
            serviceBuilder.install();
        }
    }

    static class GrpcThreadPoolRemove extends UnboundedQueueThreadPoolRemove {
        static final GrpcThreadPoolRemove INSTANCE = new GrpcThreadPoolRemove();

        public GrpcThreadPoolRemove() {
            super(GrpcThreadPoolAdd.INSTANCE);
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            // First remove the Executor service, then delegate
            context.removeService(context.getCapabilityServiceName(Capabilities.THREAD_POOL_CAPABILITY.getName(), context.getCurrentAddressValue(), null));
            super.performRuntime(context, operation, model);
        }
    }

    private static class GrpcThreadFactoryResolver extends ThreadFactoryResolver.SimpleResolver {
        static final GrpcThreadFactoryResolver INSTANCE = new GrpcThreadFactoryResolver();

        private GrpcThreadFactoryResolver() {
            super(ThreadsServices.FACTORY);
        }

        @Override
        protected String getThreadGroupName(String threadPoolName) {
            return "gRPC";
        }
    }
}
