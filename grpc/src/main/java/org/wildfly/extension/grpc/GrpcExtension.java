/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.kohsuke.MetaInfServices;

/**
 * Extension for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@MetaInfServices(Extension.class)
public class GrpcExtension implements Extension {

    @Override
    public void initializeParsers(final ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(GrpcConfigurationConstants.SUBSYSTEM_NAME,
                GrpcSubsystemSchema.CURRENT.getUri(),
                GrpcSubsystemParser_1_0::new);
    }

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(
                GrpcConfigurationConstants.SUBSYSTEM_NAME,
                GrpcSubsystemModel.CURRENT.getVersion());

        final GrpcSubsystemRegistrar registrar = new GrpcSubsystemRegistrar();
        final ManagementResourceRegistration registration = registrar.register(subsystem, new org.wildfly.subsystem.resource.ManagementResourceRegistrationContext() {
            @Override
            public boolean isRuntimeOnlyRegistrationValid() {
                return context.isRuntimeOnlyRegistrationValid();
            }

            @Override
            public java.util.Optional<org.jboss.as.controller.services.path.PathManager> getPathManager() {
                return java.util.Optional.ofNullable(context.getPathManager());
            }
        });

        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE, false);
        subsystem.registerXMLElementWriter(new GrpcSubsystemWriter());
    }
}
