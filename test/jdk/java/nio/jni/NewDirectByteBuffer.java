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

    private static final long[] ILLEGAL_CAPACITIES = {
        (long)Integer.MIN_VALUE - 1L,
        -1L,
        (long)Integer.MAX_VALUE + 1L,
        3_000_000_000L,
        5_000_000_000L
    };

    private static final long[] LEGAL_CAPACITIES = {
        0L,
        1L,
        (long)Integer.MAX_VALUE - 1,
        (long)Integer.MAX_VALUE
    };

    public static void main(String[] args) {
        System.out.println("--- Legal Capacities ---");
        for (long cap : LEGAL_CAPACITIES) {
            System.out.println("Allocating buffer with capacity " + cap);
            ByteBuffer buf = allocBigBuffer(cap);
            long bufferCapacity = getLongCapacity(buf);
            System.out.printf("buf.capacity(): %d, getLongCapacity(buf): %d%n",
                buf.capacity(), bufferCapacity);
            if (bufferCapacity != cap) {
                throw new RuntimeException("GetDirectBufferCapacity returned "
                    + bufferCapacity + ", not " + cap + "as expected");
            }
        }

        System.out.println("\n--- Illegal Capacities ---");
        for (long cap : ILLEGAL_CAPACITIES) {
            try {
                ByteBuffer buf = allocBigBuffer(cap);
                throw new RuntimeException("IAE not thrown for capacity " + cap);
            } catch (IllegalArgumentException expected) {
                System.out.println("Caught expected IAE for capacity " + cap);
            }
        }
    }

    // See libNewDirectByteBuffer.c for implementations.
    private static native ByteBuffer allocBigBuffer(long size);
    private static native long getLongCapacity(ByteBuffer buf);
}
