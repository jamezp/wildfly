/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import static org.wildfly.extension.grpc.GrpcConfigurationConstants.SUBSYSTEM_PATH;
import static org.wildfly.extension.grpc.GrpcConfigurationConstants.SUBSYSTEM_RESOLVER;
import static org.wildfly.extension.grpc._private.GrpcLogger.LOGGER;

import java.util.List;
import java.util.function.Consumer;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.grpc.deployment.GrpcDependencyProcessor;
import org.wildfly.extension.grpc.deployment.GrpcDeploymentProcessor;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;

/**
 * Registrar for the gRPC subsystem resource definition.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
class GrpcSubsystemRegistrar implements SubsystemResourceDefinitionRegistrar, ResourceServiceConfigurator,
        Consumer<DeploymentProcessorTarget> {

    private static final AttributeDefinition ENABLED = SimpleAttributeDefinitionBuilder
            .create("enabled", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final List<AttributeDefinition> ATTRIBUTES = List.of(
            ENABLED
    );

    @Override
    public ManagementResourceRegistration register(final SubsystemRegistration parent,
                                                   final ManagementResourceRegistrationContext context) {
        final ManagementResourceRegistration registration =
                parent.registerSubsystemModel(ResourceDefinition.builder(ResourceRegistration.of(SUBSYSTEM_PATH), SUBSYSTEM_RESOLVER)
                        .build());
        final ResourceDescriptor descriptor = ResourceDescriptor.builder(SUBSYSTEM_RESOLVER)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(this))
                .withDeploymentChainContributor(this)
                .addAttributes(ATTRIBUTES)
                .build();

        ManagementResourceRegistrar.of(descriptor).register(registration);

        return registration;
    }

    @Override
    public ResourceServiceInstaller configure(final OperationContext context, final ModelNode model) throws OperationFailedException {
        LOGGER.activatingSubsystem();

        final boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();

        // No global service needed - each deployment creates its own
        // Just track that subsystem is enabled

        return ResourceServiceInstaller.NONE;
    }

    @Override
    public void accept(final DeploymentProcessorTarget target) {
        // Register deployment processors (they check subsystem enabled state internally)
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
