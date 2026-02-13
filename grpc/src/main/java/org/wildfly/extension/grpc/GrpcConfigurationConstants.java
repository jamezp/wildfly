/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;

/**
 * Configuration constants for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
// TODO (jrp) do we really want to expose this?
public final class GrpcConfigurationConstants {

    public static final String SUBSYSTEM_NAME = "grpc";
    static final ParentResourceDescriptionResolver SUBSYSTEM_RESOLVER = new SubsystemResourceDescriptionResolver(SUBSYSTEM_NAME, GrpcExtension.class);
    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);
}
