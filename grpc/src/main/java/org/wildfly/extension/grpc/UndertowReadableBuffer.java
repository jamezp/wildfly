/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.grpc.internal.AbstractReadableBuffer;
import io.grpc.internal.ReadableBuffer;
import io.grpc.internal.ReadableBuffers;
import io.undertow.connector.PooledByteBuffer;

/**
 * ReadableBuffer implementation backed by Undertow's PooledByteBuffer.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
class UndertowReadableBuffer extends AbstractReadableBuffer {

    private final PooledByteBuffer buffer;

    UndertowReadableBuffer(final PooledByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int readableBytes() {
        return buffer.getBuffer().remaining();
    }

    @Override
    public int readUnsignedByte() {
        return buffer.getBuffer().get() & 0xFF;
    }

    @Override
    public void skipBytes(final int length) {
        buffer.getBuffer().position(buffer.getBuffer().position() + length);
    }

    @Override
    public void readBytes(final byte[] dest, final int destOffset, final int length) {
        buffer.getBuffer().get(dest, destOffset, length);
    }

    @Override
    public void readBytes(final ByteBuffer dest) {
        while (dest.hasRemaining() && buffer.getBuffer().hasRemaining()) {
            dest.put(buffer.getBuffer().get());
        }
    }

    @Override
    public void readBytes(final OutputStream dest, final int length) throws IOException {
        for (int i = 0; i < length; ++i) {
            dest.write(buffer.getBuffer().get());
        }
    }

    @Override
    public ReadableBuffer readBytes(final int length) {
        final byte[] data = new byte[length];
        buffer.getBuffer().get(data);
        return ReadableBuffers.wrap(data);
    }

    @Override
    public boolean hasArray() {
        return buffer.getBuffer().hasArray();
    }

    @Override
    public byte[] array() {
        return buffer.getBuffer().array();
    }

    @Override
    public int arrayOffset() {
        return buffer.getBuffer().arrayOffset();
    }

    @Override
    public void close() {
        buffer.close();
    }
}