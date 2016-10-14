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

import java.util.Arrays;

class Bignum {

    // 3584 = 128 * 28. We can represent 2^3584 > 10^1000 accurately.
    // This bignum can encode much bigger numbers, since it contains an
    // exponent.
    static final int kMaxSignificantBits = 3584;

    static final int kChunkSize = 32;       // size of int
    static final int kDoubleChunkSize = 64; // size of long
    // With bigit size of 28 we loose some bits, but a double still fits easily
    // into two ints, and more importantly we can use the Comba multiplication.
    static final int kBigitSize = 28;
    static final int kBigitMask = (1 << kBigitSize) - 1;
    // Every instance allocates kbigitLength ints on the stack. Bignums cannot
    // grow. There are no checks if the stack-allocated space is sufficient.
    static final int kBigitCapacity = kMaxSignificantBits / kBigitSize;

    private final int[] bigits_ = new int[kBigitCapacity];
    // A vector backed by bigits_buffer_. This way accesses to the array are
    // checked for out-of-bounds errors.
    // Vector<int> bigits_;
    private int used_digits_;
    // The Bignum's value equals value(bigits_) * 2^(exponent_ * kBigitSize).
    private int exponent_;

    Bignum() {}

    void times10() { multiplyByUInt32(10); }

    static boolean equal(final Bignum a, final Bignum b) {
        return compare(a, b) == 0;
    }
    static boolean lessEqual(final Bignum a, final Bignum b) {
        return compare(a, b) <= 0;
    }
    static boolean less(final Bignum a, final Bignum b) {
        return compare(a, b) < 0;
    }

    // Returns a + b == c
    static boolean plusEqual(final Bignum a, final Bignum b, final Bignum c) {
        return plusCompare(a, b, c) == 0;
    }
    // Returns a + b <= c
    static boolean plusLessEqual(final Bignum a, final Bignum b, final Bignum c) {
        return plusCompare(a, b, c) <= 0;
    }
    // Returns a + b < c
    static boolean plusLess(final Bignum a, final Bignum b, final Bignum c) {
        return plusCompare(a, b, c) < 0;
    }

    private void ensureCapacity(final int size) {
        if (size > kBigitCapacity) {
            throw new RuntimeException();
        }
    }

    // BigitLength includes the "hidden" digits encoded in the exponent.
    int bigitLength() { return used_digits_ + exponent_; }

    // Guaranteed to lie in one Bigit.
    void assignUInt16(final char value) {
        assert (kBigitSize >= 16);
        zero();
        if (value == 0) return;

        ensureCapacity(1);
        bigits_[0] = value;
        used_digits_ = 1;
    }


    void assignUInt64(long value) {
        final  int kUInt64Size = 64;

        zero();
        if (value == 0) return;

        final int needed_bigits = kUInt64Size / kBigitSize + 1;
        ensureCapacity(needed_bigits);
        for (int i = 0; i < needed_bigits; ++i) {
            bigits_[i] = (int) (value & kBigitMask);
            value = value >>> kBigitSize;
        }
        used_digits_ = needed_bigits;
        clamp();
    }


    void assignBignum(final Bignum other) {
        exponent_ = other.exponent_;
        for (int i = 0; i < other.used_digits_; ++i) {
            bigits_[i] = other.bigits_[i];
        }
        // Clear the excess digits (if there were any).
        for (int i = other.used_digits_; i < used_digits_; ++i) {
            bigits_[i] = 0;
        }
        used_digits_ = other.used_digits_;
    }


    static long readUInt64(final String str,
                           final int from,
                           final int digits_to_read) {
        long result = 0;
        for (int i = from; i < from + digits_to_read; ++i) {
            final int digit = str.charAt(i) - '0';
            assert (0 <= digit && digit <= 9);
            result = result * 10 + digit;
        }
        return result;
    }


    void assignDecimalString(final String str) {
        // 2^64 = 18446744073709551616 > 10^19
        final int kMaxUint64DecimalDigits = 19;
        zero();
        int length = str.length();
        int pos = 0;
        // Let's just say that each digit needs 4 bits.
        while (length >= kMaxUint64DecimalDigits) {
            final long digits = readUInt64(str, pos, kMaxUint64DecimalDigits);
            pos += kMaxUint64DecimalDigits;
            length -= kMaxUint64DecimalDigits;
            multiplyByPowerOfTen(kMaxUint64DecimalDigits);
            addUInt64(digits);
        }
        final long digits = readUInt64(str, pos, length);
        multiplyByPowerOfTen(length);
        addUInt64(digits);
        clamp();
    }


    static int hexCharValue(final char c) {
        if ('0' <= c && c <= '9') return c - '0';
        if ('a' <= c && c <= 'f') return 10 + c - 'a';
        assert ('A' <= c && c <= 'F');
        return 10 + c - 'A';
    }


    void assignHexString(final String str) {
        zero();
        final int length = str.length();

        final int needed_bigits = length * 4 / kBigitSize + 1;
        ensureCapacity(needed_bigits);
        int string_index = length - 1;
        for (int i = 0; i < needed_bigits - 1; ++i) {
            // These bigits are guaranteed to be "full".
            int current_bigit = 0;
            for (int j = 0; j < kBigitSize / 4; j++) {
                current_bigit += hexCharValue(str.charAt(string_index--)) << (j * 4);
            }
            bigits_[i] = current_bigit;
        }
        used_digits_ = needed_bigits - 1;

        int most_significant_bigit = 0;  // Could be = 0;
        for (int j = 0; j <= string_index; ++j) {
            most_significant_bigit <<= 4;
            most_significant_bigit += hexCharValue(str.charAt(j));
        }
        if (most_significant_bigit != 0) {
            bigits_[used_digits_] = most_significant_bigit;
            used_digits_++;
        }
        clamp();
    }


    void addUInt64(final long operand) {
        if (operand == 0) return;
        final Bignum other = new Bignum();
        other.assignUInt64(operand);
        addBignum(other);
    }


    void addBignum(final Bignum other) {
        assert (isClamped());
        assert (other.isClamped());

        // If this has a greater exponent than other append zero-bigits to this.
        // After this call exponent_ <= other.exponent_.
        align(other);

        // There are two possibilities:
        //   aaaaaaaaaaa 0000  (where the 0s represent a's exponent)
        //     bbbbb 00000000
        //   ----------------
        //   ccccccccccc 0000
        // or
        //    aaaaaaaaaa 0000
        //  bbbbbbbbb 0000000
        //  -----------------
        //  cccccccccccc 0000
        // In both cases we might need a carry bigit.

        ensureCapacity(1 + Math.max(bigitLength(), other.bigitLength()) - exponent_);
        int carry = 0;
        int bigit_pos = other.exponent_ - exponent_;
        assert (bigit_pos >= 0);
        for (int i = 0; i < other.used_digits_; ++i) {
            final int sum = bigits_[bigit_pos] + other.bigits_[i] + carry;
            bigits_[bigit_pos] = sum & kBigitMask;
            carry = sum >>> kBigitSize;
            bigit_pos++;
        }

        while (carry != 0) {
            final int sum = bigits_[bigit_pos] + carry;
            bigits_[bigit_pos] = sum & kBigitMask;
            carry = sum >>> kBigitSize;
            bigit_pos++;
        }
        used_digits_ = Math.max(bigit_pos, used_digits_);
        assert (isClamped());
    }


    void subtractBignum(final Bignum other) {
        assert (isClamped());
        assert (other.isClamped());
        // We require this to be bigger than other.
        assert (lessEqual(other, this));

        align(other);

        final int offset = other.exponent_ - exponent_;
        int borrow = 0;
        int i;
        for (i = 0; i < other.used_digits_; ++i) {
            assert ((borrow == 0) || (borrow == 1));
            final int difference = bigits_[i + offset] - other.bigits_[i] - borrow;
            bigits_[i + offset] = difference & kBigitMask;
            borrow = difference >>> (kChunkSize - 1);
        }
        while (borrow != 0) {
            final int difference = bigits_[i + offset] - borrow;
            bigits_[i + offset] = difference & kBigitMask;
            borrow = difference >>> (kChunkSize - 1);
            ++i;
        }
        clamp();
    }


    void shiftLeft(final int shift_amount) {
        if (used_digits_ == 0) return;
        exponent_ += shift_amount / kBigitSize;
        final int local_shift = shift_amount % kBigitSize;
        ensureCapacity(used_digits_ + 1);
        bigitsShiftLeft(local_shift);
    }


    void multiplyByUInt32(final int factor) {
        if (factor == 1) return;
        if (factor == 0) {
            zero();
            return;
        }
        if (used_digits_ == 0) return;

        // The product of a bigit with the factor is of size kBigitSize + 32.
        // Assert that this number + 1 (for the carry) fits into double int.
        assert (kDoubleChunkSize >= kBigitSize + 32 + 1);
        long carry = 0;
        for (int i = 0; i < used_digits_; ++i) {
            final long product = (factor & 0xFFFFFFFFL) * bigits_[i] + carry;
            bigits_[i] = (int) (product & kBigitMask);
            carry = product >>> kBigitSize;
        }
        while (carry != 0) {
            ensureCapacity(used_digits_ + 1);
            bigits_[used_digits_] = (int) (carry & kBigitMask);
            used_digits_++;
            carry >>>= kBigitSize;
        }
    }


    void multiplyByUInt64(final long factor) {
        if (factor == 1) return;
        if (factor == 0) {
            zero();
            return;
        }
        assert (kBigitSize < 32);
        long carry = 0;
        final long low = factor & 0xFFFFFFFFL;
        final long high = factor >>> 32;
        for (int i = 0; i < used_digits_; ++i) {
            final long product_low = low * bigits_[i];
            final long product_high = high * bigits_[i];
            final long tmp = (carry & kBigitMask) + product_low;
            bigits_[i] = (int) (tmp & kBigitMask);
            carry = (carry >>> kBigitSize) + (tmp >>> kBigitSize) +
                    (product_high << (32 - kBigitSize));
        }
        while (carry != 0) {
            ensureCapacity(used_digits_ + 1);
            bigits_[used_digits_] = (int) (carry & kBigitMask);
            used_digits_++;
            carry >>>= kBigitSize;
        }
    }


    void multiplyByPowerOfTen(final int exponent) {
        final long kFive27 = 0x6765c793fa10079dL;
        final int kFive1 = 5;
        final int kFive2 = kFive1 * 5;
        final int kFive3 = kFive2 * 5;
        final int kFive4 = kFive3 * 5;
        final int kFive5 = kFive4 * 5;
        final int kFive6 = kFive5 * 5;
        final int kFive7 = kFive6 * 5;
        final int kFive8 = kFive7 * 5;
        final int kFive9 = kFive8 * 5;
        final int kFive10 = kFive9 * 5;
        final int kFive11 = kFive10 * 5;
        final int kFive12 = kFive11 * 5;
        final int kFive13 = kFive12 * 5;
        final int kFive1_to_12[] =
                { kFive1, kFive2, kFive3, kFive4, kFive5, kFive6,
                        kFive7, kFive8, kFive9, kFive10, kFive11, kFive12 };

        assert (exponent >= 0);
        if (exponent == 0) return;
        if (used_digits_ == 0) return;

        // We shift by exponent at the end just before returning.
        int remaining_exponent = exponent;
        while (remaining_exponent >= 27) {
            multiplyByUInt64(kFive27);
            remaining_exponent -= 27;
        }
        while (remaining_exponent >= 13) {
            multiplyByUInt32(kFive13);
            remaining_exponent -= 13;
        }
        if (remaining_exponent > 0) {
            multiplyByUInt32(kFive1_to_12[remaining_exponent - 1]);
        }
        shiftLeft(exponent);
    }


    void square() {
        assert (isClamped());
        final int product_length = 2 * used_digits_;
        ensureCapacity(product_length);

        // Comba multiplication: compute each column separately.
        // Example: r = a2a1a0 * b2b1b0.
        //    r =  1    * a0b0 +
        //        10    * (a1b0 + a0b1) +
        //        100   * (a2b0 + a1b1 + a0b2) +
        //        1000  * (a2b1 + a1b2) +
        //        10000 * a2b2
        //
        // In the worst case we have to accumulate nb-digits products of digit*digit.
        //
        // Assert that the additional number of bits in a DoubleChunk are enough to
        // sum up used_digits of Bigit*Bigit.
        if ((1L << (2 * (kChunkSize - kBigitSize))) <= used_digits_) {
            throw new RuntimeException("unimplemented");
        }
        long accumulator = 0;
        // First shift the digits so we don't overwrite them.
        final int copy_offset = used_digits_;
        for (int i = 0; i < used_digits_; ++i) {
            bigits_[copy_offset + i] = bigits_[i];
        }
        // We have two loops to avoid some 'if's in the loop.
        for (int i = 0; i < used_digits_; ++i) {
            // Process temporary digit i with power i.
            // The sum of the two indices must be equal to i.
            int bigit_index1 = i;
            int bigit_index2 = 0;
            // Sum all of the sub-products.
            while (bigit_index1 >= 0) {
                final int int1 = bigits_[copy_offset + bigit_index1];
                final int int2 = bigits_[copy_offset + bigit_index2];
                accumulator += ((long) int1) * int2;
                bigit_index1--;
                bigit_index2++;
            }
            bigits_[i] = (int) (accumulator & kBigitMask);
            accumulator >>>= kBigitSize;
        }
        for (int i = used_digits_; i < product_length; ++i) {
            int bigit_index1 = used_digits_ - 1;
            int bigit_index2 = i - bigit_index1;
            // Invariant: sum of both indices is again equal to i.
            // Inner loop runs 0 times on last iteration, emptying accumulator.
            while (bigit_index2 < used_digits_) {
                final int int1 = bigits_[copy_offset + bigit_index1];
                final int int2 = bigits_[copy_offset + bigit_index2];
                accumulator += ((long) int1) * int2;
                bigit_index1--;
                bigit_index2++;
            }
            // The overwritten bigits_[i] will never be read in further loop iterations,
            // because bigit_index1 and bigit_index2 are always greater
            // than i - used_digits_.
            bigits_[i] = (int) (accumulator & kBigitMask);
            accumulator >>>= kBigitSize;
        }
        // Since the result was guaranteed to lie inside the number the
        // accumulator must be 0 now.
        assert (accumulator == 0);

        // Don't forget to update the used_digits and the exponent.
        used_digits_ = product_length;
        exponent_ *= 2;
        clamp();
    }


    void assignPowerUInt16(int base, final int power_exponent) {
        assert (base != 0);
        assert (power_exponent >= 0);
        if (power_exponent == 0) {
            assignUInt16((char) 1);
            return;
        }
        zero();
        int shifts = 0;
        // We expect base to be in range 2-32, and most often to be 10.
        // It does not make much sense to implement different algorithms for counting
        // the bits.
        while ((base & 1) == 0) {
            base >>>= 1;
            shifts++;
        }
        int bit_size = 0;
        int tmp_base = base;
        while (tmp_base != 0) {
            tmp_base >>>= 1;
            bit_size++;
        }
        final int final_size = bit_size * power_exponent;
        // 1 extra bigit for the shifting, and one for rounded final_size.
        ensureCapacity(final_size / kBigitSize + 2);

        // Left to Right exponentiation.
        int mask = 1;
        while (power_exponent >= mask) mask <<= 1;

        // The mask is now pointing to the bit above the most significant 1-bit of
        // power_exponent.
        // Get rid of first 1-bit;
        mask >>>= 2;
        long this_value = base;

        boolean delayed_multipliciation = false;
        final long max_32bits = 0xFFFFFFFFL;
        while (mask != 0 && this_value <= max_32bits) {
            this_value = this_value * this_value;
            // Verify that there is enough space in this_value to perform the
            // multiplication.  The first bit_size bits must be 0.
            if ((power_exponent & mask) != 0) {
                final long base_bits_mask =
                        ~((1L << (64 - bit_size)) - 1);
                final boolean high_bits_zero = (this_value & base_bits_mask) == 0;
                if (high_bits_zero) {
                    this_value *= base;
                } else {
                    delayed_multipliciation = true;
                }
            }
            mask >>>= 1;
        }
        assignUInt64(this_value);
        if (delayed_multipliciation) {
            multiplyByUInt32(base);
        }

        // Now do the same thing as a bignum.
        while (mask != 0) {
            square();
            if ((power_exponent & mask) != 0) {
                multiplyByUInt32(base);
            }
            mask >>>= 1;
        }

        // And finally add the saved shifts.
        shiftLeft(shifts * power_exponent);
    }


    // Precondition: this/other < 16bit.
    char divideModuloIntBignum(final Bignum other) {
        assert (isClamped());
        assert (other.isClamped());
        assert (other.used_digits_ > 0);

        // Easy case: if we have less digits than the divisor than the result is 0.
        // Note: this handles the case where this == 0, too.
        if (bigitLength() < other.bigitLength()) {
            return 0;
        }

        align(other);

        char result = 0;

        // Start by removing multiples of 'other' until both numbers have the same
        // number of digits.
        while (bigitLength() > other.bigitLength()) {
            // This naive approach is extremely inefficient if `this` divided by other
            // is big. This function is implemented for doubleToString where
            // the result should be small (less than 10).
            assert (other.bigits_[other.used_digits_ - 1] >= ((1 << kBigitSize) / 16));
            assert (bigits_[used_digits_ - 1] < 0x10000);
            // Remove the multiples of the first digit.
            // Example this = 23 and other equals 9. -> Remove 2 multiples.
            result += (bigits_[used_digits_ - 1]);
            subtractTimes(other, bigits_[used_digits_ - 1]);
        }

        assert (bigitLength() == other.bigitLength());

        // Both bignums are at the same length now.
        // Since other has more than 0 digits we know that the access to
        // bigits_[used_digits_ - 1] is safe.
        final int this_bigit = bigits_[used_digits_ - 1];
        final int other_bigit = other.bigits_[other.used_digits_ - 1];

        if (other.used_digits_ == 1) {
            // Shortcut for easy (and common) case.
            final int quotient = Integer.divideUnsigned(this_bigit, other_bigit);
            bigits_[used_digits_ - 1] = this_bigit - other_bigit * quotient;
            assert (Integer.compareUnsigned(quotient, 0x10000) < 0);
            result += quotient;
            clamp();
            return result;
        }

        final int division_estimate = Integer.divideUnsigned(this_bigit, (other_bigit + 1));
        assert (Integer.compareUnsigned(division_estimate, 0x10000) < 0);
        result += division_estimate;
        subtractTimes(other, division_estimate);

        if (other_bigit * (division_estimate + 1) > this_bigit) {
            // No need to even try to subtract. Even if other's remaining digits were 0
            // another subtraction would be too much.
            return result;
        }

        while (lessEqual(other, this)) {
            subtractBignum(other);
            result++;
        }
        return result;
    }


    static int sizeInHexChars(int number) {
        assert (number > 0);
        int result = 0;
        while (number != 0) {
            number >>>= 4;
            result++;
        }
        return result;
    }


    static char hexCharOfValue(final int value) {
        assert (0 <= value && value <= 16);
        if (value < 10) return (char) (value + '0');
        return (char) (value - 10 + 'A');
    }


    String toHexString() {
        assert (isClamped());
        // Each bigit must be printable as separate hex-character.
        assert (kBigitSize % 4 == 0);
        final int kHexCharsPerBigit = kBigitSize / 4;

        if (used_digits_ == 0) {
            return "0";
        }

        final int needed_chars = (bigitLength() - 1) * kHexCharsPerBigit +
                sizeInHexChars(bigits_[used_digits_ - 1]);
        final StringBuilder buffer = new StringBuilder(needed_chars);
        buffer.setLength(needed_chars);

        int string_index = needed_chars - 1;
        for (int i = 0; i < exponent_; ++i) {
            for (int j = 0; j < kHexCharsPerBigit; ++j) {
                buffer.setCharAt(string_index--, '0');
            }
        }
        for (int i = 0; i < used_digits_ - 1; ++i) {
            int current_bigit = bigits_[i];
            for (int j = 0; j < kHexCharsPerBigit; ++j) {
                buffer.setCharAt(string_index--, hexCharOfValue(current_bigit & 0xF));
                current_bigit >>>= 4;
            }
        }
        // And finally the last bigit.
        int most_significant_bigit = bigits_[used_digits_ - 1];
        while (most_significant_bigit != 0) {
            buffer.setCharAt(string_index--, hexCharOfValue(most_significant_bigit & 0xF));
            most_significant_bigit >>>= 4;
        }
        return buffer.toString();
    }


    int bigitAt(final int index) {
        if (index >= bigitLength()) return 0;
        if (index < exponent_) return 0;
        return bigits_[index - exponent_];
    }


    static int compare(final Bignum a, final Bignum b) {
        assert (a.isClamped());
        assert (b.isClamped());
        final int bigit_length_a = a.bigitLength();
        final int bigit_length_b = b.bigitLength();
        if (bigit_length_a < bigit_length_b) return -1;
        if (bigit_length_a > bigit_length_b) return +1;
        for (int i = bigit_length_a - 1; i >= Math.min(a.exponent_, b.exponent_); --i) {
            final int bigit_a = a.bigitAt(i);
            final int bigit_b = b.bigitAt(i);
            if (bigit_a < bigit_b) return -1;
            if (bigit_a > bigit_b) return +1;
            // Otherwise they are equal up to this digit. Try the next digit.
        }
        return 0;
    }


    static int plusCompare(final Bignum a, final Bignum b, final Bignum c) {
        assert (a.isClamped());
        assert (b.isClamped());
        assert (c.isClamped());
        if (a.bigitLength() < b.bigitLength()) {
            return plusCompare(b, a, c);
        }
        if (a.bigitLength() + 1 < c.bigitLength()) return -1;
        if (a.bigitLength() > c.bigitLength()) return +1;
        // The exponent encodes 0-bigits. So if there are more 0-digits in 'a' than
        // 'b' has digits, then the bigit-length of 'a'+'b' must be equal to the one
        // of 'a'.
        if (a.exponent_ >= b.bigitLength() && a.bigitLength() < c.bigitLength()) {
            return -1;
        }

        int borrow = 0;
        // Starting at min_exponent all digits are == 0. So no need to compare them.
        final int min_exponent = Math.min(Math.min(a.exponent_, b.exponent_), c.exponent_);
        for (int i = c.bigitLength() - 1; i >= min_exponent; --i) {
            final int int_a = a.bigitAt(i);
            final int int_b = b.bigitAt(i);
            final int int_c = c.bigitAt(i);
            final int sum = int_a + int_b;
            if (sum > int_c + borrow) {
                return +1;
            } else {
                borrow = int_c + borrow - sum;
                if (borrow > 1) return -1;
                borrow <<= kBigitSize;
            }
        }
        if (borrow == 0) return 0;
        return -1;
    }


    void clamp() {
        while (used_digits_ > 0 && bigits_[used_digits_ - 1] == 0) {
            used_digits_--;
        }
        if (used_digits_ == 0) {
            // Zero.
            exponent_ = 0;
        }
    }


    boolean isClamped() {
        return used_digits_ == 0 || bigits_[used_digits_ - 1] != 0;
    }


    void zero() {
        for (int i = 0; i < used_digits_; ++i) {
            bigits_[i] = 0;
        }
        used_digits_ = 0;
        exponent_ = 0;
    }


    void align(final Bignum other) {
        if (exponent_ > other.exponent_) {
            // If "X" represents a "hidden" digit (by the exponent) then we are in the
            // following case (a == this, b == other):
            // a:  aaaaaaXXXX   or a:   aaaaaXXX
            // b:     bbbbbbX      b: bbbbbbbbXX
            // We replace some of the hidden digits (X) of a with 0 digits.
            // a:  aaaaaa000X   or a:   aaaaa0XX
            final int zero_digits = exponent_ - other.exponent_;
            ensureCapacity(used_digits_ + zero_digits);
            for (int i = used_digits_ - 1; i >= 0; --i) {
                bigits_[i + zero_digits] = bigits_[i];
            }
            for (int i = 0; i < zero_digits; ++i) {
                bigits_[i] = 0;
            }
            used_digits_ += zero_digits;
            exponent_ -= zero_digits;
            assert (used_digits_ >= 0);
            assert (exponent_ >= 0);
        }
    }


    void bigitsShiftLeft(final int shift_amount) {
        assert (shift_amount < kBigitSize);
        assert (shift_amount >= 0);
        int carry = 0;
        for (int i = 0; i < used_digits_; ++i) {
            final int new_carry = bigits_[i] >>> (kBigitSize - shift_amount);
            bigits_[i] = ((bigits_[i] << shift_amount) + carry) & kBigitMask;
            carry = new_carry;
        }
        if (carry != 0) {
            bigits_[used_digits_] = carry;
            used_digits_++;
        }
    }


    void subtractTimes(final Bignum other, final int factor) {
        assert (exponent_ <= other.exponent_);
        if (factor < 3) {
            for (int i = 0; i < factor; ++i) {
                subtractBignum(other);
            }
            return;
        }
        int borrow = 0;
        final int exponent_diff = other.exponent_ - exponent_;
        for (int i = 0; i < other.used_digits_; ++i) {
            final long product = ((long) factor) * other.bigits_[i];
            final long remove = borrow + product;
            final int difference = bigits_[i + exponent_diff] - (int) (remove & kBigitMask);
            bigits_[i + exponent_diff] = difference & kBigitMask;
            borrow = (int) ((difference >>> (kChunkSize - 1)) +
                    (remove >>> kBigitSize));
        }
        for (int i = other.used_digits_ + exponent_diff; i < used_digits_; ++i) {
            if (borrow == 0) return;
            final int difference = bigits_[i] - borrow;
            bigits_[i] = difference & kBigitMask;
            borrow = difference >>> (kChunkSize - 1);
        }
        clamp();
    }

    @Override
    public String toString() {
        return "Bignum" + Arrays.toString(bigits_);
    }
}
