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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/*
 * @test
 * @bug 8299684
 * @summary Verify that JNI NewDirectByteBuffer throws
 * IllegalArgumentException if the capacity is negative or greater than
 * Integer::MAX_VALUE
 * @requires (sun.arch.data.model == "64" & os.maxMemory >= 8g)
 * @run junit/othervm/native NewDirectByteBuffer
 */
public class NewDirectByteBuffer {
    static {
        System.loadLibrary("NewDirectByteBuffer");
    }

    private static final void checkBuffer(ByteBuffer buf, long capacity) {
        Assertions.assertTrue(buf.isDirect(), "Buffer is not direct");
        Assertions.assertEquals(capacity, getDirectByteBufferCapacity(buf),
            "GetDirectBufferCapacity returned unexpected value");
        Assertions.assertEquals(capacity, buf.capacity(),
            "Buffer::capacity returned unexpected value");
        Assertions.assertEquals(0L, buf.position(),
            "Buffer::position returned unexpected value");
        Assertions.assertEquals(capacity, buf.limit(),
            "Buffer::limit returned unexpected value");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, (long)Integer.MAX_VALUE/2,
        (long)Integer.MAX_VALUE - 1, (long)Integer.MAX_VALUE})
    void legalCapacities(long capacity) {
        try {
            final AtomicReference<ByteBuffer> bufHolder = new AtomicReference();
            Assertions.assertDoesNotThrow(() -> {
                try {
                    bufHolder.set(newDirectByteBuffer(capacity));
                } finally {
                    ByteBuffer buf = bufHolder.get();
                    if (buf != null) {
                        freeDirectByteBufferMemory(buf);
                    }
                }
            });
            ByteBuffer buf = bufHolder.get();
            if (buf != null) {
                checkBuffer(buf, capacity);
            }
        } catch (OutOfMemoryError ignored) {
            // Ignore the error
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, (long)Integer.MIN_VALUE - 1L, -1L,
        (long)Integer.MAX_VALUE + 1L, 3_000_000_000L, 5_000_000_000L,
        Long.MAX_VALUE})
    void illegalCapacities(long capacity) {
        try {
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                ByteBuffer buf = newDirectByteBuffer(capacity);
                if (buf != null) {
                    freeDirectByteBufferMemory(buf);
                }
            });
        } catch (OutOfMemoryError ignored) {
            // Ignore the error
        }
    }

    // See libNewDirectByteBuffer.c for implementations.
    private static native ByteBuffer newDirectByteBuffer(long size);
    private static native long getDirectByteBufferCapacity(ByteBuffer buf);
    private static native void freeDirectByteBufferMemory(ByteBuffer buf);
}
