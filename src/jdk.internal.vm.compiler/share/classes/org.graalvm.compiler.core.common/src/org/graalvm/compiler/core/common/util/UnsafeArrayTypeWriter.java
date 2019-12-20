/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package org.graalvm.compiler.core.common.util;

import static org.graalvm.compiler.core.common.util.TypeConversion.asS1;
import static org.graalvm.compiler.core.common.util.TypeConversion.asS2;
import static org.graalvm.compiler.core.common.util.TypeConversion.asS4;
import static org.graalvm.compiler.core.common.util.TypeConversion.asU1;
import static org.graalvm.compiler.core.common.util.TypeConversion.asU2;
import static org.graalvm.compiler.core.common.util.TypeConversion.asU4;
import static org.graalvm.compiler.serviceprovider.GraalUnsafeAccess.getUnsafe;

import java.nio.ByteBuffer;

import org.graalvm.compiler.core.common.calc.UnsignedMath;

import sun.misc.Unsafe;

/**
 * Provides low-level sequential write access to a byte[] array for signed and unsigned values of
 * size 1, 2, 4, and 8 bytes. To avoid copying an array when the buffer size is no longer
 * sufficient, the buffer is split into chunks of a fixed size.
 *
 * The flag {@code supportsUnalignedMemoryAccess} must be set according to the capabilities of the
 * hardware architecture: the value {@code true} allows more efficient memory access on
 * architectures that support unaligned memory accesses; the value {@code false} is the safe
 * fallback that works on every hardware.
 */
public abstract class UnsafeArrayTypeWriter implements TypeWriter {
    private static final Unsafe UNSAFE = getUnsafe();
    private static final int MIN_CHUNK_LENGTH = 200;
    private static final int MAX_CHUNK_LENGTH = 16000;

    // Constants for UNSIGNED5 coding of Pack200
    public static final long HIGH_WORD_SHIFT = 6;
    public static final long NUM_HIGH_CODES = 1 << HIGH_WORD_SHIFT; // number of high codes (64)
    public static final long NUM_LOW_CODES = (1 << Byte.SIZE) - NUM_HIGH_CODES;
    public static final long MAX_BYTES = 11;

    static class Chunk {
        protected final byte[] data;
        protected int size;
        protected Chunk next;

        protected Chunk(int arrayLength) {
            data = new byte[arrayLength];
        }
    }

    protected final Chunk firstChunk;
    protected Chunk writeChunk;
    protected int totalSize;

    public static UnsafeArrayTypeWriter create(boolean supportsUnalignedMemoryAccess) {
        if (supportsUnalignedMemoryAccess) {
            return new UnalignedUnsafeArrayTypeWriter();
        } else {
            return new AlignedUnsafeArrayTypeWriter();
        }
    }

    protected UnsafeArrayTypeWriter() {
        firstChunk = new Chunk(MIN_CHUNK_LENGTH);
        writeChunk = firstChunk;
    }

    @Override
    public final long getBytesWritten() {
        return totalSize;
    }

    /**
     * Copies the buffer into the provided byte[] array of length {@link #getBytesWritten()}.
     */
    public final byte[] toArray(byte[] result) {
        assert result.length == totalSize;
        int resultIdx = 0;
        for (Chunk cur = firstChunk; cur != null; cur = cur.next) {
            System.arraycopy(cur.data, 0, result, resultIdx, cur.size);
            resultIdx += cur.size;
        }
        assert resultIdx == totalSize;
        return result;
    }

    /** Copies the buffer into the provided ByteBuffer at its current position. */
    public final ByteBuffer toByteBuffer(ByteBuffer buffer) {
        assert buffer.remaining() <= totalSize;
        int initialPos = buffer.position();
        for (Chunk cur = firstChunk; cur != null; cur = cur.next) {
            buffer.put(cur.data, 0, cur.size);
        }
        assert buffer.position() - initialPos == totalSize;
        return buffer;
    }

    public final byte[] toArray() {
        byte[] result = new byte[TypeConversion.asS4(getBytesWritten())];
        return toArray(result);
    }

    @Override
    public final void putS1(long value) {
        long offset = writeOffset(Byte.BYTES);
        UNSAFE.putByte(writeChunk.data, offset, asS1(value));
    }

    @Override
    public final void putU1(long value) {
        long offset = writeOffset(Byte.BYTES);
        UNSAFE.putByte(writeChunk.data, offset, asU1(value));
    }

    @Override
    public final void putU2(long value) {
        putS2(asU2(value));
    }

    @Override
    public final void putU4(long value) {
        putS4(asU4(value));
    }

    @Override
    public void putS2(long value) {
        long offset = writeOffset(Short.BYTES);
        putS2(value, writeChunk, offset);
    }

    @Override
    public void putS4(long value) {
        long offset = writeOffset(Integer.BYTES);
        putS4(value, writeChunk, offset);
    }

    @Override
    public void putS8(long value) {
        long offset = writeOffset(Long.BYTES);
        putS8(value, writeChunk, offset);
    }

    protected abstract void putS2(long value, Chunk chunk, long offset);

    protected abstract void putS4(long value, Chunk chunk, long offset);

    protected abstract void putS8(long value, Chunk chunk, long offset);

    protected long writeOffset(int writeBytes) {
        if (writeChunk.size + writeBytes >= writeChunk.data.length) {
            Chunk newChunk = new Chunk(Math.min(writeChunk.data.length * 2, MAX_CHUNK_LENGTH));
            writeChunk.next = newChunk;
            writeChunk = newChunk;
        }

        assert Unsafe.ARRAY_BYTE_INDEX_SCALE == 1;
        long result = writeChunk.size + Unsafe.ARRAY_BYTE_BASE_OFFSET;

        totalSize += writeBytes;
        writeChunk.size += writeBytes;
        assert writeChunk.size <= writeChunk.data.length;

        return result;
    }

    @Override
    public void patchS4(long value, long offset) {
        long chunkStartOffset = 0;
        Chunk chunk = firstChunk;
        while (chunkStartOffset + chunk.size <= offset) {
            chunkStartOffset += chunk.size;
            chunk = chunk.next;
        }

        long targetOffset = offset - chunkStartOffset;
        assert targetOffset + Integer.BYTES <= chunk.size : "out of bounds";
        putS4(value, chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + targetOffset);
    }

    @Override
    public void putSV(long value) {
        // this is a modified version of the SIGNED5 encoding from Pack200
        write(encodeSign(value));
    }

    @Override
    public void putUV(long value) {
        // this is a modified version of the UNSIGNED5 encoding from Pack200
        write(value);
    }

    private static long encodeSign(long value) {
        return (value << 1) ^ (value >> 63);
    }

    private void write(long value) {
        if (UnsignedMath.belowThan(value, NUM_LOW_CODES)) {
            putU1(value);
        } else {
            writePacked(value);
        }
    }

    private void writePacked(long value) {
        long sum = value;
        for (int i = 1; UnsignedMath.aboveOrEqual(sum, NUM_LOW_CODES) && i < MAX_BYTES; i++) {
            sum -= NUM_LOW_CODES;
            long u1 = NUM_LOW_CODES + (sum & (NUM_HIGH_CODES - 1)); // this is a "high code"
            sum >>>= HIGH_WORD_SHIFT; // extracted 6 bits
            putU1(u1);
        }

        // remainder is either a "low code" or the last byte
        assert sum == (sum & 0xFF) : "not a byte";
        putU1(sum & 0xFF);
    }
}

final class UnalignedUnsafeArrayTypeWriter extends UnsafeArrayTypeWriter {
    private static final Unsafe UNSAFE = getUnsafe();

    @Override
    protected void putS2(long value, Chunk chunk, long offset) {
        UNSAFE.putShort(chunk.data, offset, asS2(value));
    }

    @Override
    protected void putS4(long value, Chunk chunk, long offset) {
        UNSAFE.putInt(chunk.data, offset, asS4(value));
    }

    @Override
    protected void putS8(long value, Chunk chunk, long offset) {
        UNSAFE.putLong(chunk.data, offset, value);
    }
}

final class AlignedUnsafeArrayTypeWriter extends UnsafeArrayTypeWriter {
    private static final Unsafe UNSAFE = getUnsafe();

    @Override
    protected void putS2(long value, Chunk chunk, long offset) {
        UNSAFE.putByte(chunk.data, offset + 0, (byte) (value >> 0));
        UNSAFE.putByte(chunk.data, offset + 1, (byte) (value >> 8));
    }

    @Override
    protected void putS4(long value, Chunk chunk, long offset) {
        UNSAFE.putByte(chunk.data, offset + 0, (byte) (value >> 0));
        UNSAFE.putByte(chunk.data, offset + 1, (byte) (value >> 8));
        UNSAFE.putByte(chunk.data, offset + 2, (byte) (value >> 16));
        UNSAFE.putByte(chunk.data, offset + 3, (byte) (value >> 24));
    }

    @Override
    protected void putS8(long value, Chunk chunk, long offset) {
        UNSAFE.putByte(chunk.data, offset + 0, (byte) (value >> 0));
        UNSAFE.putByte(chunk.data, offset + 1, (byte) (value >> 8));
        UNSAFE.putByte(chunk.data, offset + 2, (byte) (value >> 16));
        UNSAFE.putByte(chunk.data, offset + 3, (byte) (value >> 24));
        UNSAFE.putByte(chunk.data, offset + 4, (byte) (value >> 32));
        UNSAFE.putByte(chunk.data, offset + 5, (byte) (value >> 40));
        UNSAFE.putByte(chunk.data, offset + 6, (byte) (value >> 48));
        UNSAFE.putByte(chunk.data, offset + 7, (byte) (value >> 56));
    }
}
