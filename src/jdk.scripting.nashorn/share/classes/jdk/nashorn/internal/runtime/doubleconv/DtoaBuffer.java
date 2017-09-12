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
// Copyright 2010 the V8 project authors. All rights reserved.
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
 * A buffer for generating string representations of doubles.
 */
public class DtoaBuffer {

    // The character buffer
    final char[] chars;

    // The number of characters in the buffer
    int length = 0;

    // The position of the decimal point
    int decimalPoint = 0;

    // Is this a negative number?
    boolean isNegative = false;

    /**
     * Maximal length of numbers converted by FastDtoa
     */
    public static final int kFastDtoaMaximalLength = FastDtoa.kFastDtoaMaximalLength;

    /**
     * Create a buffer with the given capacity.
     * @param capacity the capacity of the buffer.
     */
    public DtoaBuffer(final int capacity) {
        chars = new char[capacity];
    }

    /**
     * Append a character to the buffer, increasing its length.
     * @param c character
     */
    void append(final char c) {
        chars[length++] = c;
    }

    /**
     * Clear the buffer contents and set its length to {@code 0}.
     */
    public void reset() {
        length = 0;
        decimalPoint = 0;
    }

    /**
     * Get the raw digits of this buffer as string.
     * @return the raw buffer contents
     */
    public String getRawDigits() {
        return new String(chars, 0, length);
    }

    /**
     * Get the position of the decimal point.
     * @return the decimal point position
     */
    public int getDecimalPoint() {
        return decimalPoint;
    }

    /**
     * Returns the number of characters in the buffer.
     * @return buffer length
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns the formatted buffer content as string, using the specified conversion mode
     * and padding.
     *
     * @param mode conversion mode
     * @param digitsAfterPoint number of digits after point
     * @return formatted string
     */
    public String format(final DtoaMode mode, final int digitsAfterPoint) {
        final StringBuilder buffer = new StringBuilder();
        if (isNegative) {
            buffer.append('-');
        }

        // check for minus sign
        switch (mode) {
            case SHORTEST:
                if (decimalPoint < -5 || decimalPoint > 21) {
                    toExponentialFormat(buffer);
                } else {
                    toFixedFormat(buffer, digitsAfterPoint);
                }
                break;
            case FIXED:
                toFixedFormat(buffer, digitsAfterPoint);
                break;
            case PRECISION:
                if (decimalPoint < -5 || decimalPoint > length) {
                    toExponentialFormat(buffer);
                } else {
                    toFixedFormat(buffer, digitsAfterPoint);
                }
                break;
        }

        return buffer.toString();
    }

    private void toFixedFormat(final StringBuilder buffer, final int digitsAfterPoint) {
        if (decimalPoint <= 0) {
            // < 1,
            buffer.append('0');
            if (length > 0) {
                buffer.append('.');
                final int padding = -decimalPoint;
                for (int i = 0; i < padding; i++) {
                    buffer.append('0');
                }
                buffer.append(chars, 0, length);
            } else {
                decimalPoint = 1;
            }
        } else if (decimalPoint >= length) {
            // large integer, add trailing zeroes
            buffer.append(chars, 0, length);
            for (int i = length; i < decimalPoint; i++) {
                buffer.append('0');
            }
        } else if (decimalPoint < length) {
            // >= 1, split decimals and insert decimalPoint
            buffer.append(chars, 0, decimalPoint);
            buffer.append('.');
            buffer.append(chars, decimalPoint, length - decimalPoint);
        }

        // Create trailing zeros if requested
        if (digitsAfterPoint > 0) {
            if (decimalPoint >= length) {
                buffer.append('.');
            }
            for (int i = Math.max(0, length - decimalPoint); i < digitsAfterPoint; i++) {
                buffer.append('0');
            }
        }
    }

    private void toExponentialFormat(final StringBuilder buffer) {
        buffer.append(chars[0]);
        if (length > 1) {
            // insert decimal decimalPoint if more than one digit was produced
            buffer.append('.');
            buffer.append(chars, 1, length - 1);
        }
        buffer.append('e');
        final int exponent = decimalPoint - 1;
        if (exponent > 0) {
            buffer.append('+');
        }
        buffer.append(exponent);
    }

    @Override
    public String toString() {
        return "[chars:" + new String(chars, 0, length) + ", decimalPoint:" + decimalPoint + "]";
    }
}
