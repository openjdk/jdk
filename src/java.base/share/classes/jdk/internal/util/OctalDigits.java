/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.Stable;

/**
 * Digits class for octal digits.
 *
 * @since 21
 */
public final class OctalDigits {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    @Stable
    private static final short[] DIGITS;

    static {
        short[] digits = new short[8 * 8];

        for (int i = 0; i < 8; i++) {
            short lo = (short) (i + '0');

            for (int j = 0; j < 8; j++) {
                short hi = (short) ((j + '0') << 8);
                digits[(i << 3) + j] = (short) (hi | lo);
            }
        }

        DIGITS = digits;
    }

    /**
     * Constructor.
     */
    private OctalDigits() {
    }

    /**
     * Insert digits for long value in buffer from high index to low index.
     *
     * @param value      value to convert
     * @param index      insert point + 1
     * @param buffer     byte buffer to copy into
     *
     * @return the last index used
     */
    public static int getCharsLatin1(long value, int index, byte[] buffer){
        while ((value & ~0x3F) != 0) {
            int digits = DIGITS[((int) value) & 0x3F];
            value >>>= 6;
            buffer[--index] = (byte) (digits >> 8);
            buffer[--index] = (byte) (digits & 0xFF);
        }

        int digits = DIGITS[(int) (value & 0x3F)];
        buffer[--index] = (byte) (digits >> 8);

        if (7 < value) {
            buffer[--index] = (byte) (digits & 0xFF);
        }

        return index;
    }


    /**
     * This is a variant of {@link OctalDigits#getCharsLatin1(long, int, byte[])}, but for
     * UTF-16 coder.
     *
     * @param value      value to convert
     * @param index      insert point + 1
     * @param buffer     byte buffer to copy into
     *
     * @return the last index used
     */
    public static int getCharsUTF16(long value, int index, byte[] buffer){
        while ((value & ~0x3F) != 0) {
            int pair = (int) DIGITS[((int) value) & 0x3F];
            JLA.putCharUTF16(buffer, --index, pair >> 8);
            JLA.putCharUTF16(buffer, --index, pair & 0xFF);
            value >>>= 6;
        }

        int digits = DIGITS[(int) (value & 0x3F)];
        JLA.putCharUTF16(buffer, --index, digits >> 8);

        if (7 < value) {
            JLA.putCharUTF16(buffer, --index, digits & 0xFF);
        }

        return index;
    }

    /**
     * Calculate the number of digits required to represent the long.
     *
     * @param value value to convert
     *
     * @return number of digits
     */
    public static int stringSize(long value) {
        return value == 0 ? 1 : ((66 - Long.numberOfLeadingZeros(value)) / 3);
    }
}
