/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import java.util.concurrent.Executor;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Capabilities for the gRPC extension.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class Capabilities {

    /**
     * Service descriptor for the default executor used by gRPC deployments.
     */
    public static final NullaryServiceDescriptor<Executor> DEFAULT_EXECUTOR_SERVICE_DESCRIPTOR =
            NullaryServiceDescriptor.of("org.wildfly.grpc.default-executor", Executor.class);

    /**
     * Service descriptor for named thread pools used by gRPC for async operations.
     */
    public static final UnaryServiceDescriptor<Executor> THREAD_POOL_SERVICE_DESCRIPTOR =
            UnaryServiceDescriptor.of("org.wildfly.grpc.thread.pool", DEFAULT_EXECUTOR_SERVICE_DESCRIPTOR);

    /**
     * A capability for the default executor used by gRPC deployments.
     */
    public static final RuntimeCapability<Void> DEFAULT_EXECUTOR_CAPABILITY =
            RuntimeCapability.Builder.of(DEFAULT_EXECUTOR_SERVICE_DESCRIPTOR).build();

    /**
     * A capability for thread-pools used by gRPC for async operations.
     */
    public static final RuntimeCapability<Void> THREAD_POOL_CAPABILITY =
            RuntimeCapability.Builder.of(THREAD_POOL_SERVICE_DESCRIPTOR).build();

    private Capabilities() {
    }
}
