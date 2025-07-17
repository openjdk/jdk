/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.java2d.pipe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import static java.lang.foreign.ValueLayout.*;


/**
 * The RenderBuffer class is a simplified, high-performance class
 * used for buffering rendering operations in a single-threaded rendering
 * environment.  Its functionality is similar to the ByteBuffer and related
 * NIO classes.  However, the methods in this class perform little to no
 * alignment or bounds checks for performance reasons.  Therefore, it is
 * the caller's responsibility to ensure that all put() calls are properly
 * aligned and within bounds:
 *   - int and float values must be aligned on 4-byte boundaries
 *   - long and double values must be aligned on 8-byte boundaries
 * Failure to do so will result in exceptions from the FFM API, or worse.
 *
 * This class only includes the bare minimum of methods to support
 * single-threaded rendering.  For example, there is no put(double[]) method
 * because we currently have no need for such a method in the STR classes.
 */
public final class RenderBuffer {

    /**
     * These constants represent the size of various data types (in bytes).
     */
    private static final int SIZEOF_BYTE   = Byte.BYTES;
    private static final int SIZEOF_SHORT  = Short.BYTES;
    private static final int SIZEOF_INT    = Integer.BYTES;
    private static final int SIZEOF_FLOAT  = Float.BYTES;
    private static final int SIZEOF_LONG   = Long.BYTES;
    private static final int SIZEOF_DOUBLE = Double.BYTES;

    /**
     * Measurements show that using the copy API from a segment backed by a heap
     * array gets reliably faster than individual puts around a length of 10.
     * However the time is miniscule in the context of what it is used for
     * and much more than adequate, so no problem expected if this changes over time.
     */
    private static final int COPY_FROM_ARRAY_THRESHOLD = 10;

    private final MemorySegment segment;
    private int curOffset;

    private RenderBuffer(int numBytes) {
        segment = Arena.global().allocate(numBytes, SIZEOF_DOUBLE);
        curOffset = 0;
    }

    /**
     * Allocates a fresh buffer using the machine endianness.
     */
    public static RenderBuffer allocate(int numBytes) {
        return new RenderBuffer(numBytes);
    }

    /**
     * Returns the base address of the underlying memory buffer.
     */
    public final long getAddress() {
        return segment.address();
    }

    /**
     * The behavior (and names) of the following methods are nearly
     * identical to their counterparts in the various NIO Buffer classes.
     */

    public final int capacity() {
        return (int)segment.byteSize();
    }

    public final int remaining() {
        return (capacity() - curOffset);
    }

    public final int position() {
        return curOffset;
    }

    public final void position(int bytePos) {
        curOffset = bytePos;
    }

    public final void clear() {
        curOffset = 0;
    }

    public final RenderBuffer skip(int numBytes) {
        curOffset += numBytes;
        return this;
    }

    /**
     * putByte() methods...
     */

    public final RenderBuffer putByte(byte x) {
        segment.set(JAVA_BYTE, curOffset, x);
        curOffset += SIZEOF_BYTE;
        return this;
    }

    public RenderBuffer put(byte[] x) {
        return put(x, 0, x.length);
    }

    public RenderBuffer put(byte[] x, int offset, int length) {
        if (length > COPY_FROM_ARRAY_THRESHOLD) {
            MemorySegment.copy(x, offset, segment, JAVA_BYTE, curOffset, length);
            position(position() + length * SIZEOF_BYTE);
        } else {
            int end = offset + length;
            for (int i = offset; i < end; i++) {
                putByte(x[i]);
            }
        }
        return this;
    }

    /**
     * putShort() methods...
     */

    public final RenderBuffer putShort(short x) {
        // assert (position() % SIZEOF_SHORT == 0);
        segment.set(JAVA_SHORT, curOffset, x);
        curOffset += SIZEOF_SHORT;
        return this;
    }

    public RenderBuffer put(short[] x) {
        return put(x, 0, x.length);
    }

    public RenderBuffer put(short[] x, int offset, int length) {
        // assert (position() % SIZEOF_SHORT == 0);
        if (length > COPY_FROM_ARRAY_THRESHOLD) {
            MemorySegment.copy(x, offset, segment, JAVA_SHORT, curOffset, length);
            position(position() + length * SIZEOF_SHORT);
        } else {
            int end = offset + length;
            for (int i = offset; i < end; i++) {
                putShort(x[i]);
            }
        }
        return this;
    }

    /**
     * putInt() methods...
     */

    public final RenderBuffer putInt(int pos, int x) {
        // assert (getAddress() + pos % SIZEOF_INT == 0);
        segment.set(JAVA_INT, pos, x);
        return this;
    }

    public final RenderBuffer putInt(int x) {
        // assert (position() % SIZEOF_INT == 0);
        segment.set(JAVA_INT, curOffset, x);
        curOffset += SIZEOF_INT;
        return this;
    }

    public RenderBuffer put(int[] x) {
        return put(x, 0, x.length);
    }

    public RenderBuffer put(int[] x, int offset, int length) {
        // assert (position() % SIZEOF_INT == 0);
        if (length > COPY_FROM_ARRAY_THRESHOLD) {
            MemorySegment.copy(x, offset, segment, JAVA_INT, curOffset, length);
            position(position() + length * SIZEOF_INT);
        } else {
            int end = offset + length;
            for (int i = offset; i < end; i++) {
                putInt(x[i]);
            }
        }
        return this;
    }

    /**
     * putFloat() methods...
     */

    public final RenderBuffer putFloat(float x) {
        // assert (position() % SIZEOF_FLOAT == 0);
        segment.set(JAVA_FLOAT, curOffset, x);
        curOffset += SIZEOF_FLOAT;
        return this;
    }

    public RenderBuffer put(float[] x) {
        return put(x, 0, x.length);
    }

    public RenderBuffer put(float[] x, int offset, int length) {
        // assert (position() % SIZEOF_FLOAT == 0);
        if (length > COPY_FROM_ARRAY_THRESHOLD) {
            MemorySegment.copy(x, offset, segment, JAVA_FLOAT, curOffset, length);
            position(position() + length * SIZEOF_FLOAT);
        } else {
            int end = offset + length;
            for (int i = offset; i < end; i++) {
                putFloat(x[i]);
            }
        }
        return this;
    }

    /**
     * putLong() methods...
     */

    public final RenderBuffer putLong(long x) {
        // assert (position() % SIZEOF_LONG == 0);
        segment.set(JAVA_LONG, curOffset, x);
        curOffset += SIZEOF_LONG;
        return this;
    }

    public RenderBuffer put(long[] x) {
        return put(x, 0, x.length);
    }

    public RenderBuffer put(long[] x, int offset, int length) {
        // assert (position() % SIZEOF_LONG == 0);
        if (length > COPY_FROM_ARRAY_THRESHOLD) {
            MemorySegment.copy(x, offset, segment, JAVA_LONG, curOffset, length);
            position(position() + length * SIZEOF_LONG);
        } else {
            int end = offset + length;
            for (int i = offset; i < end; i++) {
                putLong(x[i]);
            }
        }
        return this;
    }

    /**
     * putDouble() method(s)...
     */

    public final RenderBuffer putDouble(double x) {
        // assert (position() % SIZEOF_DOUBLE == 0);
        segment.set(JAVA_DOUBLE, curOffset, x);
        curOffset += SIZEOF_DOUBLE;
        return this;
    }
}
