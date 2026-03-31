/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

/**
 * Enumerates the supported namespaces for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
enum GrpcSubsystemSchema {

    VERSION_1_0("urn:wildfly:grpc:1.0"),
    ;
    static final GrpcSubsystemSchema CURRENT = VERSION_1_0;

    private final String uri;

    GrpcSubsystemSchema(final String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return this.uri;
    }
}
