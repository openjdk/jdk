/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.util.Preconditions;
import jdk.internal.vm.annotation.IntrinsicCandidate;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Utility class for string encoding and decoding.
 */
class StringCoding {

    private StringCoding() { }

    /**
     * Count the number of leading non-zero ascii chars in the range.
     */
    static int countNonZeroAscii(String s) {
        byte[] value = s.value();
        if (s.isLatin1()) {
            return countNonZeroAsciiLatin1(value, 0, value.length);
        } else {
            return countNonZeroAsciiUTF16(value, 0, s.length());
        }
    }

    /**
     * Count the number of non-zero ascii chars in the range.
     */
    private static int countNonZeroAsciiLatin1(byte[] ba, int off, int len) {
        int limit = off + len;
        for (int i = off; i < limit; i++) {
            if (ba[i] <= 0) {
                return i - off;
            }
        }
        return len;
    }

    /**
     * Count the number of leading non-zero ascii chars in the range.
     */
    private static int countNonZeroAsciiUTF16(byte[] ba, int off, int strlen) {
        int limit = off + strlen;
        for (int i = off; i < limit; i++) {
            char c = StringUTF16.charAt(ba, i);
            if (c == 0 || c > 0x7F) {
                return i - off;
            }
        }
        return strlen;
    }

    static boolean hasNegatives(byte[] ba, int off, int len) {
        return countPositives(ba, off, len) != len;
    }

    /**
     * Count the number of leading positive bytes in the range.
     *
     * @implSpec the implementation must return len if there are no negative
     *   bytes in the range. If there are negative bytes, the implementation must return
     *   a value that is less than or equal to the index of the first negative byte
     *   in the range.
     *
     * @param ba a byte array
     * @param off the index of the first byte to start reading from
     * @param len the total number of bytes to read
     * @throws NullPointerException if {@code ba} is null
     * @throws ArrayIndexOutOfBoundsException if the provided sub-range is
     *         {@linkplain Preconditions#checkFromIndexSize(int, int, int, BiFunction) out of bounds}
     */
    static int countPositives(byte[] ba, int off, int len) {
        Preconditions.checkFromIndexSize(off, len, Objects.requireNonNull(ba, "ba").length, Preconditions.AIOOBE_FORMATTER);
        return countPositives0(ba, off, len);
    }

    @IntrinsicCandidate
    private static int countPositives0(byte[] ba, int off, int len) {
        int limit = off + len;
        for (int i = off; i < limit; i++) {
            if (ba[i] < 0) {
                return i - off;
            }
        }
        return len;
    }

    /**
     * Encodes as many ISO-8859-1 codepoints as possible from the source byte
     * array containing characters encoded in UTF-16, into the destination byte
     * array, assuming that the encoding is ISO-8859-1 compatible.
     *
     * @apiNote
     *
     * {@code sa} denotes the {@code byte[]} backing a {@link String}. When
     * {@linkplain String#COMPACT_STRINGS compact strings} are disabled, a
     * {@code char} is always represented by 2 bytes, i.e.,
     * encoded in UTF-16. When enabled, if the content is ISO-8859-1, a
     * {@code char} is represented by 1 byte; otherwise again by 2 bytes.
     * <p>
     * This method assumes that {@code sa} is encoded in UTF-16, and hence,
     * each {@code char} maps to 2 bytes.
     * </p><p>
     * {@code sp} is encoded in ISO-8859-1. There each {@code byte} corresponds
     * to a {@code char}.
     * </p>
     *
     * @param sa the source byte array containing characters encoded in UTF-16
     * @param sp the index of the <em>byte (not character!)</em> from the source array to start reading from
     * @param da the target byte array
     * @param dp the index of the target array to start writing to
     * @param len the total number of <em>characters (not bytes!)</em> to be encoded
     * @return the total number of <em>characters (not bytes!)</em> successfully encoded
     * @throws NullPointerException if any of the provided arrays is null
     * @throws ArrayIndexOutOfBoundsException if any of the provided sub-ranges are
     *         {@linkplain Preconditions#checkFromIndexSize(int, int, int, BiFunction) out of bounds}
     */
    static int encodeISOArray(byte[] sa, int sp,
                              byte[] da, int dp, int len) {
        Objects.requireNonNull(sa, "sa");
        Objects.requireNonNull(da, "da");
        Preconditions.checkFromIndexSize(sp, len << 1, sa.length, Preconditions.AIOOBE_FORMATTER);
        // Not checking the `dp + len < da.length` invariant, since "as many
        // codepoints as possible" contract still holds with a `da` of
        // insufficient capacity, and the compiler intrinsic matches this
        // behavior too.
        Preconditions.checkIndex(dp, da.length, Preconditions.AIOOBE_FORMATTER);
        return encodeISOArray0(sa, sp, da, dp, len);
    }

    @IntrinsicCandidate
    private static int encodeISOArray0(byte[] sa, int sp,
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

    /**
     * Encodes as many ASCII codepoints as possible from the source
     * character array into the destination byte array, assuming that
     * the encoding is ASCII compatible.
     *
     * @param sa the source character array
     * @param sp the index of the source array to start reading from
     * @param da the target byte array
     * @param dp the index of the target array to start writing to
     * @param len the total number of characters to be encoded
     * @return the total number of characters successfully encoded
     * @throws NullPointerException if any of the provided arrays is null
     * @throws ArrayIndexOutOfBoundsException if any of the provided sub-ranges are
     *         {@linkplain Preconditions#checkFromIndexSize(int, int, int, BiFunction) out of bounds}
     */
    static int encodeAsciiArray(char[] sa, int sp,
                                byte[] da, int dp, int len) {
        Objects.requireNonNull(sa, "sa");
        Objects.requireNonNull(da, "da");
        Preconditions.checkFromIndexSize(sp, len, sa.length, Preconditions.AIOOBE_FORMATTER);
        // Not checking the `dp + len < da.length` invariant, since "as many
        // codepoints as possible" contract still holds with a `da` of
        // insufficient capacity, and the compiler intrinsic matches this
        // behavior too.
        Preconditions.checkIndex(dp, da.length, Preconditions.AIOOBE_FORMATTER);
        return encodeAsciiArray0(sa, sp, da, dp, len);
    }

    @IntrinsicCandidate
    static int encodeAsciiArray0(char[] sa, int sp,
                                 byte[] da, int dp, int len) {
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
