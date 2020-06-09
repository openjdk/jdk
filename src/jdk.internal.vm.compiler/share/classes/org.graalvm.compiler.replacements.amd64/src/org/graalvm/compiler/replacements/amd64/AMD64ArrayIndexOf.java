/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.amd64;

import org.graalvm.compiler.core.common.spi.ForeignCallSignature;

public class AMD64ArrayIndexOf {

    public static final ForeignCallSignature STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES = new ForeignCallSignature(
                    "indexOfTwoConsecutiveBytes", int.class, byte[].class, int.class, int.class, int.class);
    public static final ForeignCallSignature STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS = new ForeignCallSignature(
                    "indexOfTwoConsecutiveChars", int.class, char[].class, int.class, int.class, int.class);
    public static final ForeignCallSignature STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS_COMPACT = new ForeignCallSignature(
                    "indexOfTwoConsecutiveCharsCompact", int.class, byte[].class, int.class, int.class, int.class);
    public static final ForeignCallSignature STUB_INDEX_OF_1_BYTE = new ForeignCallSignature(
                    "indexOf1Byte", int.class, byte[].class, int.class, int.class, byte.class);
    public static final ForeignCallSignature STUB_INDEX_OF_2_BYTES = new ForeignCallSignature(
                    "indexOf2Bytes", int.class, byte[].class, int.class, int.class, byte.class, byte.class);
    public static final ForeignCallSignature STUB_INDEX_OF_3_BYTES = new ForeignCallSignature(
                    "indexOf3Bytes", int.class, byte[].class, int.class, int.class, byte.class, byte.class, byte.class);
    public static final ForeignCallSignature STUB_INDEX_OF_4_BYTES = new ForeignCallSignature(
                    "indexOf4Bytes", int.class, byte[].class, int.class, int.class, byte.class, byte.class, byte.class, byte.class);
    public static final ForeignCallSignature STUB_INDEX_OF_1_CHAR = new ForeignCallSignature(
                    "indexOf1Char", int.class, char[].class, int.class, int.class, char.class);
    public static final ForeignCallSignature STUB_INDEX_OF_2_CHARS = new ForeignCallSignature(
                    "indexOf2Chars", int.class, char[].class, int.class, int.class, char.class, char.class);
    public static final ForeignCallSignature STUB_INDEX_OF_3_CHARS = new ForeignCallSignature(
                    "indexOf3Chars", int.class, char[].class, int.class, int.class, char.class, char.class, char.class);
    public static final ForeignCallSignature STUB_INDEX_OF_4_CHARS = new ForeignCallSignature(
                    "indexOf4Chars", int.class, char[].class, int.class, int.class, char.class, char.class, char.class, char.class);
    public static final ForeignCallSignature STUB_INDEX_OF_1_CHAR_COMPACT = new ForeignCallSignature(
                    "indexOf1CharCompact", int.class, byte[].class, int.class, int.class, char.class);
    public static final ForeignCallSignature STUB_INDEX_OF_2_CHARS_COMPACT = new ForeignCallSignature(
                    "indexOf2CharsCompact", int.class, byte[].class, int.class, int.class, char.class, char.class);
    public static final ForeignCallSignature STUB_INDEX_OF_3_CHARS_COMPACT = new ForeignCallSignature(
                    "indexOf3CharsCompact", int.class, byte[].class, int.class, int.class, char.class, char.class, char.class);
    public static final ForeignCallSignature STUB_INDEX_OF_4_CHARS_COMPACT = new ForeignCallSignature(
                    "indexOf4CharsCompact", int.class, byte[].class, int.class, int.class, char.class, char.class, char.class, char.class);

    public static int indexOfTwoConsecutiveBytes(byte[] array, int arrayLength, int fromIndex, byte b1, byte b2) {
        int searchValue = (Byte.toUnsignedInt(b2) << Byte.SIZE) | Byte.toUnsignedInt(b1);
        return AMD64ArrayIndexOfDispatchNode.indexOf2ConsecutiveBytes(STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES, array, arrayLength, fromIndex, searchValue);
    }

    public static int indexOfTwoConsecutiveChars(char[] array, int arrayLength, int fromIndex, char c1, char c2) {
        int searchValue = (c2 << Character.SIZE) | c1;
        return AMD64ArrayIndexOfDispatchNode.indexOf2ConsecutiveChars(STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS, array, arrayLength, fromIndex, searchValue);
    }

    public static int indexOfTwoConsecutiveChars(byte[] array, int arrayLength, int fromIndex, char c1, char c2) {
        int searchValue = (c2 << Character.SIZE) | c1;
        return AMD64ArrayIndexOfDispatchNode.indexOf2ConsecutiveChars(STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS_COMPACT, array, arrayLength, fromIndex, searchValue);
    }

    public static int indexOf1Byte(byte[] array, int arrayLength, int fromIndex, byte b) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_1_BYTE, array, arrayLength, fromIndex, b);
    }

    public static int indexOf2Bytes(byte[] array, int arrayLength, int fromIndex, byte b1, byte b2) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_2_BYTES, array, arrayLength, fromIndex, b1, b2);
    }

    public static int indexOf3Bytes(byte[] array, int arrayLength, int fromIndex, byte b1, byte b2, byte b3) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_3_BYTES, array, arrayLength, fromIndex, b1, b2, b3);
    }

    public static int indexOf4Bytes(byte[] array, int arrayLength, int fromIndex, byte b1, byte b2, byte b3, byte b4) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_4_BYTES, array, arrayLength, fromIndex, b1, b2, b3, b4);
    }

    public static int indexOf1Char(char[] array, int arrayLength, int fromIndex, char c) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_1_CHAR, array, arrayLength, fromIndex, c);
    }

    public static int indexOf2Chars(char[] array, int arrayLength, int fromIndex, char c1, char c2) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_2_CHARS, array, arrayLength, fromIndex, c1, c2);
    }

    public static int indexOf3Chars(char[] array, int arrayLength, int fromIndex, char c1, char c2, char c3) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_3_CHARS, array, arrayLength, fromIndex, c1, c2, c3);
    }

    public static int indexOf4Chars(char[] array, int arrayLength, int fromIndex, char c1, char c2, char c3, char c4) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_4_CHARS, array, arrayLength, fromIndex, c1, c2, c3, c4);
    }

    public static int indexOf1Char(byte[] array, int arrayLength, int fromIndex, char c) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_1_CHAR_COMPACT, array, arrayLength, fromIndex, c);
    }

    public static int indexOf2Chars(byte[] array, int arrayLength, int fromIndex, char c1, char c2) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_2_CHARS_COMPACT, array, arrayLength, fromIndex, c1, c2);
    }

    public static int indexOf3Chars(byte[] array, int arrayLength, int fromIndex, char c1, char c2, char c3) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_3_CHARS_COMPACT, array, arrayLength, fromIndex, c1, c2, c3);
    }

    public static int indexOf4Chars(byte[] array, int arrayLength, int fromIndex, char c1, char c2, char c3, char c4) {
        return AMD64ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_4_CHARS_COMPACT, array, arrayLength, fromIndex, c1, c2, c3, c4);
    }
}
