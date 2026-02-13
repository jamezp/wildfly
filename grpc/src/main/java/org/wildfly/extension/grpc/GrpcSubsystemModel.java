/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * Enumeration of supported management model versions for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public enum GrpcSubsystemModel implements SubsystemModel {
    VERSION_1_0_0(1, 0, 0);

    static final GrpcSubsystemModel CURRENT = VERSION_1_0_0;

    private final ModelVersion version;

    GrpcSubsystemModel(final int major, final int minor, final int micro) {
        this.version = ModelVersion.create(major, minor, micro);
    }

    @Override
    public ModelVersion getVersion() {
        return version;
    }
}
