/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import static org.wildfly.extension.grpc._private.GrpcLogger.LOGGER;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.grpc.deployment.GrpcDependencyProcessor;
import org.wildfly.extension.grpc.deployment.GrpcDeploymentProcessor;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceAttributeDefinition;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registrar for the gRPC subsystem resource definition.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
class GrpcSubsystemRegistrar implements SubsystemResourceDefinitionRegistrar, ResourceServiceConfigurator,
        Consumer<DeploymentProcessorTarget> {

    static final SubsystemResourceRegistration REGISTRATION = SubsystemResourceRegistration.of(GrpcConfigurationConstants.SUBSYSTEM_NAME);
    static final ParentResourceDescriptionResolver RESOLVER = new SubsystemResourceDescriptionResolver(REGISTRATION.getName(), GrpcExtension.class);

    static final CapabilityReferenceAttributeDefinition<Executor> DEFAULT_THREAD_POOL =
            new CapabilityReferenceAttributeDefinition.Builder<>("default-thread-pool",
                    CapabilityReference.builder(Capabilities.DEFAULT_EXECUTOR_CAPABILITY, Capabilities.THREAD_POOL_SERVICE_DESCRIPTOR).build())
                    .setRequired(true)
                    .setRestartAllServices()
                    .build();

    @Override
    public ManagementResourceRegistration register(final SubsystemRegistration parent,
                                                   final ManagementResourceRegistrationContext context) {
        final ResourceDescriptor descriptor = ResourceDescriptor.builder(RESOLVER)
                .addCapability(Capabilities.DEFAULT_EXECUTOR_CAPABILITY)
                .addAttributes(List.of(DEFAULT_THREAD_POOL))
                .withRuntimeHandler(org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler.configureService(this))
                .withDeploymentChainContributor(this)
                .build();

        final ManagementResourceRegistration registration = parent.registerSubsystemModel(ResourceDefinition.builder(REGISTRATION, RESOLVER).build());
        ManagementResourceRegistrar.of(descriptor).register(registration);

        // Register thread pool child resource
        registration.registerSubModel(new GrpcThreadPoolResourceDefinition(context.isRuntimeOnlyRegistrationValid()));

        return registration;
    }

    @Override
    public ResourceServiceInstaller configure(final OperationContext context, final ModelNode model) throws OperationFailedException {
        LOGGER.activatingSubsystem();
        // Install the default executor capability that delegates to the configured thread pool
        return CapabilityServiceInstaller.BlockingBuilder.of(Capabilities.DEFAULT_EXECUTOR_CAPABILITY, DEFAULT_THREAD_POOL.resolve(context, model)).build();
    }

    @Override
    public void accept(final DeploymentProcessorTarget target) {
        // Register deployment processors
        target.addDeploymentProcessor(
                GrpcConfigurationConstants.SUBSYSTEM_NAME,
                Phase.POST_MODULE,
                // TODO (jrp) this needs to be a constant in wildfly-core, but for now we will simply hard-code it
                0x4000, // Run after module setup but before most other processors
                new GrpcDeploymentProcessor()
        );
        target.addDeploymentProcessor(
                GrpcConfigurationConstants.SUBSYSTEM_NAME,
                Phase.DEPENDENCIES,
                0x1F1D,
                new GrpcDependencyProcessor()
        );
    }
}
