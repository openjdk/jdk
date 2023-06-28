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

package java.util;

import java.lang.invoke.MethodHandle;

import jdk.internal.vm.annotation.Stable;

/**
 * Digits class for octal digits.
 *
 * @since 21
 */
final class OctalDigits implements Digits {
    @Stable
    private static final short[] DIGITS;

    /**
     * Singleton instance of OctalDigits.
     */
    static final Digits INSTANCE = new OctalDigits();

    static {
        short[] digits = new short[8 * 8];

        for (int i = 0; i < 8; i++) {
            short hi = (short) ((i + '0') << 8);

            for (int j = 0; j < 8; j++) {
                short lo = (short) (j + '0');
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

    @Override
    public int digits(long value, byte[] buffer, int index,
                      MethodHandle putCharMH) throws Throwable {
        while ((value & ~0x3F) != 0) {
            int digits = DIGITS[(int) (value & 0x3F)];
            value >>>= 6;
            putCharMH.invokeExact(buffer, --index, digits & 0xFF);
            putCharMH.invokeExact(buffer, --index, digits >> 8);
        }

        int digits = DIGITS[(int) (value & 0x3F)];
        putCharMH.invokeExact(buffer, --index, digits & 0xFF);

        if (7 < value) {
            putCharMH.invokeExact(buffer, --index, digits >> 8);
        }

        return index;
    }

    @Override
    public int size(long value) {
        return (66 - Long.numberOfLeadingZeros(value)) / 3;
    }
}
