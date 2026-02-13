package org.wildfly.extension.grpc;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.wildfly.extension.grpc._private.GrpcLogger;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
class ReadChannelListener implements ChannelListener<StreamSourceChannel> {
    private final ByteBufferPool pool;
    private final BiConsumer<UndertowReadableBuffer, Boolean> dataReceiver;
    private final Consumer<Exception> errorHandler;

    ReadChannelListener(final ByteBufferPool pool, final BiConsumer<UndertowReadableBuffer, Boolean> dataReceiver, final Consumer<Exception> errorHandler) {
        this.pool = pool;
        this.dataReceiver = dataReceiver;
        this.errorHandler = errorHandler;
    }

    @Override
    public void handleEvent(final StreamSourceChannel channel) {
        for (; ; ) {
            final PooledByteBuffer pooled = pool.allocate();
            try {
                final int res = channel.read(pooled.getBuffer());
                if (res == -1) {
                    pooled.getBuffer().flip();
                    dataReceiver.accept(new UndertowReadableBuffer(pooled), true);
                    return;
                } else if (res == 0) {
                    pooled.close();
                    return;
                } else {
                    pooled.getBuffer().flip();
                    dataReceiver.accept(new UndertowReadableBuffer(pooled), false);
                }
            } catch (final Exception e) {
                // TODO (jrp) should we log this as an error?
                GrpcLogger.LOGGER.errorf(e, "Error reading gRPC request data");
                pooled.close();
                errorHandler.accept(e);
                return;
            }
        }
    }
}
