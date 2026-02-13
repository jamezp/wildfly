/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import io.grpc.internal.WritableBuffer;
import io.grpc.internal.WritableBufferAllocator;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;

/**
 * WritableBufferAllocator implementation for Undertow that allocates buffers from a ByteBufferPool.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class UndertowWritableBufferAllocator implements WritableBufferAllocator {
    private final ByteBufferPool pool;

    public UndertowWritableBufferAllocator(final ByteBufferPool pool) {
        this.pool = pool;
    }

    @Override
    public WritableBuffer allocate(final int capacityHint) {
        // Allocate a new buffer from the pool for each request
        // capacityHint is ignored as pool controls buffer size
        return new UndertowWritableBuffer(pool.allocate());
    }

    /**
     * An implementation that delegates to the Undertow {@link PooledByteBuffer}
     */
    record UndertowWritableBuffer(PooledByteBuffer buffer) implements WritableBuffer {

        public void write(byte[] src, int srcIndex, int length) {
            buffer.getBuffer().put(src, srcIndex, length);
        }

        public void write(byte b) {
            buffer.getBuffer().put(b);
        }

        public int writableBytes() {
            return buffer.getBuffer().remaining();
        }

        public int readableBytes() {
            return buffer.getBuffer().position();
        }

        public void release() {
            buffer.close();
        }
    }
}
