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

/*
 * @test
 * @bug 8299684
 * @summary Verify that JNI NewDirectByteBuffer throws IllegalArgumentException
 * if the capacity is negative or greater than Integer::MAX_VALUE
 * @run main/native NewDirectByteBuffer
 */
public class NewDirectByteBuffer {
    static {
        System.loadLibrary("NewDirectByteBuffer");
    }

    private static final long[] LEGAL_CAPACITIES = {
        0L,
        1L,
        (long)Integer.MAX_VALUE/2,
        (long)Integer.MAX_VALUE - 1,
        (long)Integer.MAX_VALUE
    };

    private static final long[] ILLEGAL_CAPACITIES = {
        (long)Integer.MIN_VALUE - 1L,
        -1L,
        (long)Integer.MAX_VALUE + 1L,
        3_000_000_000L,
        5_000_000_000L
    };

    private static final void checkBuffer(ByteBuffer buf, long capacity) {
        if (!buf.isDirect())
            throw new RuntimeException("Buffer is not direct");
        long bufferCapacity = getDirectBufferCapacity(buf);
        if (bufferCapacity != capacity)
            throw new RuntimeException("GetDirectBufferCapacity "
                + bufferCapacity + " is not " + capacity);
        if (buf.capacity() != capacity)
            throw new RuntimeException("buf.capacity() "
                + buf.capacity() + " is not " + capacity);
        if (buf.position() != 0)
            throw new RuntimeException("buf.position() "
                + buf.position() + " is nonzero");
        if (buf.limit() != capacity)
            throw new RuntimeException("buf.limit() "
                + buf.limit() + " is not " + capacity);
    }

    public static void main(String[] args) {
        System.out.println("--- Legal Capacities ---");
        for (long cap : LEGAL_CAPACITIES) {
            System.out.println("Capacity " + cap);
            ByteBuffer buf = newDirectByteBuffer(cap);
            if (buf != null) {
                try {
                    checkBuffer(buf, cap);
                    System.out.println("Verified buffer for capacity " + cap);
                } finally {
                    freeDirectBufferMemory(buf);
                }
            } else {
                throw new RuntimeException("Direct buffer is null but no OOME");
            }
        }

        System.out.println("\n--- Illegal Capacities ---");
        for (long cap : ILLEGAL_CAPACITIES) {
            System.out.println("Capacity " + cap);
            try {
                ByteBuffer buf = newDirectByteBuffer(cap);
                if (buf != null) {
                    freeDirectBufferMemory(buf);
                }
                throw new RuntimeException("IAE not thrown for capacity " + cap);
            } catch (IllegalArgumentException expected) {
                System.out.println("Caught expected IAE for capacity " + cap);
            }
        }
    }

    // See libNewDirectByteBuffer.c for implementations.
    private static native ByteBuffer newDirectByteBuffer(long size);
    private static native long getDirectBufferCapacity(ByteBuffer buf);
    private static native void freeDirectBufferMemory(ByteBuffer buf);
}
