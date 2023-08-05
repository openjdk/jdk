/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import jdk.internal.misc.Unsafe;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8299684
 * @summary Unit test for the JNI function NewDirectByteBuffer
 * @requires (sun.arch.data.model == "64" & os.maxMemory >= 8g)
 * @modules java.base/jdk.internal.misc
 * @run junit/othervm/native NewDirectByteBuffer
 */
public class NewDirectByteBuffer {
    private static final Unsafe UNSAFE;
    static {
        System.loadLibrary("NewDirectByteBuffer");
        UNSAFE = Unsafe.getUnsafe();
    }

    private static final void checkBuffer(ByteBuffer buf, long capacity) {
        // Verify that the JNI function returns the correct capacity
        assertEquals(capacity, getDirectByteBufferCapacity(buf),
            "GetDirectBufferCapacity returned unexpected value");

        // Verify that the initial state values are correct
        assertTrue(buf.isDirect(), "Buffer is not direct");
        assertFalse(buf.hasArray(), "Buffer has an array");
        if (capacity > 0) {
            assertTrue(buf.hasRemaining(), "Buffer has no remaining values");
        }
        assertFalse(buf.isReadOnly(), "Buffer s read-only");
        assertEquals(capacity, buf.capacity(),
            "Buffer::capacity returned unexpected value");
        assertEquals(0L, buf.position(),
            "Buffer::position returned unexpected value");
        assertEquals(capacity, buf.limit(),
            "Buffer::limit returned unexpected value");

        // Verify that the various state mutators work correctly
        int halfPos = buf.capacity()/2;
        buf.position(halfPos);
        assertEquals(halfPos, buf.position(),
            "Position not set to halfPos");
        assertEquals(buf.capacity() - halfPos, buf.remaining(),
            "Remaining not capacity - halfPos");

        buf.mark();

        int twoThirdsPos = 2*(buf.capacity()/3);
        buf.position(twoThirdsPos);
        assertEquals(twoThirdsPos, buf.position(),
            "Position not set to twoThirdsPos");
        assertEquals(buf.capacity() - twoThirdsPos, buf.remaining(),
            "Remaining != capacity - twoThirdsPos");

        buf.reset();
        assertEquals(halfPos, buf.position(),
            "Buffer not reset to halfPos");

        buf.limit(twoThirdsPos);
        assertEquals(twoThirdsPos, buf.limit(),
            "Limit not set to twoThirdsPos");
        assertEquals(twoThirdsPos - halfPos, buf.remaining(),
            "Remaining != twoThirdsPos - halfPos");

        buf.position(twoThirdsPos);
        assertFalse(buf.hasRemaining(), "Buffer has remaining values");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, (long)Integer.MAX_VALUE/2,
        (long)Integer.MAX_VALUE - 1, (long)Integer.MAX_VALUE})
    void legalCapacities(long capacity) {
        long addr;
        try {
            addr = UNSAFE.allocateMemory(capacity);
        } catch (OutOfMemoryError ignore) {
            System.err.println("legalCapacities( " + capacity
                + ") test skipped due to insufficient memory");
            return;
        }
        try {
            ByteBuffer buf = newDirectByteBuffer(addr, capacity);
            assertEquals(addr, getDirectBufferAddress(buf),
                "GetDirectBufferAddress does not return supplied address");
            checkBuffer(buf, capacity);
        } finally {
            UNSAFE.freeMemory(addr);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, (long)Integer.MIN_VALUE - 1L, -1L,
        (long)Integer.MAX_VALUE + 1L, 3_000_000_000L, 5_000_000_000L,
        Long.MAX_VALUE})
    void illegalCapacities(long capacity) {
        assertThrows(IllegalArgumentException.class, () -> {
            long addr = UNSAFE.allocateMemory(1);
            try {
                ByteBuffer buf = newDirectByteBuffer(addr, capacity);
            } finally {
                UNSAFE.freeMemory(addr);
            }
        });
    }

    // See libNewDirectByteBuffer.c for implementations.
    private static native ByteBuffer newDirectByteBuffer(long addr, long capacity);
    private static native long getDirectByteBufferCapacity(ByteBuffer buf);
    private static native long getDirectBufferAddress(ByteBuffer buf);
}
