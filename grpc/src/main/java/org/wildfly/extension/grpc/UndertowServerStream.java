/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import io.grpc.Attributes;
import io.grpc.InternalLogId;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.internal.AbstractServerStream;
import io.grpc.internal.SerializingExecutor;
import io.grpc.internal.StatsTraceContext;
import io.grpc.internal.TransportFrameUtil;
import io.grpc.internal.TransportTracer;
import io.grpc.internal.WritableBuffer;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import org.wildfly.extension.grpc._private.GrpcLogger;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

/**
 * Undertow-based implementation of gRPC ServerStream that bridges Undertow's HTTP/2 transport with grpc-java's server
 * abstractions.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class UndertowServerStream extends AbstractServerStream {

    private final UndertowTransportState transportState;
    private final UndertowSink sink;
    private final Attributes attributes;
    private final String authority;
    private final InternalLogId logId;
    private final StreamSourceChannel requestChannel;

    public UndertowServerStream(
            final HttpServerExchange exchange,
            final StatsTraceContext statsTraceCtx,
            final int maxInboundMessageSize,
            final Attributes attributes,
            final String authority,
            final InternalLogId logId,
            final Executor executor) {
        super(
                new UndertowWritableBufferAllocator(exchange.getConnection().getByteBufferPool()),
                statsTraceCtx
        );

        if (!Protocols.HTTP_2_0.equals(exchange.getProtocol())) {
            throw GrpcLogger.LOGGER.notHttp2Request();
        }

        this.attributes = attributes;
        this.authority = authority;
        this.logId = logId;

        // Create transport state with executor for async operations FIRST
        // (needed by ReadChannelListener below)
        this.transportState = new UndertowTransportState(
                maxInboundMessageSize,
                statsTraceCtx,
                new TransportTracer(),
                executor,
                logId,
                exchange
        );

        // Set up request channel with ReadChannelListener
        requestChannel = exchange.getRequestChannel();
        requestChannel.getReadSetter().set(new ReadChannelListener(
                exchange.getConnection().getByteBufferPool(),
                transportState::inboundDataReceived,
                (e) -> transportState.transportReportStatus(Status.fromThrowable(e))
        ));

        // Create sink with callbacks for onSendingBytes and cancel
        this.sink = new UndertowSink(
                exchange,
                logId,
                this::onSendingBytes,
                () -> transportState.runOnTransportThread(() ->
                        transportState.transportReportStatus(Status.CANCELLED))
        );
    }

    @Override
    protected UndertowTransportState transportState() {
        return transportState;
    }

    @Override
    protected Sink abstractServerStreamSink() {
        return sink;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public String getAuthority() {
        return authority;
    }

    @Override
    public int streamId() {
        // We currently do not support this. What Undertow does is something like:
        // streamId += 2 (isClient ? (isUpgrade ? 3 : 1) : 2
        // This means we'd need to keep a counter, which is likely okay. See the io.undertow.protocols.http2.Http2Channel
        // for details.
        // Ours would be more simple and simply be something like isUpgrade ? 3 : 2 because we know this is not a client.
        // What we need to determine is if we just use a new counter each time a new UndertowServerStream is created.
        // For now, we'll just return -1 as that is what the Servlet implementation does. However, I think we should
        // implement this.
        return -1;
    }

    /**
     * Starts reading from the request channel. his must be called after the stream is created to begin receiving
     * request data.
     */
    // TODO (jrp) I don't like this, there must be somewhere else we can initialize this from
    public void startReading() {
        if (requestChannel != null) {
            requestChannel.resumeReads();
        } else {
            GrpcLogger.LOGGER.errorf("Request channel is null, cannot start reading for stream %s", logId);
        }
    }

    /**
     * Transport state implementation for Undertow-backed gRPC streams.
     * Static nested class to reduce coupling and make dependencies explicit.
     */
    static final class UndertowTransportState extends TransportState {

        private final SerializingExecutor transportThreadExecutor;
        private final InternalLogId logId;
        private final HttpServerExchange exchange;

        UndertowTransportState(
                final int maxMessageSize,
                final StatsTraceContext statsTraceCtx,
                final TransportTracer transportTracer,
                final Executor executor,
                final InternalLogId logId,
                final HttpServerExchange exchange) {
            super(maxMessageSize, statsTraceCtx, transportTracer);
            this.transportThreadExecutor = new SerializingExecutor(
                    executor != null ? executor : Runnable::run);
            this.logId = logId;
            this.exchange = exchange;
        }

        @Override
        public void runOnTransportThread(final Runnable r) {
            transportThreadExecutor.execute(r);
        }

        @Override
        public void bytesRead(final int processedBytes) {
        }

        @Override
        public void deframeFailed(final Throwable cause) {
            GrpcLogger.LOGGER.errorf(cause, "Deframing failed for stream %s", logId);
            transportReportStatus(Status.fromThrowable(cause));
            IoUtils.safeClose(exchange.getConnection());
        }
    }

    /**
     * Sink implementation for writing outbound data to Undertow's response stream.
     * Static nested class to reduce coupling and make dependencies explicit.
     */
    static final class UndertowSink implements Sink {

        private final HttpServerExchange exchange;
        private final Sender sender;
        private final InternalLogId logId;
        private final Consumer<Integer> onSendingBytesCallback;
        private final Runnable onCancelCallback;

        private List<PooledByteBuffer> queuedData;
        private boolean ready = true;
        private boolean closed;
        private volatile boolean headersSent = false;

        UndertowSink(
                final HttpServerExchange exchange,
                final InternalLogId logId,
                final Consumer<Integer> onSendingBytesCallback,
                final Runnable onCancelCallback) {
            this.exchange = exchange;
            this.sender = exchange.getResponseSender();
            this.logId = logId;
            this.onSendingBytesCallback = onSendingBytesCallback;
            this.onCancelCallback = onCancelCallback;
        }

        @Override
        public void writeHeaders(final Metadata headers, final boolean flush) {
            if (headersSent) {
                GrpcLogger.LOGGER.warnf("Headers already sent for stream %s", logId);
                return;
            }
            final HeaderMap exchangeHeaders = exchange.getResponseHeaders();
            exchangeHeaders.put(Headers.CONTENT_TYPE, "application/grpc");

            writeMetadataToHeaders(headers, exchangeHeaders);

            headersSent = true;

            if (flush) {
                doSend();
            }
        }

        @Override
        public void writeFrame(final WritableBuffer frame, final boolean flush, final int numMessages) {
            if (frame == null || frame.readableBytes() == 0) {
                if (flush) {
                    doSend();
                }
                return;
            }

            final int numBytes = frame.readableBytes();
            if (numBytes > 0) {
                onSendingBytesCallback.accept(numBytes);

                final UndertowWritableBufferAllocator.UndertowWritableBuffer buffer =
                        (UndertowWritableBufferAllocator.UndertowWritableBuffer) frame;
                final PooledByteBuffer pooledBuffer = buffer.buffer();

                pooledBuffer.getBuffer().flip();

                if (queuedData == null) {
                    queuedData = new ArrayList<>();
                }
                queuedData.add(pooledBuffer);
            }

            if (flush) {
                doSend();
            }
        }

        @Override
        public void writeTrailers(final Metadata trailers, final boolean headersSent, final Status status) {
            if (!headersSent) {
                writeHeaders(trailers, false);
            }

            final HeaderMap trailerMap = new HeaderMap();

            trailerMap.put(new HttpString("grpc-status"), String.valueOf(status.getCode().value()));
            if (status.getDescription() != null) {
                trailerMap.put(new HttpString("grpc-message"), status.getDescription());
            }

            if (trailers != null) {
                writeMetadataToHeaders(trailers, trailerMap);
            }

            exchange.putAttachment(HttpAttachments.RESPONSE_TRAILERS, trailerMap);

            closed = true;

            if (queuedData != null && !queuedData.isEmpty()) {
                doSend();
            } else if (ready) {
                sender.close();
            }
        }

        @Override
        public void cancel(final Status status) {
            onCancelCallback.run();
        }

        private void doSend() {
            if (ready) {
                final List<PooledByteBuffer> buffers;
                final ByteBuffer[] data;
                if (queuedData != null) {
                    buffers = queuedData;
                    queuedData = null;
                    data = new ByteBuffer[buffers.size()];
                    for (int i = 0; i < data.length; ++i) {
                        data[i] = buffers.get(i).getBuffer();
                    }
                } else {
                    buffers = Collections.emptyList();
                    data = new ByteBuffer[0];
                }
                ready = false;
                sender.send(data, new IoCallback() {
                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        for (final PooledByteBuffer i : buffers) {
                            i.close();
                        }
                        ready = true;
                        if (closed) {
                            sender.close();

                            if (queuedData != null) {
                                for (final PooledByteBuffer i : queuedData) {
                                    i.close();
                                }
                                queuedData = null;
                            }
                        } else if (queuedData != null) {
                            doSend();
                        }
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        for (final PooledByteBuffer i : buffers) {
                            i.close();
                        }
                        if (queuedData != null) {
                            for (final PooledByteBuffer i : queuedData) {
                                i.close();
                            }
                            queuedData = null;
                        }
                        GrpcLogger.LOGGER.errorf(exception, "Error sending response data for stream %s", logId);
                        cancel(Status.INTERNAL.withCause(exception).withDescription(GrpcLogger.LOGGER.failedToSendResponseData()));
                    }
                });
            }
        }

        private void writeMetadataToHeaders(final Metadata metadata, final HeaderMap headerMap) {
            final byte[][] serialized = TransportFrameUtil.toHttp2Headers(metadata);
            for (int i = 0; i < serialized.length; i += 2) {
                final String key = new String(serialized[i], StandardCharsets.US_ASCII);
                final String value = new String(serialized[i + 1], StandardCharsets.US_ASCII);
                headerMap.add(new HttpString(key), value);
            }
        }
    }
}
