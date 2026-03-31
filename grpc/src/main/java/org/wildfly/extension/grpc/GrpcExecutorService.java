/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.threads.ManagedQueueExecutorService;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * Service that provides an {@link Executor} for gRPC async operations.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
class GrpcExecutorService implements Service {

    private final Consumer<Executor> executorConsumer;
    private final Supplier<ManagedQueueExecutorService> threadPoolSupplier;

    GrpcExecutorService(
            final Consumer<Executor> executorConsumer,
            final Supplier<ManagedQueueExecutorService> threadPoolSupplier) {
        this.executorConsumer = executorConsumer;
        this.threadPoolSupplier = threadPoolSupplier;
    }

    @Override
    public void start(final StartContext context) {
        executorConsumer.accept(threadPoolSupplier.get());
    }

    @Override
    public void stop(final StopContext context) {
        executorConsumer.accept(null);
    }
}
