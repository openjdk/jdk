/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;

import sun.misc.Unsafe;

/**
 * Optimized methods for converting between byte[] and int[]/long[], both for
 * big endian and little endian byte orders.
 *
 * Currently, it includes a default code path plus two optimized code paths.
 * One is for little endian architectures that support full speed int/long
 * access at unaligned addresses (i.e. x86/amd64). The second is for big endian
 * architectures (that only support correctly aligned access), such as SPARC.
 * These are the only platforms we currently support, but other optimized
 * variants could be added as needed.
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

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    // whether to use the optimized path for little endian platforms that
    // support full speed unaligned memory access.
    private static final boolean littleEndianUnaligned;

    // whether to use the optimzied path for big endian platforms that
    // support only correctly aligned full speed memory access.
    // (Note that on SPARC unaligned memory access is possible, but it is
    // implemented using a software trap and therefore very slow)
    private static final boolean bigEndian;

    private static final int byteArrayOfs = unsafe.arrayBaseOffset(byte[].class);

    static {
        boolean scaleOK = ((unsafe.arrayIndexScale(byte[].class) == 1)
            && (unsafe.arrayIndexScale(int[].class) == 4)
            && (unsafe.arrayIndexScale(long[].class) == 8)
            && ((byteArrayOfs & 3) == 0));

        ByteOrder byteOrder = ByteOrder.nativeOrder();
        littleEndianUnaligned =
            scaleOK && unaligned() && (byteOrder == ByteOrder.LITTLE_ENDIAN);
        bigEndian =
            scaleOK && (byteOrder == ByteOrder.BIG_ENDIAN);
    }

    // Return whether this platform supports full speed int/long memory access
    // at unaligned addresses.
    private static boolean unaligned() {
        return unsafe.unalignedAccess();
    }

    /**
     * byte[] to int[] conversion, little endian byte order.
     */
    static void b2iLittle(byte[] in, int inOfs, int[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len) ||
            (outOfs < 0) || ((out.length - outOfs) < len/4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            inOfs += byteArrayOfs;
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] = unsafe.getInt(in, (long)inOfs);
                inOfs += 4;
            }
        } else if (bigEndian && ((inOfs & 3) == 0)) {
            inOfs += byteArrayOfs;
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] = reverseBytes(unsafe.getInt(in, (long)inOfs));
                inOfs += 4;
            }
        } else {
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] = ((in[inOfs    ] & 0xff)      )
                              | ((in[inOfs + 1] & 0xff) <<  8)
                              | ((in[inOfs + 2] & 0xff) << 16)
                              | ((in[inOfs + 3]       ) << 24);
                inOfs += 4;
            }
        }
    }

    // Special optimization of b2iLittle(in, inOfs, out, 0, 64)
    static void b2iLittle64(byte[] in, int inOfs, int[] out) {
        if ((inOfs < 0) || ((in.length - inOfs) < 64) ||
            (out.length < 16)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            inOfs += byteArrayOfs;
            out[ 0] = unsafe.getInt(in, (long)(inOfs     ));
            out[ 1] = unsafe.getInt(in, (long)(inOfs +  4));
            out[ 2] = unsafe.getInt(in, (long)(inOfs +  8));
            out[ 3] = unsafe.getInt(in, (long)(inOfs + 12));
            out[ 4] = unsafe.getInt(in, (long)(inOfs + 16));
            out[ 5] = unsafe.getInt(in, (long)(inOfs + 20));
            out[ 6] = unsafe.getInt(in, (long)(inOfs + 24));
            out[ 7] = unsafe.getInt(in, (long)(inOfs + 28));
            out[ 8] = unsafe.getInt(in, (long)(inOfs + 32));
            out[ 9] = unsafe.getInt(in, (long)(inOfs + 36));
            out[10] = unsafe.getInt(in, (long)(inOfs + 40));
            out[11] = unsafe.getInt(in, (long)(inOfs + 44));
            out[12] = unsafe.getInt(in, (long)(inOfs + 48));
            out[13] = unsafe.getInt(in, (long)(inOfs + 52));
            out[14] = unsafe.getInt(in, (long)(inOfs + 56));
            out[15] = unsafe.getInt(in, (long)(inOfs + 60));
        } else if (bigEndian && ((inOfs & 3) == 0)) {
            inOfs += byteArrayOfs;
            out[ 0] = reverseBytes(unsafe.getInt(in, (long)(inOfs     )));
            out[ 1] = reverseBytes(unsafe.getInt(in, (long)(inOfs +  4)));
            out[ 2] = reverseBytes(unsafe.getInt(in, (long)(inOfs +  8)));
            out[ 3] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 12)));
            out[ 4] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 16)));
            out[ 5] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 20)));
            out[ 6] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 24)));
            out[ 7] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 28)));
            out[ 8] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 32)));
            out[ 9] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 36)));
            out[10] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 40)));
            out[11] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 44)));
            out[12] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 48)));
            out[13] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 52)));
            out[14] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 56)));
            out[15] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 60)));
        } else {
            b2iLittle(in, inOfs, out, 0, 64);
        }
    }

    /**
     * int[] to byte[] conversion, little endian byte order.
     */
    static void i2bLittle(int[] in, int inOfs, byte[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len/4) ||
            (outOfs < 0) || ((out.length - outOfs) < len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            outOfs += byteArrayOfs;
            len += outOfs;
            while (outOfs < len) {
                unsafe.putInt(out, (long)outOfs, in[inOfs++]);
                outOfs += 4;
            }
        } else if (bigEndian && ((outOfs & 3) == 0)) {
            outOfs += byteArrayOfs;
            len += outOfs;
            while (outOfs < len) {
                unsafe.putInt(out, (long)outOfs, reverseBytes(in[inOfs++]));
                outOfs += 4;
            }
        } else {
            len += outOfs;
            while (outOfs < len) {
                int i = in[inOfs++];
                out[outOfs++] = (byte)(i      );
                out[outOfs++] = (byte)(i >>  8);
                out[outOfs++] = (byte)(i >> 16);
                out[outOfs++] = (byte)(i >> 24);
            }
        }
    }

    // Store one 32-bit value into out[outOfs..outOfs+3] in little endian order.
    static void i2bLittle4(int val, byte[] out, int outOfs) {
        if ((outOfs < 0) || ((out.length - outOfs) < 4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            unsafe.putInt(out, (long)(byteArrayOfs + outOfs), val);
        } else if (bigEndian && ((outOfs & 3) == 0)) {
            unsafe.putInt(out, (long)(byteArrayOfs + outOfs), reverseBytes(val));
        } else {
            out[outOfs    ] = (byte)(val      );
            out[outOfs + 1] = (byte)(val >>  8);
            out[outOfs + 2] = (byte)(val >> 16);
            out[outOfs + 3] = (byte)(val >> 24);
        }
    }

    /**
     * byte[] to int[] conversion, big endian byte order.
     */
    static void b2iBig(byte[] in, int inOfs, int[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len) ||
            (outOfs < 0) || ((out.length - outOfs) < len/4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            inOfs += byteArrayOfs;
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] = reverseBytes(unsafe.getInt(in, (long)inOfs));
                inOfs += 4;
            }
        } else if (bigEndian && ((inOfs & 3) == 0)) {
            inOfs += byteArrayOfs;
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] = unsafe.getInt(in, (long)inOfs);
                inOfs += 4;
            }
        } else {
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] = ((in[inOfs + 3] & 0xff)      )
                              | ((in[inOfs + 2] & 0xff) <<  8)
                              | ((in[inOfs + 1] & 0xff) << 16)
                              | ((in[inOfs    ]       ) << 24);
                inOfs += 4;
            }
        }
    }

    // Special optimization of b2iBig(in, inOfs, out, 0, 64)
    static void b2iBig64(byte[] in, int inOfs, int[] out) {
        if ((inOfs < 0) || ((in.length - inOfs) < 64) ||
            (out.length < 16)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            inOfs += byteArrayOfs;
            out[ 0] = reverseBytes(unsafe.getInt(in, (long)(inOfs     )));
            out[ 1] = reverseBytes(unsafe.getInt(in, (long)(inOfs +  4)));
            out[ 2] = reverseBytes(unsafe.getInt(in, (long)(inOfs +  8)));
            out[ 3] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 12)));
            out[ 4] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 16)));
            out[ 5] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 20)));
            out[ 6] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 24)));
            out[ 7] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 28)));
            out[ 8] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 32)));
            out[ 9] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 36)));
            out[10] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 40)));
            out[11] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 44)));
            out[12] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 48)));
            out[13] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 52)));
            out[14] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 56)));
            out[15] = reverseBytes(unsafe.getInt(in, (long)(inOfs + 60)));
        } else if (bigEndian && ((inOfs & 3) == 0)) {
            inOfs += byteArrayOfs;
            out[ 0] = unsafe.getInt(in, (long)(inOfs     ));
            out[ 1] = unsafe.getInt(in, (long)(inOfs +  4));
            out[ 2] = unsafe.getInt(in, (long)(inOfs +  8));
            out[ 3] = unsafe.getInt(in, (long)(inOfs + 12));
            out[ 4] = unsafe.getInt(in, (long)(inOfs + 16));
            out[ 5] = unsafe.getInt(in, (long)(inOfs + 20));
            out[ 6] = unsafe.getInt(in, (long)(inOfs + 24));
            out[ 7] = unsafe.getInt(in, (long)(inOfs + 28));
            out[ 8] = unsafe.getInt(in, (long)(inOfs + 32));
            out[ 9] = unsafe.getInt(in, (long)(inOfs + 36));
            out[10] = unsafe.getInt(in, (long)(inOfs + 40));
            out[11] = unsafe.getInt(in, (long)(inOfs + 44));
            out[12] = unsafe.getInt(in, (long)(inOfs + 48));
            out[13] = unsafe.getInt(in, (long)(inOfs + 52));
            out[14] = unsafe.getInt(in, (long)(inOfs + 56));
            out[15] = unsafe.getInt(in, (long)(inOfs + 60));
        } else {
            b2iBig(in, inOfs, out, 0, 64);
        }
    }

    /**
     * int[] to byte[] conversion, big endian byte order.
     */
    static void i2bBig(int[] in, int inOfs, byte[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len/4) ||
            (outOfs < 0) || ((out.length - outOfs) < len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            outOfs += byteArrayOfs;
            len += outOfs;
            while (outOfs < len) {
                unsafe.putInt(out, (long)outOfs, reverseBytes(in[inOfs++]));
                outOfs += 4;
            }
        } else if (bigEndian && ((outOfs & 3) == 0)) {
            outOfs += byteArrayOfs;
            len += outOfs;
            while (outOfs < len) {
                unsafe.putInt(out, (long)outOfs, in[inOfs++]);
                outOfs += 4;
            }
        } else {
            len += outOfs;
            while (outOfs < len) {
                int i = in[inOfs++];
                out[outOfs++] = (byte)(i >> 24);
                out[outOfs++] = (byte)(i >> 16);
                out[outOfs++] = (byte)(i >>  8);
                out[outOfs++] = (byte)(i      );
            }
        }
    }

    // Store one 32-bit value into out[outOfs..outOfs+3] in big endian order.
    static void i2bBig4(int val, byte[] out, int outOfs) {
        if ((outOfs < 0) || ((out.length - outOfs) < 4)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            unsafe.putInt(out, (long)(byteArrayOfs + outOfs), reverseBytes(val));
        } else if (bigEndian && ((outOfs & 3) == 0)) {
            unsafe.putInt(out, (long)(byteArrayOfs + outOfs), val);
        } else {
            out[outOfs    ] = (byte)(val >> 24);
            out[outOfs + 1] = (byte)(val >> 16);
            out[outOfs + 2] = (byte)(val >>  8);
            out[outOfs + 3] = (byte)(val      );
        }
    }

    /**
     * byte[] to long[] conversion, big endian byte order.
     */
    static void b2lBig(byte[] in, int inOfs, long[] out, int outOfs, int len) {
        if ((inOfs < 0) || ((in.length - inOfs) < len) ||
            (outOfs < 0) || ((out.length - outOfs) < len/8)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            inOfs += byteArrayOfs;
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] = reverseBytes(unsafe.getLong(in, (long)inOfs));
                inOfs += 8;
            }
        } else if (bigEndian && ((inOfs & 3) == 0)) {
            // In the current HotSpot memory layout, the first element of a
            // byte[] is only 32-bit aligned, not 64-bit.
            // That means we could use getLong() only for offset 4, 12, etc.,
            // which would rarely occur in practice. Instead, we use an
            // optimization that uses getInt() so that it works for offset 0.
            inOfs += byteArrayOfs;
            len += inOfs;
            while (inOfs < len) {
                out[outOfs++] =
                      ((long)unsafe.getInt(in, (long)inOfs) << 32)
                          | (unsafe.getInt(in, (long)(inOfs + 4)) & 0xffffffffL);
                inOfs += 8;
            }
        } else {
            len += inOfs;
            while (inOfs < len) {
                int i1 = ((in[inOfs + 3] & 0xff)      )
                       | ((in[inOfs + 2] & 0xff) <<  8)
                       | ((in[inOfs + 1] & 0xff) << 16)
                       | ((in[inOfs    ]       ) << 24);
                inOfs += 4;
                int i2 = ((in[inOfs + 3] & 0xff)      )
                       | ((in[inOfs + 2] & 0xff) <<  8)
                       | ((in[inOfs + 1] & 0xff) << 16)
                       | ((in[inOfs    ]       ) << 24);
                out[outOfs++] = ((long)i1 << 32) | (i2 & 0xffffffffL);
                inOfs += 4;
            }
        }
    }

    // Special optimization of b2lBig(in, inOfs, out, 0, 128)
    static void b2lBig128(byte[] in, int inOfs, long[] out) {
        if ((inOfs < 0) || ((in.length - inOfs) < 128) ||
            (out.length < 16)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (littleEndianUnaligned) {
            inOfs += byteArrayOfs;
            out[ 0] = reverseBytes(unsafe.getLong(in, (long)(inOfs      )));
            out[ 1] = reverseBytes(unsafe.getLong(in, (long)(inOfs +   8)));
            out[ 2] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  16)));
            out[ 3] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  24)));
            out[ 4] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  32)));
            out[ 5] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  40)));
            out[ 6] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  48)));
            out[ 7] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  56)));
            out[ 8] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  64)));
            out[ 9] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  72)));
            out[10] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  80)));
            out[11] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  88)));
            out[12] = reverseBytes(unsafe.getLong(in, (long)(inOfs +  96)));
            out[13] = reverseBytes(unsafe.getLong(in, (long)(inOfs + 104)));
            out[14] = reverseBytes(unsafe.getLong(in, (long)(inOfs + 112)));
            out[15] = reverseBytes(unsafe.getLong(in, (long)(inOfs + 120)));
        } else {
            // no optimization for big endian, see comments in b2lBig
            b2lBig(in, inOfs, out, 0, 128);
        }
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
            long i = in[inOfs++];
            out[outOfs++] = (byte)(i >> 56);
            out[outOfs++] = (byte)(i >> 48);
            out[outOfs++] = (byte)(i >> 40);
            out[outOfs++] = (byte)(i >> 32);
            out[outOfs++] = (byte)(i >> 24);
            out[outOfs++] = (byte)(i >> 16);
            out[outOfs++] = (byte)(i >>  8);
            out[outOfs++] = (byte)(i      );
        }
    }
}
