/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import static io.grpc.internal.GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE;
import static org.wildfly.extension.grpc._private.GrpcLogger.LOGGER;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.Attributes;
import io.grpc.BindableService;
import io.grpc.InternalChannelz.SocketStats;
import io.grpc.InternalInstrumented;
import io.grpc.InternalLogId;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.InternalServer;
import io.grpc.internal.ServerImplBuilder;
import io.grpc.internal.ServerListener;
import io.grpc.internal.ServerStream;
import io.grpc.internal.ServerTransport;
import io.grpc.internal.ServerTransportListener;
import io.grpc.internal.SharedResourceHolder;
import io.undertow.server.HttpServerExchange;

/**
 * Service for the gRPC server.
 * <p>
 * This service creates and manages a gRPC server that integrates with Undertow's HTTP/2 transport. The server doesn't
 * bind to its own port - instead it uses Undertow's existing HTTP/2 listeners via the {@link GrpcHttpHandler}.
 * </p>
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
// TODO (jrp) consider making this a real service or renaming it as it's not an MSC service
public class GrpcServerService {

    private final List<BindableService> services = new ArrayList<>();
    private final String deploymentContextPath;
    private final Executor grpcExecutor;
    // TODO (jrp) we should make this configurable
    private final int maxInboundMessageSize = DEFAULT_MAX_MESSAGE_SIZE;

    private ServerTransportListener transportListener;
    private GrpcHttpHandler httpHandler;
    private ScheduledExecutorService scheduler;
    private Function<HttpServerExchange, String> methodNameResolver;
    private List<? extends ServerStreamTracer.Factory> streamTracerFactories;
    private InternalServerImpl internalServer;

    /**
     * Creates a new gRPC server service for a deployment.
     *
     * @param deploymentContextPath the deployment context path (e.g., "/grpc-helloworld")
     * @param executorSupplier      supplier for WildFly's managed executor for async operations
     */
    public GrpcServerService(final String deploymentContextPath, final Supplier<Executor> executorSupplier) {
        this.deploymentContextPath = deploymentContextPath != null ? deploymentContextPath : "";
        this.grpcExecutor = executorSupplier != null ? executorSupplier.get() : null;
    }

    /**
     * Adds a gRPC service to be registered with the server.
     *
     * @param service the gRPC service
     */
    public void addService(final BindableService service) {
        services.add(service);
    }

    /**
     * Gets the HTTP handler for routing gRPC requests.
     *
     * @return the gRPC HTTP handler, or null if not started
     */
    public GrpcHttpHandler getHttpHandler() {
        return httpHandler;
    }

    public void start() {
        LOGGER.startingGrpcServer();

        try {
            // Create method name resolver that strips deployment context path
            methodNameResolver = createMethodNameResolver();

            // Build the gRPC server infrastructure
            final ServerImplBuilder serverImplBuilder = new ServerImplBuilder(this::buildTransportServers);

            // Add executor if available
            if (grpcExecutor != null) {
                serverImplBuilder.executor(grpcExecutor);
            }

            // Add all registered services
            for (final BindableService service : services) {
                serverImplBuilder.addService(service);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("Registered gRPC service: %s", service.bindService()
                            .getServiceDescriptor()
                            .getName());
                }
            }

            // Build and start the server to initialize the transport listener
            final Server server = serverImplBuilder.build().start();

            // Get the scheduler for the transport
            scheduler = SharedResourceHolder.get(GrpcUtil.TIMER_SERVICE);

            // Create the server transport and get the transport listener from the internal server
            final ServerTransportImpl serverTransport = new ServerTransportImpl(scheduler);
            final ServerTransportListener delegate = internalServer.serverListener.transportCreated(serverTransport);

            // Wrap the delegate to handle shutdown
            transportListener = new ServerTransportListener() {
                @Override
                public void streamCreated(final ServerStream stream, final String method, final Metadata headers) {
                    delegate.streamCreated(stream, method, headers);
                }

                @Override
                public Attributes transportReady(final Attributes attributes) {
                    return delegate.transportReady(attributes);
                }

                @Override
                public void transportTerminated() {
                    server.shutdown();
                    delegate.transportTerminated();
                    SharedResourceHolder.release(GrpcUtil.TIMER_SERVICE, scheduler);
                }
            };

            // Create the HTTP handler with all configuration
            httpHandler = new GrpcHttpHandler(
                    transportListener,
                    streamTracerFactories,
                    methodNameResolver,
                    maxInboundMessageSize,
                    grpcExecutor
            );

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("gRPC server started with %d services (context path: %s)",
                        services.size(), deploymentContextPath.isEmpty() ? "/" : deploymentContextPath);
            }

        } catch (final Exception e) {
            throw LOGGER.failedToStartServer(e);
        }
    }

    public void stop() {
        LOGGER.stoppingGrpcServer();

        try {
            // Notify the transport that it's terminating (this will also shutdown the server and release scheduler)
            if (transportListener != null) {
                transportListener.transportTerminated();
            }

            httpHandler = null;
            transportListener = null;
            methodNameResolver = null;
            streamTracerFactories = null;
            scheduler = null;
            internalServer = null;

            // CRITICAL: Clear services list to release BindableService instances
            // This prevents classloader leaks by releasing references to deployment classes
            services.clear();

        } catch (final Exception e) {
            LOGGER.failedToStopServer(e);
        }
    }

    /**
     * Creates the method name resolver based on deployment context path.
     */
    private Function<HttpServerExchange, String> createMethodNameResolver() {
        if (deploymentContextPath.isEmpty()) {
            // Default resolver: just strip leading slash
            return (final HttpServerExchange exchange) -> {
                final String path = exchange.getRequestPath();
                return path.startsWith("/") ? path.substring(1) : path;
            };
        } else {
            // Custom resolver: strip deployment context path
            return (final HttpServerExchange exchange) -> {
                final String requestPath = exchange.getRequestPath();
                final String relativePath = exchange.getRelativePath();

                LOGGER.debugf("Method resolver - requestPath='%s', relativePath='%s', contextPath='%s'",
                        requestPath, relativePath, deploymentContextPath);

                String path = requestPath;

                // Strip leading slash
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }

                // Count remaining slashes
                final int slashCount = path.length() - path.replace("/", "").length();

                // If exactly 2 slashes, strip the first segment (context path)
                // Example: "/grpc-helloworld/helloworld.Greeter/SayHello" has 3 slashes total
                //          After stripping leading slash: "grpc-helloworld/helloworld.Greeter/SayHello" has 2 slashes
                if (slashCount == 2) {
                    final int index = path.indexOf('/');
                    if (index > 0) {
                        path = path.substring(index + 1);
                    }
                }

                LOGGER.debugf("Extracted method name='%s'", path);
                return path;
            };
        }
    }

    /**
     * Callback invoked by ServerImplBuilder to build the internal transport servers.
     */
    private InternalServer buildTransportServers(final List<? extends ServerStreamTracer.Factory> factories) {
        this.streamTracerFactories = factories;
        this.internalServer = new InternalServerImpl();
        return internalServer;
    }

    /**
     * Internal server implementation that doesn't manage actual sockets.
     * Socket management is delegated to Undertow.
     */
    private static final class InternalServerImpl implements InternalServer {

        ServerListener serverListener;

        @Override
        public void start(final ServerListener listener) {
            this.serverListener = listener;
        }

        @Override
        public void shutdown() {
            if (serverListener != null) {
                serverListener.serverShutdown();
            }
        }

        @Override
        public SocketAddress getListenSocketAddress() {
            return new SocketAddress() {
                @Override
                public String toString() {
                    return "UndertowServer";
                }
            };
        }

        @Override
        public InternalInstrumented<SocketStats> getListenSocketStats() {
            return null;
        }

        @Override
        public List<? extends SocketAddress> getListenSocketAddresses() {
            return Collections.emptyList();
        }

        @Override
        public List<InternalInstrumented<SocketStats>> getListenSocketStatsList() {
            return null;
        }
    }

    /**
     * Server transport implementation that doesn't manage actual transport.
     * Transport is managed by Undertow.
     */
    private static final class ServerTransportImpl implements ServerTransport {

        private final InternalLogId logId = InternalLogId.allocate(ServerTransportImpl.class, null);
        private final ScheduledExecutorService scheduler;

        ServerTransportImpl(final ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void shutdownNow(final Status reason) {
        }

        @Override
        public ScheduledExecutorService getScheduledExecutorService() {
            return scheduler;
        }

        @Override
        public ListenableFuture<SocketStats> getStats() {
            return null;
        }

        @Override
        public InternalLogId getLogId() {
            return logId;
        }
    }
}
