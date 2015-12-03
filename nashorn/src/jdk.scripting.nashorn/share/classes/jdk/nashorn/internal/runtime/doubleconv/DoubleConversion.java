/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
// Copyright 2011 the V8 project authors. All rights reserved.
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials provided
//       with the distribution.
//     * Neither the name of Google Inc. nor the names of its
//       contributors may be used to endorse or promote products derived
//       from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package jdk.nashorn.internal.runtime.doubleconv;

/**
 * This class provides the public API for the double conversion package.
 */
public final class DoubleConversion {

    private final static int BUFFER_LENGTH = 30;

    /**
     * Converts a double number to its shortest string representation.
     *
     * @param value number to convert
     * @return formatted number
     */
    public static String toShortestString(final double value) {
        final DtoaBuffer buffer = new DtoaBuffer(FastDtoa.kFastDtoaMaximalLength);
        final double absValue = Math.abs(value);

        if (value < 0) {
            buffer.isNegative = true;
        }

        if (!fastDtoaShortest(absValue, buffer)) {
            buffer.reset();
            bignumDtoa(absValue, DtoaMode.SHORTEST, 0, buffer);
        }

        return buffer.format(DtoaMode.SHORTEST, 0);
    }

    /**
     * Converts a double number to a string representation with a fixed number of digits
     * after the decimal point.
     *
     * @param value number to convert.
     * @param requestedDigits number of digits after decimal point
     * @return formatted number
     */
    public static String toFixed(final double value, final int requestedDigits) {
        final DtoaBuffer buffer = new DtoaBuffer(BUFFER_LENGTH);
        final double absValue = Math.abs(value);

        if (value < 0) {
            buffer.isNegative = true;
        }

        if (value == 0) {
            buffer.append('0');
            buffer.decimalPoint = 1;
        } else if (!fixedDtoa(absValue, requestedDigits, buffer)) {
            buffer.reset();
            bignumDtoa(absValue, DtoaMode.FIXED, requestedDigits, buffer);
        }

        return buffer.format(DtoaMode.FIXED, requestedDigits);
    }

    /**
     * Converts a double number to a string representation with a fixed number of digits.
     *
     * @param value number to convert
     * @param precision number of digits to create
     * @return formatted number
     */
    public static String toPrecision(final double value, final int precision) {
        final DtoaBuffer buffer = new DtoaBuffer(precision);
        final double absValue = Math.abs(value);

        if (value < 0) {
            buffer.isNegative = true;
        }

        if (value == 0) {
            for (int i = 0; i < precision; i++) {
                buffer.append('0');
            }
            buffer.decimalPoint = 1;

        } else if (!fastDtoaCounted(absValue, precision, buffer)) {
            buffer.reset();
            bignumDtoa(absValue, DtoaMode.PRECISION, precision, buffer);
        }

        return buffer.format(DtoaMode.PRECISION, 0);
    }

    /**
     * Converts a double number to a string representation using the
     * {@code BignumDtoa} algorithm and the specified conversion mode
     * and number of digits.
     *
     * @param v number to convert
     * @param mode conversion mode
     * @param digits number of digits
     * @param buffer buffer to use
     */
    public static void bignumDtoa(final double v, final DtoaMode mode, final int digits, final DtoaBuffer buffer) {
        assert(v > 0);
        assert(!Double.isNaN(v));
        assert(!Double.isInfinite(v));

        BignumDtoa.bignumDtoa(v, mode, digits, buffer);
    }

    /**
     * Converts a double number to its shortest string representation
     * using the {@code FastDtoa} algorithm.
     *
     * @param v number to convert
     * @param buffer buffer to use
     * @return true if conversion succeeded
     */
    public static boolean fastDtoaShortest(final double v, final DtoaBuffer buffer) {
        assert(v > 0);
        assert(!Double.isNaN(v));
        assert(!Double.isInfinite(v));

        return FastDtoa.grisu3(v, buffer);
    }

    /**
     * Converts a double number to a string representation with the
     * given number of digits using the {@code FastDtoa} algorithm.
     *
     * @param v number to convert
     * @param precision number of digits to generate
     * @param buffer buffer to use
     * @return true if conversion succeeded
     */
    public static boolean fastDtoaCounted(final double v, final int precision, final DtoaBuffer buffer) {
        assert(v > 0);
        assert(!Double.isNaN(v));
        assert(!Double.isInfinite(v));

        return FastDtoa.grisu3Counted(v, precision, buffer);
    }

    /**
     * Converts a double number to a string representation with a
     * fixed number of digits after the decimal point using the
     * {@code FixedDtoa} algorithm.
     *
     * @param v number to convert.
     * @param digits number of digits after the decimal point
     * @param buffer buffer to use
     * @return true if conversion succeeded
     */
    public static boolean fixedDtoa(final double v, final int digits, final DtoaBuffer buffer) {
        assert(v > 0);
        assert(!Double.isNaN(v));
        assert(!Double.isInfinite(v));

        return FixedDtoa.fastFixedDtoa(v, digits, buffer);
    }

}
