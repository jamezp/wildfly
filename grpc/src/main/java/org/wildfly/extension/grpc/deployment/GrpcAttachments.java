/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc.deployment;

import org.jboss.as.server.deployment.AttachmentKey;
import org.wildfly.extension.grpc.GrpcServerService;

/**
 * Attachment keys for deployment gRPC state.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public final class GrpcAttachments {

    /**
     * The deployment gRPC server service instance.
     * Each deployment with gRPC services gets its own GrpcServerService.
     */
    // TODO (jrp) I don't know what we'll need this
    public static final AttachmentKey<GrpcServerService> GRPC_SERVER_SERVICE =
            AttachmentKey.create(GrpcServerService.class);

    private GrpcAttachments() {
    }
}
