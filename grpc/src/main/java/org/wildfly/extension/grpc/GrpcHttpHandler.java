/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Function;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.InternalLogId;
import io.grpc.Metadata;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.ServerTransportListener;
import io.grpc.internal.StatsTraceContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import org.wildfly.extension.grpc._private.GrpcLogger;

/**
 * Undertow HttpHandler that detects and processes gRPC requests.
 *
 * <p>This handler checks if an incoming request is a gRPC request by verifying:
 * <ul>
 *   <li>The protocol is HTTP/2</li>
 *   <li>The Content-Type header is "application/grpc" or starts with "application/grpc+"</li>
 * </ul>
 *
 * <p>If the request is a gRPC request, it creates an {@link UndertowServerStream} and
 * routes it to the gRPC server's {@link ServerTransportListener}.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class GrpcHttpHandler implements HttpHandler {

    private static final String GRPC_CONTENT_TYPE = GrpcUtil.CONTENT_TYPE_GRPC;

    private final ServerTransportListener transportListener;
    private final Function<HttpServerExchange, String> methodNameResolver;
    private final int maxInboundMessageSize;
    private final List<? extends ServerStreamTracer.Factory> streamTracerFactories;
    private final Executor executor;
    private final InternalLogId logId;

    /**
     * Creates a new gRPC HTTP handler.
     *
     * @param transportListener     the gRPC transport listener
     * @param streamTracerFactories the stream tracer factories
     * @param methodNameResolver    function to extract gRPC method name from exchange
     * @param maxInboundMessageSize maximum inbound message size in bytes
     * @param executor              executor for async gRPC operations (may be null for direct execution)
     */
    public GrpcHttpHandler(final ServerTransportListener transportListener,
                           final List<? extends ServerStreamTracer.Factory> streamTracerFactories,
                           final Function<HttpServerExchange, String> methodNameResolver,
                           final int maxInboundMessageSize, final Executor executor) {
        this.transportListener = transportListener;
        this.streamTracerFactories = streamTracerFactories;
        this.methodNameResolver = methodNameResolver;
        this.maxInboundMessageSize = maxInboundMessageSize;
        this.executor = executor;
        this.logId = InternalLogId.allocate(GrpcHttpHandler.class, null);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        // Check if this is a gRPC request
        if (!isGrpcRequest(exchange)) {
            // Not a gRPC request, return without processing
            // In a real handler chain, this would call next.handleRequest(exchange)
            exchange.setStatusCode(400);
            // TODO (jrp) is this the content-type we want to send? Is it defined in a spec?
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.endExchange();
            return;
        }

        // Extract method name
        final String method = methodNameResolver.apply(exchange);

        if (method == null || method.isEmpty()) {
            // TODO (jrp) should this be a debug message?
            GrpcLogger.LOGGER.errorf("Failed to extract gRPC method from request path: %s", exchange.getRequestPath());
            sendErrorResponse(exchange, Status.UNIMPLEMENTED.withDescription(GrpcLogger.LOGGER.methodNotFound()));
            return;
        }

        // CRITICAL: Dispatch to I/O thread to mark exchange as async
        // This prevents Undertow from auto-closing the exchange when this handler returns
        // The gRPC response will be generated asynchronously on the executor thread
        exchange.dispatch(exchange.getIoThread(), () -> {
            try {
                // Extract metadata from headers
                final Metadata headers = extractMetadata(exchange);

                // Create stats trace context
                final StatsTraceContext statsTraceCtx = StatsTraceContext.newServerContext(streamTracerFactories, method, headers);

                // Create attributes
                final Attributes attributes = Attributes.newBuilder()
                        .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, exchange.getSourceAddress())
                        .set(Grpc.TRANSPORT_ATTR_LOCAL_ADDR, exchange.getDestinationAddress())
                        .build();

                // Get authority (host header)
                String authority = exchange.getRequestHeaders().getFirst(Headers.HOST);
                if (authority == null) {
                    authority = exchange.getHostName();
                }

                // Create the server stream
                final UndertowServerStream stream = new UndertowServerStream(exchange, statsTraceCtx, maxInboundMessageSize, attributes, authority, logId, executor);

                // Notify the transport listener about the new stream
                transportListener.streamCreated(stream, method, headers);

                // Allocate the stream on the transport thread
                stream.transportState().runOnTransportThread(stream.transportState()::onStreamAllocated);

                // Start reading the request
                stream.startReading();

            } catch (final Exception e) {
                // TODO (jrp) should this be a debug message?
                GrpcLogger.LOGGER.errorf(e, "Failed to process gRPC request for method: %s", method);
                sendErrorResponse(exchange, Status.INTERNAL.withCause(e)
                        .withDescription(GrpcLogger.LOGGER.failedToProcessRequest()));
            }
        });
    }

    /**
     * Checks if the request is a gRPC request.
     *
     * @param exchange the HTTP server exchange
     *
     * @return true if this is a gRPC request
     */
    private boolean isGrpcRequest(final HttpServerExchange exchange) {
        // Check HTTP/2
        if (!Protocols.HTTP_2_0.equals(exchange.getProtocol())) {
            return false;
        }

        // Check Content-Type header
        final String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType == null) {
            return false;
        }

        // Content-Type should be "application/grpc" or "application/grpc+proto", etc.
        final String lowerContentType = contentType.toLowerCase(Locale.ROOT);
        return lowerContentType.startsWith(GRPC_CONTENT_TYPE);
    }

    /**
     * Extracts gRPC metadata from HTTP headers.
     *
     * @param exchange the HTTP server exchange
     *
     * @return the metadata
     */
    private Metadata extractMetadata(final HttpServerExchange exchange) {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();

        // Count headers (excluding pseudo-headers and reserved gRPC headers)
        int count = 0;
        for (final HttpString headerName : requestHeaders.getHeaderNames()) {
            final String name = headerName.toString().toLowerCase(Locale.ROOT);
            // Skip pseudo-headers (start with :) and some reserved headers
            if (!name.startsWith(":") && !name.equals("content-type") && !name.equals("te") && !name.equals("user-agent")) {
                count++;
            }
        }

        // Build metadata
        byte[][] serializedHeaders = new byte[count * 2][];
        int index = 0;

        for (final HttpString headerName : requestHeaders.getHeaderNames()) {
            final String name = headerName.toString().toLowerCase(Locale.ROOT);

            // Skip pseudo-headers and reserved headers
            if (name.startsWith(":") || name.equals("content-type") || name.equals("te") || name.equals("user-agent")) {
                continue;
            }

            // Get first header value (gRPC doesn't support multiple values per header in metadata)
            final String value = requestHeaders.getFirst(headerName);
            if (value != null) {
                serializedHeaders[index++] = name.getBytes(StandardCharsets.US_ASCII);
                serializedHeaders[index++] = value.getBytes(StandardCharsets.US_ASCII);
            }
        }

        // Trim array if needed
        if (index < serializedHeaders.length) {
            final byte[][] trimmed = new byte[index][];
            System.arraycopy(serializedHeaders, 0, trimmed, 0, index);
            serializedHeaders = trimmed;
        }

        return io.grpc.InternalMetadata.newMetadata(serializedHeaders);
    }

    /**
     * Sends an error response for a failed gRPC request.
     *
     * @param exchange the HTTP server exchange
     * @param status   the gRPC status
     */
    private void sendErrorResponse(final HttpServerExchange exchange, final Status status) {
        exchange.setStatusCode(200); // gRPC always uses HTTP 200
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, GRPC_CONTENT_TYPE);
        exchange.getResponseHeaders().put(new HttpString("grpc-status"), String.valueOf(status.getCode().value()));

        if (status.getDescription() != null) {
            exchange.getResponseHeaders().put(new HttpString("grpc-message"), status.getDescription());
        }

        exchange.endExchange();
    }
}
