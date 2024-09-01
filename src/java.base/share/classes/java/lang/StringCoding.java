/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package java.lang;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.IntrinsicCandidate;

import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

/**
 * Utility class for string encoding and decoding.
 */
class StringCoding {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long POSITIVE_MASK = 0b1000000_1000000_1000000_1000000_1000000_1000000_1000000_1000000L;

    private StringCoding() { }

    public static int countGreaterThanZero(String s) {
        byte[] value;
        return countGreaterThanZero(value = s.value(), 0, value.length);
    }

    /**
     * Count the number of leading greater than zero bytes in the range.
     */
    public static int countGreaterThanZero(byte[] ba, int off, int len) {
        int limit = off + len;
        int i = off;
        for (; i < limit; i += 8) {
            long v = UNSAFE.getLong(ba, i + ARRAY_BYTE_BASE_OFFSET);
            if ((v & POSITIVE_MASK) != 0 || (v & ~POSITIVE_MASK) != 0) {
                break;
            }
        }

        for (; i < limit; i++) {
            if (ba[i] <= 0) {
                return i - off;
            }
        }
        return len;
    }

    public static boolean hasNegatives(byte[] ba, int off, int len) {
        return countPositives(ba, off, len) != len;
    }

    /**
     * Count the number of leading positive bytes in the range.
     *
     * @implSpec the implementation must return len if there are no negative
     *   bytes in the range. If there are negative bytes, the implementation must return
     *   a value that is less than or equal to the index of the first negative byte
     *   in the range.
     */
    @IntrinsicCandidate
    public static int countPositives(byte[] ba, int off, int len) {
        int limit = off + len;
        for (int i = off; i < limit; i++) {
            if (ba[i] < 0) {
                return i - off;
            }
        }
        return len;
    }

    @IntrinsicCandidate
    public static int implEncodeISOArray(byte[] sa, int sp,
                                         byte[] da, int dp, int len) {
        int i = 0;
        for (; i < len; i++) {
            char c = StringUTF16.getChar(sa, sp++);
            if (c > '\u00FF')
                break;
            da[dp++] = (byte)c;
        }
        return i;
    }

    @IntrinsicCandidate
    public static int implEncodeAsciiArray(char[] sa, int sp,
                                           byte[] da, int dp, int len)
    {
        int i = 0;
        for (; i < len; i++) {
            char c = sa[sp++];
            if (c >= '\u0080')
                break;
            da[dp++] = (byte)c;
        }
        return i;
    }

}
