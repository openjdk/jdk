/*
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import static java.lang.Integer.reverseBytes;
import static java.lang.Long.reverseBytes;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import jdk.internal.misc.Unsafe;

/**
 * Optimized methods for converting between byte[] and int[]/long[], both for
 * big endian and little endian byte orders.
 *
 * NOTE that ArrayIndexOutOfBoundsException will be thrown if the bounds checks
 * failed.
 *
 * This class may also be helpful in improving the performance of the
 * crypto code in the SunJCE provider. However, for now it is only accessible by
 * the message digest implementation in the SUN provider.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
final class ByteArrayAccess {

    private ByteArrayAccess() {
        // empty
    }

    private static final class LE {
        private static final VarHandle INT_ARRAY
                = MethodHandles.byteArrayViewVarHandle(int[].class,
                ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();

        private static final VarHandle LONG_ARRAY
                = MethodHandles.byteArrayViewVarHandle(long[].class,
                ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();
    }

    private static final class BE {
        private static final VarHandle INT_ARRAY
                = MethodHandles.byteArrayViewVarHandle(int[].class,
                ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();

        private static final VarHandle LONG_ARRAY
                = MethodHandles.byteArrayViewVarHandle(long[].class,
                ByteOrder.BIG_ENDIAN).withInvokeExactBehavior();
    }
    /**
     * byte[] to int[] conversion, little endian byte order.
     */
    static void b2iLittle(byte[] in, int inOfs, int[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len) ||
            (outOfs < 0) || ((out.length - outOfs) < len/4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += inOfs;
        while (inOfs < len) {
            out[outOfs++] = (int) LE.INT_ARRAY.get(in, inOfs);
            inOfs += 4;
        }
    }

    // Special optimization of b2iLittle(in, inOfs, out, 0, 64)
    static void b2iLittle64(byte[] in, int inOfs, int[] out) {
        if ((inOfs < 0) || ((in.length - inOfs) < 64) ||
            (out.length < 16)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        out[ 0] = (int) LE.INT_ARRAY.get(in, inOfs     );
        out[ 1] = (int) LE.INT_ARRAY.get(in, inOfs +  4);
        out[ 2] = (int) LE.INT_ARRAY.get(in, inOfs +  8);
        out[ 3] = (int) LE.INT_ARRAY.get(in, inOfs + 12);
        out[ 4] = (int) LE.INT_ARRAY.get(in, inOfs + 16);
        out[ 5] = (int) LE.INT_ARRAY.get(in, inOfs + 20);
        out[ 6] = (int) LE.INT_ARRAY.get(in, inOfs + 24);
        out[ 7] = (int) LE.INT_ARRAY.get(in, inOfs + 28);
        out[ 8] = (int) LE.INT_ARRAY.get(in, inOfs + 32);
        out[ 9] = (int) LE.INT_ARRAY.get(in, inOfs + 36);
        out[10] = (int) LE.INT_ARRAY.get(in, inOfs + 40);
        out[11] = (int) LE.INT_ARRAY.get(in, inOfs + 44);
        out[12] = (int) LE.INT_ARRAY.get(in, inOfs + 48);
        out[13] = (int) LE.INT_ARRAY.get(in, inOfs + 52);
        out[14] = (int) LE.INT_ARRAY.get(in, inOfs + 56);
        out[15] = (int) LE.INT_ARRAY.get(in, inOfs + 60);
    }

    /**
     * int[] to byte[] conversion, little endian byte order.
     */
    static void i2bLittle(int[] in, int inOfs, byte[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len/4) ||
            (outOfs < 0) || ((out.length - outOfs) < len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += outOfs;
        while (outOfs < len) {
            LE.INT_ARRAY.set(out, outOfs, in[inOfs++]);
            outOfs += 4;
        }
    }

    // Store one 32-bit value into out[outOfs..outOfs+3] in little endian order.
    static void i2bLittle4(int val, byte[] out, int outOfs) {
        if ((outOfs < 0) || ((out.length - outOfs) < 4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        LE.INT_ARRAY.set(out, outOfs, val);
    }

    /**
     * byte[] to int[] conversion, big endian byte order.
     */
    static void b2iBig(byte[] in, int inOfs, int[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len) ||
            (outOfs < 0) || ((out.length - outOfs) < len/4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += inOfs;
        while (inOfs < len) {
            out[outOfs++] = (int) BE.INT_ARRAY.get(in, inOfs);
            inOfs += 4;
        }
    }

    // Special optimization of b2iBig(in, inOfs, out, 0, 64)
    static void b2iBig64(byte[] in, int inOfs, int[] out) {
        if ((inOfs < 0) || ((in.length - inOfs) < 64) ||
            (out.length < 16)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        out[ 0] = (int) BE.INT_ARRAY.get(in, inOfs     );
        out[ 1] = (int) BE.INT_ARRAY.get(in, inOfs +  4);
        out[ 2] = (int) BE.INT_ARRAY.get(in, inOfs +  8);
        out[ 3] = (int) BE.INT_ARRAY.get(in, inOfs + 12);
        out[ 4] = (int) BE.INT_ARRAY.get(in, inOfs + 16);
        out[ 5] = (int) BE.INT_ARRAY.get(in, inOfs + 20);
        out[ 6] = (int) BE.INT_ARRAY.get(in, inOfs + 24);
        out[ 7] = (int) BE.INT_ARRAY.get(in, inOfs + 28);
        out[ 8] = (int) BE.INT_ARRAY.get(in, inOfs + 32);
        out[ 9] = (int) BE.INT_ARRAY.get(in, inOfs + 36);
        out[10] = (int) BE.INT_ARRAY.get(in, inOfs + 40);
        out[11] = (int) BE.INT_ARRAY.get(in, inOfs + 44);
        out[12] = (int) BE.INT_ARRAY.get(in, inOfs + 48);
        out[13] = (int) BE.INT_ARRAY.get(in, inOfs + 52);
        out[14] = (int) BE.INT_ARRAY.get(in, inOfs + 56);
        out[15] = (int) BE.INT_ARRAY.get(in, inOfs + 60);
    }

    /**
     * int[] to byte[] conversion, big endian byte order.
     */
    static void i2bBig(int[] in, int inOfs, byte[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len/4) ||
            (outOfs < 0) || ((out.length - outOfs) < len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += outOfs;
        while (outOfs < len) {
            BE.INT_ARRAY.set(out, outOfs, in[inOfs++]);
            outOfs += 4;
        }
    }

    // Store one 32-bit value into out[outOfs..outOfs+3] in big endian order.
    static void i2bBig4(int val, byte[] out, int outOfs) {
        if ((outOfs < 0) || ((out.length - outOfs) < 4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        BE.INT_ARRAY.set(out, outOfs, val);
    }

    /**
     * byte[] to long[] conversion, big endian byte order.
     */
    static void b2lBig(byte[] in, int inOfs, long[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len) ||
            (outOfs < 0) || ((out.length - outOfs) < len/8)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += inOfs;
        while (inOfs < len) {
            out[outOfs++] = (long) BE.LONG_ARRAY.get(in, inOfs);
            inOfs += 8;
        }
    }

    // Special optimization of b2lBig(in, inOfs, out, 0, 128)
    static void b2lBig128(byte[] in, int inOfs, long[] out) {
        if ((inOfs < 0) || ((in.length - inOfs) < 128) ||
            (out.length < 16)) {
            throw new ArrayIndexOutOfBoundsException();
        }

        out[ 0] = (long) BE.LONG_ARRAY.get(in, inOfs      );
        out[ 1] = (long) BE.LONG_ARRAY.get(in, inOfs +   8);
        out[ 2] = (long) BE.LONG_ARRAY.get(in, inOfs +  16);
        out[ 3] = (long) BE.LONG_ARRAY.get(in, inOfs +  24);
        out[ 4] = (long) BE.LONG_ARRAY.get(in, inOfs +  32);
        out[ 5] = (long) BE.LONG_ARRAY.get(in, inOfs +  40);
        out[ 6] = (long) BE.LONG_ARRAY.get(in, inOfs +  48);
        out[ 7] = (long) BE.LONG_ARRAY.get(in, inOfs +  56);
        out[ 8] = (long) BE.LONG_ARRAY.get(in, inOfs +  64);
        out[ 9] = (long) BE.LONG_ARRAY.get(in, inOfs +  72);
        out[10] = (long) BE.LONG_ARRAY.get(in, inOfs +  80);
        out[11] = (long) BE.LONG_ARRAY.get(in, inOfs +  88);
        out[12] = (long) BE.LONG_ARRAY.get(in, inOfs +  96);
        out[13] = (long) BE.LONG_ARRAY.get(in, inOfs + 104);
        out[14] = (long) BE.LONG_ARRAY.get(in, inOfs + 112);
        out[15] = (long) BE.LONG_ARRAY.get(in, inOfs + 120);
    }

    /**
     * long[] to byte[] conversion, big endian byte order.
     */
    static void l2bBig(long[] in, int inOfs, byte[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len/8) ||
            (outOfs < 0) || ((out.length - outOfs) < len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += outOfs;
        while (outOfs < len) {
            BE.LONG_ARRAY.set(out, outOfs, in[inOfs++]);
            outOfs += 8;
        }
    }

    /**
     * byte[] to long[] conversion, little endian byte order
     */
    static void b2lLittle(byte[] in, int inOfs, long[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len) ||
            ((outOfs < 0) || (out.length - outOfs) < len/8)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += outOfs;
        while (outOfs < len) {
            out[outOfs++] = (long) LE.LONG_ARRAY.get(in, inOfs);
            inOfs += 8;
        }
    }


    /**
     * long[] to byte[] conversion, little endian byte order
     */
    static void l2bLittle(long[] in, int inOfs, byte[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len/8) ||
            (outOfs < 0) || ((out.length - outOfs) < len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        len += outOfs;
        while (outOfs < len) {
            LE.LONG_ARRAY.set(out, outOfs, in[inOfs++]);
            outOfs += 8;
        }
    }
}
