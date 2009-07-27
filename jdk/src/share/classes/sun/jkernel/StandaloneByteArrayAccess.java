/*
 * Copyright 2008 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * This is a pure subset of package-private class
 * sun.security.provider.ByteArrayAccess. The subset consists of only the simple
 * shift and boolean operations needed for the one current client of this
 * class (sun.jkernel.StandaloneSHA) and omits optimization code and comments
 * not relevant to the subset.  No semantic changes have been made.
 * A few long lines were broken to conform to JDK coding style.
 * Pete Soper, August, 2007.
 */

package sun.jkernel;

/**
 * Methods for converting between byte[] and int[]/long[].
 *
 * @since   1.6
 * @version 1.1, 05/26/06
 * @author  Andreas Sterbenz
 */
final class StandaloneByteArrayAccess {

    private StandaloneByteArrayAccess() {
        // empty
    }

    /**
     * byte[] to int[] conversion, little endian byte order.
     */
    static void b2iLittle(byte[] in, int inOfs, int[] out, int outOfs,
        int len) {
        len += inOfs;
        while (inOfs < len) {
            out[outOfs++] = ((in[inOfs    ] & 0xff)      )
                          | ((in[inOfs + 1] & 0xff) <<  8)
                          | ((in[inOfs + 2] & 0xff) << 16)
                          | ((in[inOfs + 3]       ) << 24);
            inOfs += 4;
        }
    }

    /**
     * int[] to byte[] conversion, little endian byte order.
     */
    static void i2bLittle(int[] in, int inOfs, byte[] out, int outOfs,
        int len) {
        len += outOfs;
        while (outOfs < len) {
            int i = in[inOfs++];
            out[outOfs++] = (byte)(i      );
            out[outOfs++] = (byte)(i >>  8);
            out[outOfs++] = (byte)(i >> 16);
            out[outOfs++] = (byte)(i >> 24);
        }
    }

    /**
     * byte[] to int[] conversion, big endian byte order.
     */
    static void b2iBig(byte[] in, int inOfs, int[] out, int outOfs, int len) {
        len += inOfs;
        while (inOfs < len) {
            out[outOfs++] = ((in[inOfs + 3] & 0xff)      )
                          | ((in[inOfs + 2] & 0xff) <<  8)
                          | ((in[inOfs + 1] & 0xff) << 16)
                          | ((in[inOfs    ]       ) << 24);
            inOfs += 4;
        }
    }

    /**
     * int[] to byte[] conversion, big endian byte order.
     */
    static void i2bBig(int[] in, int inOfs, byte[] out, int outOfs, int len) {
        len += outOfs;
        while (outOfs < len) {
            int i = in[inOfs++];
            out[outOfs++] = (byte)(i >> 24);
            out[outOfs++] = (byte)(i >> 16);
            out[outOfs++] = (byte)(i >>  8);
            out[outOfs++] = (byte)(i      );
        }
    }

    // Store one 32-bit value into out[outOfs..outOfs+3] in big endian order.
    static void i2bBig4(int val, byte[] out, int outOfs) {
        out[outOfs    ] = (byte)(val >> 24);
        out[outOfs + 1] = (byte)(val >> 16);
        out[outOfs + 2] = (byte)(val >>  8);
        out[outOfs + 3] = (byte)(val      );
    }

    /**
     * byte[] to long[] conversion, big endian byte order.
     */
    static void b2lBig(byte[] in, int inOfs, long[] out, int outOfs, int len) {
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

    /**
     * long[] to byte[] conversion
     */
    static void l2bBig(long[] in, int inOfs, byte[] out, int outOfs, int len) {
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
