/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import org.jboss.as.controller.Extension;
import org.kohsuke.MetaInfServices;
import org.wildfly.subsystem.SubsystemConfiguration;
import org.wildfly.subsystem.SubsystemExtension;
import org.wildfly.subsystem.SubsystemPersistence;

/**
 * Extension for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@MetaInfServices(Extension.class)
public class GrpcExtension extends SubsystemExtension<GrpcSubsystemSchema> {
    public GrpcExtension() {
        super(SubsystemConfiguration.of(GrpcConfigurationConstants.SUBSYSTEM_NAME,
                        GrpcSubsystemModel.CURRENT,
                        GrpcSubsystemRegistrar::new),
                SubsystemPersistence.of(GrpcSubsystemSchema.CURRENT));
    }
}
