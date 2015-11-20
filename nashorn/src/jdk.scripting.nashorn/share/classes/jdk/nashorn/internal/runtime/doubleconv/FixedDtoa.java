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

class FixedDtoa {

    // Represents a 128bit type. This class should be replaced by a native type on
    // platforms that support 128bit integers.
    static class UInt128 {

        private static final long kMask32 = 0xFFFFFFFFL;
        // Value == (high_bits_ << 64) + low_bits_
        private long high_bits_;
        private long low_bits_;

        UInt128(final long high_bits, final long low_bits) {
            this.high_bits_ = high_bits;
            this.low_bits_ = low_bits;
        }

        void multiply(final int multiplicand) {
            long accumulator;

            accumulator = (low_bits_ & kMask32) * multiplicand;
            long part = accumulator & kMask32;
            accumulator >>>= 32;
            accumulator = accumulator + (low_bits_ >>> 32) * multiplicand;
            low_bits_ = (accumulator << 32) + part;
            accumulator >>>= 32;
            accumulator = accumulator + (high_bits_ & kMask32) * multiplicand;
            part = accumulator & kMask32;
            accumulator >>>= 32;
            accumulator = accumulator + (high_bits_ >>> 32) * multiplicand;
            high_bits_ = (accumulator << 32) + part;
            assert ((accumulator >>> 32) == 0);
        }

        void shift(final int shift_amount) {
            assert (-64 <= shift_amount && shift_amount <= 64);
            if (shift_amount == 0) {
                return;
            } else if (shift_amount == -64) {
                high_bits_ = low_bits_;
                low_bits_ = 0;
            } else if (shift_amount == 64) {
                low_bits_ = high_bits_;
                high_bits_ = 0;
            } else if (shift_amount <= 0) {
                high_bits_ <<= -shift_amount;
                high_bits_ += low_bits_ >>> (64 + shift_amount);
                low_bits_ <<= -shift_amount;
            } else {
                low_bits_ >>>= shift_amount;
                low_bits_ += high_bits_ << (64 - shift_amount);
                high_bits_ >>>= shift_amount;
            }
        }

        // Modifies *this to *this MOD (2^power).
        // Returns *this DIV (2^power).
        int divModPowerOf2(final int power) {
            if (power >= 64) {
                final int result = (int) (high_bits_ >>> (power - 64));
                high_bits_ -= (long) (result) << (power - 64);
                return result;
            } else {
                final long part_low = low_bits_ >>> power;
                final long part_high = high_bits_ << (64 - power);
                final int result = (int) (part_low + part_high);
                high_bits_ = 0;
                low_bits_ -= part_low << power;
                return result;
            }
        }

        boolean isZero() {
            return high_bits_ == 0 && low_bits_ == 0;
        }

        int bitAt(final int position) {
            if (position >= 64) {
                return (int) (high_bits_ >>> (position - 64)) & 1;
            } else {
                return (int) (low_bits_ >>> position) & 1;
            }
        }

    };


    static final  int kDoubleSignificandSize = 53;  // Includes the hidden bit.


    static void fillDigits32FixedLength(int number, final int requested_length,
                                        final DtoaBuffer buffer) {
        for (int i = requested_length - 1; i >= 0; --i) {
            buffer.chars[buffer.length + i] = (char) ('0' + Integer.remainderUnsigned(number, 10));
            number = Integer.divideUnsigned(number, 10);
        }
        buffer.length += requested_length;
    }


    static void fillDigits32(int number, final DtoaBuffer buffer) {
        int number_length = 0;
        // We fill the digits in reverse order and exchange them afterwards.
        while (number != 0) {
            final int digit = Integer.remainderUnsigned(number, 10);
            number = Integer.divideUnsigned(number, 10);
            buffer.chars[buffer.length + number_length] = (char) ('0' + digit);
            number_length++;
        }
        // Exchange the digits.
        int i = buffer.length;
        int j = buffer.length + number_length - 1;
        while (i < j) {
            final char tmp = buffer.chars[i];
            buffer.chars[i] = buffer.chars[j];
            buffer.chars[j] = tmp;
            i++;
            j--;
        }
        buffer.length += number_length;
    }


    static void fillDigits64FixedLength(long number, final DtoaBuffer buffer) {
        final int kTen7 = 10000000;
        // For efficiency cut the number into 3 uint32_t parts, and print those.
        final int part2 = (int) Long.remainderUnsigned(number, kTen7);
        number = Long.divideUnsigned(number, kTen7);
        final int part1 = (int) Long.remainderUnsigned(number, kTen7);
        final int part0 = (int) Long.divideUnsigned(number, kTen7);

        fillDigits32FixedLength(part0, 3, buffer);
        fillDigits32FixedLength(part1, 7, buffer);
        fillDigits32FixedLength(part2, 7, buffer);
    }


    static void FillDigits64(long number, final DtoaBuffer buffer) {
        final int kTen7 = 10000000;
        // For efficiency cut the number into 3 uint32_t parts, and print those.
        final int part2 = (int) Long.remainderUnsigned(number, kTen7);
        number = Long.divideUnsigned(number, kTen7);
        final int part1 = (int) Long.remainderUnsigned(number, kTen7);
        final int part0 = (int) Long.divideUnsigned(number, kTen7);

        if (part0 != 0) {
            fillDigits32(part0, buffer);
            fillDigits32FixedLength(part1, 7, buffer);
            fillDigits32FixedLength(part2, 7, buffer);
        } else if (part1 != 0) {
            fillDigits32(part1, buffer);
            fillDigits32FixedLength(part2, 7, buffer);
        } else {
            fillDigits32(part2, buffer);
        }
    }


    static void roundUp(final DtoaBuffer buffer) {
        // An empty buffer represents 0.
        if (buffer.length == 0) {
            buffer.chars[0] = '1';
            buffer.decimalPoint = 1;
            buffer.length = 1;
            return;
        }
        // Round the last digit until we either have a digit that was not '9' or until
        // we reached the first digit.
        buffer.chars[buffer.length - 1]++;
        for (int i = buffer.length - 1; i > 0; --i) {
            if (buffer.chars[i] != '0' + 10) {
                return;
            }
            buffer.chars[i] = '0';
            buffer.chars[i - 1]++;
        }
        // If the first digit is now '0' + 10, we would need to set it to '0' and add
        // a '1' in front. However we reach the first digit only if all following
        // digits had been '9' before rounding up. Now all trailing digits are '0' and
        // we simply switch the first digit to '1' and update the decimal-point
        // (indicating that the point is now one digit to the right).
        if (buffer.chars[0] == '0' + 10) {
            buffer.chars[0] = '1';
            buffer.decimalPoint++;
        }
    }


    // The given fractionals number represents a fixed-point number with binary
    // point at bit (-exponent).
    // Preconditions:
    //   -128 <= exponent <= 0.
    //   0 <= fractionals * 2^exponent < 1
    //   The buffer holds the result.
    // The function will round its result. During the rounding-process digits not
    // generated by this function might be updated, and the decimal-point variable
    // might be updated. If this function generates the digits 99 and the buffer
    // already contained "199" (thus yielding a buffer of "19999") then a
    // rounding-up will change the contents of the buffer to "20000".
    static void fillFractionals(long fractionals, final int exponent,
                                final int fractional_count, final DtoaBuffer buffer) {
        assert (-128 <= exponent && exponent <= 0);
        // 'fractionals' is a fixed-decimalPoint number, with binary decimalPoint at bit
        // (-exponent). Inside the function the non-converted remainder of fractionals
        // is a fixed-decimalPoint number, with binary decimalPoint at bit 'decimalPoint'.
        if (-exponent <= 64) {
            // One 64 bit number is sufficient.
            assert (fractionals >>> 56 == 0);
            int point = -exponent;
            for (int i = 0; i < fractional_count; ++i) {
                if (fractionals == 0) break;
                // Instead of multiplying by 10 we multiply by 5 and adjust the point
                // location. This way the fractionals variable will not overflow.
                // Invariant at the beginning of the loop: fractionals < 2^point.
                // Initially we have: point <= 64 and fractionals < 2^56
                // After each iteration the point is decremented by one.
                // Note that 5^3 = 125 < 128 = 2^7.
                // Therefore three iterations of this loop will not overflow fractionals
                // (even without the subtraction at the end of the loop body). At this
                // time point will satisfy point <= 61 and therefore fractionals < 2^point
                // and any further multiplication of fractionals by 5 will not overflow.
                fractionals *= 5;
                point--;
                final int digit = (int) (fractionals >>> point);
                assert (digit <= 9);
                buffer.chars[buffer.length] = (char) ('0' + digit);
                buffer.length++;
                fractionals -= (long) (digit) << point;
            }
            // If the first bit after the point is set we have to round up.
            if (((fractionals >>> (point - 1)) & 1) == 1) {
                roundUp(buffer);
            }
        } else {  // We need 128 bits.
            assert (64 < -exponent && -exponent <= 128);
            final UInt128 fractionals128 = new UInt128(fractionals, 0);
            fractionals128.shift(-exponent - 64);
            int point = 128;
            for (int i = 0; i < fractional_count; ++i) {
                if (fractionals128.isZero()) break;
                // As before: instead of multiplying by 10 we multiply by 5 and adjust the
                // point location.
                // This multiplication will not overflow for the same reasons as before.
                fractionals128.multiply(5);
                point--;
                final int digit = fractionals128.divModPowerOf2(point);
                assert (digit <= 9);
                buffer.chars[buffer.length] = (char) ('0' + digit);
                buffer.length++;
            }
            if (fractionals128.bitAt(point - 1) == 1) {
                roundUp(buffer);
            }
        }
    }


    // Removes leading and trailing zeros.
    // If leading zeros are removed then the decimal point position is adjusted.
    static void trimZeros(final DtoaBuffer buffer) {
        while (buffer.length > 0 && buffer.chars[buffer.length - 1] == '0') {
            buffer.length--;
        }
        int first_non_zero = 0;
        while (first_non_zero < buffer.length && buffer.chars[first_non_zero] == '0') {
            first_non_zero++;
        }
        if (first_non_zero != 0) {
            for (int i = first_non_zero; i < buffer.length; ++i) {
                buffer.chars[i - first_non_zero] = buffer.chars[i];
            }
            buffer.length -= first_non_zero;
            buffer.decimalPoint -= first_non_zero;
        }
    }


    static boolean fastFixedDtoa(final double v,
                                 final int fractional_count,
                                 final DtoaBuffer buffer) {
        final long kMaxUInt32 = 0xFFFFFFFFL;
        final long l = IeeeDouble.doubleToLong(v);
        long significand = IeeeDouble.significand(l);
        final int exponent = IeeeDouble.exponent(l);
        // v = significand * 2^exponent (with significand a 53bit integer).
        // If the exponent is larger than 20 (i.e. we may have a 73bit number) then we
        // don't know how to compute the representation. 2^73 ~= 9.5*10^21.
        // If necessary this limit could probably be increased, but we don't need
        // more.
        if (exponent > 20) return false;
        if (fractional_count > 20) return false;
        // At most kDoubleSignificandSize bits of the significand are non-zero.
        // Given a 64 bit integer we have 11 0s followed by 53 potentially non-zero
        // bits:  0..11*..0xxx..53*..xx
        if (exponent + kDoubleSignificandSize > 64) {
            // The exponent must be > 11.
            //
            // We know that v = significand * 2^exponent.
            // And the exponent > 11.
            // We simplify the task by dividing v by 10^17.
            // The quotient delivers the first digits, and the remainder fits into a 64
            // bit number.
            // Dividing by 10^17 is equivalent to dividing by 5^17*2^17.
            final long kFive17 = 0xB1A2BC2EC5L;  // 5^17
            long divisor = kFive17;
            final int divisor_power = 17;
            long dividend = significand;
            final int quotient;
            final long remainder;
            // Let v = f * 2^e with f == significand and e == exponent.
            // Then need q (quotient) and r (remainder) as follows:
            //   v            = q * 10^17       + r
            //   f * 2^e      = q * 10^17       + r
            //   f * 2^e      = q * 5^17 * 2^17 + r
            // If e > 17 then
            //   f * 2^(e-17) = q * 5^17        + r/2^17
            // else
            //   f  = q * 5^17 * 2^(17-e) + r/2^e
            if (exponent > divisor_power) {
                // We only allow exponents of up to 20 and therefore (17 - e) <= 3
                dividend <<= exponent - divisor_power;
                quotient = (int) Long.divideUnsigned(dividend, divisor);
                remainder = Long.remainderUnsigned(dividend, divisor) << divisor_power;
            } else {
                divisor <<= divisor_power - exponent;
                quotient = (int) Long.divideUnsigned(dividend, divisor);
                remainder = Long.remainderUnsigned(dividend, divisor) << exponent;
            }
            fillDigits32(quotient, buffer);
            fillDigits64FixedLength(remainder, buffer);
            buffer.decimalPoint = buffer.length;
        } else if (exponent >= 0) {
            // 0 <= exponent <= 11
            significand <<= exponent;
            FillDigits64(significand, buffer);
            buffer.decimalPoint = buffer.length;
        } else if (exponent > -kDoubleSignificandSize) {
            // We have to cut the number.
            final long integrals = significand >>> -exponent;
            final long fractionals = significand - (integrals << -exponent);
            if (Long.compareUnsigned(integrals, kMaxUInt32) > 0) {
                FillDigits64(integrals, buffer);
            } else {
                fillDigits32((int) (integrals), buffer);
            }
            buffer.decimalPoint = buffer.length;
            fillFractionals(fractionals, exponent, fractional_count, buffer);
        } else if (exponent < -128) {
            // This configuration (with at most 20 digits) means that all digits must be
            // 0.
            assert (fractional_count <= 20);
            buffer.reset();
            buffer.decimalPoint = -fractional_count;
        } else {
            buffer.decimalPoint = 0;
            fillFractionals(significand, exponent, fractional_count, buffer);
        }
        trimZeros(buffer);
        if (buffer.length == 0) {
            // The string is empty and the decimal_point thus has no importance. Mimick
            // Gay's dtoa and and set it to -fractional_count.
            buffer.decimalPoint = -fractional_count;
        }
        return true;
    }

}
