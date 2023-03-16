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

import jdk.internal.javac.PreviewFeature;

/**
 * Digits provides a fast methodology for converting integers and longs to
 * ASCII strings.
 *
 * @since 21
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
sealed interface Digits permits Digits.DecimalDigits, Digits.HexDigits, Digits.OctalDigits {
    /**
     * Insert digits for long value in buffer from high index to low index.
     *
     * @param value      value to convert
     * @param buffer     byte buffer to copy into
     * @param index      insert point + 1
     * @param putCharMH  method to put character
     *
     * @return the last index used
     *
     * @throws Throwable if putCharMH fails (unusual).
     */
    int digits(long value, byte[] buffer, int index,
               MethodHandle putCharMH) throws Throwable;

    /**
     * Calculate the number of digits required to represent the long.
     *
     * @param value value to convert
     *
     * @return number of digits
     */
    int size(long value);

    /**
     * Digits class for decimal digits.
     */
    final class DecimalDigits implements Digits {
        private static final short[] DIGITS;

        /**
         * Singleton instance of DecimalDigits.
         */
        static final Digits INSTANCE = new DecimalDigits();

        static {
            short[] digits = new short[10 * 10];

            for (int i = 0; i < 10; i++) {
                short hi = (short) ((i + '0') << 8);

                for (int j = 0; j < 10; j++) {
                    short lo = (short) (j + '0');
                    digits[i * 10 + j] = (short) (hi | lo);
                }
            }

            DIGITS = digits;
        }

        /**
         * Constructor.
         */
        private DecimalDigits() {
        }

        @Override
        public int digits(long value, byte[] buffer, int index,
                          MethodHandle putCharMH) throws Throwable {
            boolean negative = value < 0;
            if (!negative) {
                value = -value;
            }

            long q;
            int r;
            while (value <= Integer.MIN_VALUE) {
                q = value / 100;
                r = (int)((q * 100) - value);
                value = q;
                int digits = DIGITS[r];

                putCharMH.invokeExact(buffer, --index, digits & 0xFF);
                putCharMH.invokeExact(buffer, --index, digits >> 8);
            }

            int iq, ivalue = (int)value;
            while (ivalue <= -100) {
                iq = ivalue / 100;
                r = (iq * 100) - ivalue;
                ivalue = iq;
                int digits = DIGITS[r];
                putCharMH.invokeExact(buffer, --index, digits & 0xFF);
                putCharMH.invokeExact(buffer, --index, digits >> 8);
            }

            if (ivalue < 0) {
                ivalue = -ivalue;
            }

            int digits = DIGITS[ivalue];
            putCharMH.invokeExact(buffer, --index, digits & 0xFF);

            if (9 < ivalue) {
                putCharMH.invokeExact(buffer, --index, digits >> 8);
            }

            if (negative) {
                putCharMH.invokeExact(buffer, --index, (int)'-');
            }

            return index;
        }

        @Override
        public int size(long value) {
            boolean negative = value < 0;
            int sign = negative ? 1 : 0;

            if (!negative) {
                value = -value;
            }

            long precision = -10;
            for (int i = 1; i < 19; i++) {
                if (value > precision)
                    return i + sign;

                precision = 10 * precision;
            }

            return 19 + sign;
        }
    }

    /**
     * Digits class for hexadecimal digits.
     */
    final class HexDigits implements Digits {
        private static final short[] DIGITS;

        /**
         * Singleton instance of HexDigits.
         */
        static final Digits INSTANCE = new HexDigits();

        static {
            short[] digits = new short[16 * 16];

            for (int i = 0; i < 16; i++) {
                short hi = (short) ((i < 10 ? i + '0' : i - 10 + 'a') << 8);

                for (int j = 0; j < 16; j++) {
                    short lo = (short) (j < 10 ? j + '0' : j - 10 + 'a');
                    digits[(i << 4) + j] = (short) (hi | lo);
                }
            }

            DIGITS = digits;
        }

        /**
         * Constructor.
         */
        private HexDigits() {
        }

        @Override
        public int digits(long value, byte[] buffer, int index,
                          MethodHandle putCharMH) throws Throwable {
            while ((value & ~0xFF) != 0) {
                int digits = DIGITS[(int) (value & 0xFF)];
                value >>>= 8;
                putCharMH.invokeExact(buffer, --index, digits & 0xFF);
                putCharMH.invokeExact(buffer, --index, digits >> 8);
            }

            int digits = DIGITS[(int) (value & 0xFF)];
            putCharMH.invokeExact(buffer, --index, digits & 0xFF);

            if (0xF < value) {
                putCharMH.invokeExact(buffer, --index, digits >> 8);
            }

            return index;
        }

        @Override
        public int size(long value) {
            return value == 0 ? 1 :
                    67 - Long.numberOfLeadingZeros(value) >> 2;
        }
    }

    /**
     * Digits class for octal digits.
     */
    final class OctalDigits implements Digits {
        private static final short[] DIGITS;

        /**
         * Singleton instance of HexDigits.
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
}
