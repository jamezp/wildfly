/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.staxmapper.IntVersion;

/**
 * Enumerates the supported namespaces for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public enum GrpcSubsystemSchema implements PersistentSubsystemSchema<GrpcSubsystemSchema> {

    VERSION_1_0(1, 0),
    ;
    static final GrpcSubsystemSchema CURRENT = VERSION_1_0;

    private final VersionedNamespace<IntVersion, GrpcSubsystemSchema> namespace;

    GrpcSubsystemSchema(final int major, final int minor) {
        this.namespace = SubsystemSchema.createSubsystemURN(GrpcConfigurationConstants.SUBSYSTEM_NAME, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, GrpcSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    @SuppressWarnings("removal")
    public PersistentResourceXMLDescription getXMLDescription() {
        return PersistentResourceXMLDescription.factory(this)
                .builder(GrpcConfigurationConstants.SUBSYSTEM_PATH)
                .addAttributes(GrpcSubsystemRegistrar.ATTRIBUTES.stream())
                .build();
    }
}
