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

// Fast Dtoa implementation supporting shortest and precision modes. Does not
// work for all numbers so BugnumDtoa is used as fallback.
class FastDtoa {

    // FastDtoa will produce at most kFastDtoaMaximalLength digits. This does not
    // include the terminating '\0' character.
    static final int kFastDtoaMaximalLength = 17;

    // The minimal and maximal target exponent define the range of w's binary
    // exponent, where 'w' is the result of multiplying the input by a cached power
    // of ten.
    //
    // A different range might be chosen on a different platform, to optimize digit
    // generation, but a smaller range requires more powers of ten to be cached.
    static final int kMinimalTargetExponent = -60;
    static final int kMaximalTargetExponent = -32;


    // Adjusts the last digit of the generated number, and screens out generated
    // solutions that may be inaccurate. A solution may be inaccurate if it is
    // outside the safe interval, or if we cannot prove that it is closer to the
    // input than a neighboring representation of the same length.
    //
    // Input: * buffer containing the digits of too_high / 10^kappa
    //        * distance_too_high_w == (too_high - w).f() * unit
    //        * unsafe_interval == (too_high - too_low).f() * unit
    //        * rest = (too_high - buffer * 10^kappa).f() * unit
    //        * ten_kappa = 10^kappa * unit
    //        * unit = the common multiplier
    // Output: returns true if the buffer is guaranteed to contain the closest
    //    representable number to the input.
    //  Modifies the generated digits in the buffer to approach (round towards) w.
    static boolean roundWeed(final DtoaBuffer buffer,
                             final long distance_too_high_w,
                             final long unsafe_interval,
                             long rest,
                             final long ten_kappa,
                             final long unit) {
        final long small_distance = distance_too_high_w - unit;
        final long big_distance = distance_too_high_w + unit;
        // Let w_low  = too_high - big_distance, and
        //     w_high = too_high - small_distance.
        // Note: w_low < w < w_high
        //
        // The real w (* unit) must lie somewhere inside the interval
        // ]w_low; w_high[ (often written as "(w_low; w_high)")

        // Basically the buffer currently contains a number in the unsafe interval
        // ]too_low; too_high[ with too_low < w < too_high
        //
        //  too_high - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
        //                     ^v 1 unit            ^      ^                 ^      ^
        //  boundary_high ---------------------     .      .                 .      .
        //                     ^v 1 unit            .      .                 .      .
        //   - - - - - - - - - - - - - - - - - - -  +  - - + - - - - - -     .      .
        //                                          .      .         ^       .      .
        //                                          .  big_distance  .       .      .
        //                                          .      .         .       .    rest
        //                              small_distance     .         .       .      .
        //                                          v      .         .       .      .
        //  w_high - - - - - - - - - - - - - - - - - -     .         .       .      .
        //                     ^v 1 unit                   .         .       .      .
        //  w ----------------------------------------     .         .       .      .
        //                     ^v 1 unit                   v         .       .      .
        //  w_low  - - - - - - - - - - - - - - - - - - - - -         .       .      .
        //                                                           .       .      v
        //  buffer --------------------------------------------------+-------+--------
        //                                                           .       .
        //                                                  safe_interval    .
        //                                                           v       .
        //   - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -     .
        //                     ^v 1 unit                                     .
        //  boundary_low -------------------------                     unsafe_interval
        //                     ^v 1 unit                                     v
        //  too_low  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
        //
        //
        // Note that the value of buffer could lie anywhere inside the range too_low
        // to too_high.
        //
        // boundary_low, boundary_high and w are approximations of the real boundaries
        // and v (the input number). They are guaranteed to be precise up to one unit.
        // In fact the error is guaranteed to be strictly less than one unit.
        //
        // Anything that lies outside the unsafe interval is guaranteed not to round
        // to v when read again.
        // Anything that lies inside the safe interval is guaranteed to round to v
        // when read again.
        // If the number inside the buffer lies inside the unsafe interval but not
        // inside the safe interval then we simply do not know and bail out (returning
        // false).
        //
        // Similarly we have to take into account the imprecision of 'w' when finding
        // the closest representation of 'w'. If we have two potential
        // representations, and one is closer to both w_low and w_high, then we know
        // it is closer to the actual value v.
        //
        // By generating the digits of too_high we got the largest (closest to
        // too_high) buffer that is still in the unsafe interval. In the case where
        // w_high < buffer < too_high we try to decrement the buffer.
        // This way the buffer approaches (rounds towards) w.
        // There are 3 conditions that stop the decrementation process:
        //   1) the buffer is already below w_high
        //   2) decrementing the buffer would make it leave the unsafe interval
        //   3) decrementing the buffer would yield a number below w_high and farther
        //      away than the current number. In other words:
        //              (buffer{-1} < w_high) && w_high - buffer{-1} > buffer - w_high
        // Instead of using the buffer directly we use its distance to too_high.
        // Conceptually rest ~= too_high - buffer
        // We need to do the following tests in this order to avoid over- and
        // underflows.
        assert (Long.compareUnsigned(rest, unsafe_interval) <= 0);
        while (Long.compareUnsigned(rest, small_distance) < 0 &&  // Negated condition 1
                Long.compareUnsigned(unsafe_interval - rest, ten_kappa) >= 0 &&  // Negated condition 2
                (Long.compareUnsigned(rest + ten_kappa, small_distance) < 0 ||  // buffer{-1} > w_high
                        Long.compareUnsigned(small_distance - rest, rest + ten_kappa - small_distance) >= 0)) {
            buffer.chars[buffer.length - 1]--;
            rest += ten_kappa;
        }

        // We have approached w+ as much as possible. We now test if approaching w-
        // would require changing the buffer. If yes, then we have two possible
        // representations close to w, but we cannot decide which one is closer.
        if (Long.compareUnsigned(rest, big_distance) < 0 &&
                Long.compareUnsigned(unsafe_interval - rest, ten_kappa) >= 0 &&
                (Long.compareUnsigned(rest + ten_kappa, big_distance) < 0 ||
                        Long.compareUnsigned(big_distance - rest, rest + ten_kappa - big_distance) > 0)) {
            return false;
        }

        // Weeding test.
        //   The safe interval is [too_low + 2 ulp; too_high - 2 ulp]
        //   Since too_low = too_high - unsafe_interval this is equivalent to
        //      [too_high - unsafe_interval + 4 ulp; too_high - 2 ulp]
        //   Conceptually we have: rest ~= too_high - buffer
        return Long.compareUnsigned(2 * unit, rest) <= 0 && Long.compareUnsigned(rest, unsafe_interval - 4 * unit) <= 0;
    }

    // Rounds the buffer upwards if the result is closer to v by possibly adding
    // 1 to the buffer. If the precision of the calculation is not sufficient to
    // round correctly, return false.
    // The rounding might shift the whole buffer in which case the kappa is
    // adjusted. For example "99", kappa = 3 might become "10", kappa = 4.
    //
    // If 2*rest > ten_kappa then the buffer needs to be round up.
    // rest can have an error of +/- 1 unit. This function accounts for the
    // imprecision and returns false, if the rounding direction cannot be
    // unambiguously determined.
    //
    // Precondition: rest < ten_kappa.
    // Changed return type to int to let caller know they should increase kappa (return value 2)
    static int roundWeedCounted(final char[] buffer,
                                final int length,
                                final long rest,
                                final long  ten_kappa,
                                final long  unit) {
        assert(Long.compareUnsigned(rest, ten_kappa) < 0);
        // The following tests are done in a specific order to avoid overflows. They
        // will work correctly with any uint64 values of rest < ten_kappa and unit.
        //
        // If the unit is too big, then we don't know which way to round. For example
        // a unit of 50 means that the real number lies within rest +/- 50. If
        // 10^kappa == 40 then there is no way to tell which way to round.
        if (Long.compareUnsigned(unit, ten_kappa) >= 0) return 0;
        // Even if unit is just half the size of 10^kappa we are already completely
        // lost. (And after the previous test we know that the expression will not
        // over/underflow.)
        if (Long.compareUnsigned(ten_kappa - unit, unit) <= 0) return 0;
        // If 2 * (rest + unit) <= 10^kappa we can safely round down.
        if (Long.compareUnsigned(ten_kappa - rest, rest) > 0 && Long.compareUnsigned(ten_kappa - 2 * rest, 2 * unit) >= 0) {
            return 1;
        }
        // If 2 * (rest - unit) >= 10^kappa, then we can safely round up.
        if (Long.compareUnsigned(rest, unit) > 0 && Long.compareUnsigned(ten_kappa - (rest - unit), (rest - unit)) <= 0) {
            // Increment the last digit recursively until we find a non '9' digit.
            buffer[length - 1]++;
            for (int i = length - 1; i > 0; --i) {
                if (buffer[i] != '0' + 10) break;
                buffer[i] = '0';
                buffer[i - 1]++;
            }
            // If the first digit is now '0'+ 10 we had a buffer with all '9's. With the
            // exception of the first digit all digits are now '0'. Simply switch the
            // first digit to '1' and adjust the kappa. Example: "99" becomes "10" and
            // the power (the kappa) is increased.
            if (buffer[0] == '0' + 10) {
                buffer[0] = '1';
                // Return value of 2 tells caller to increase (*kappa) += 1
                return 2;
            }
            return 1;
        }
        return 0;
    }

    // Returns the biggest power of ten that is less than or equal to the given
    // number. We furthermore receive the maximum number of bits 'number' has.
    //
    // Returns power == 10^(exponent_plus_one-1) such that
    //    power <= number < power * 10.
    // If number_bits == 0 then 0^(0-1) is returned.
    // The number of bits must be <= 32.
    // Precondition: number < (1 << (number_bits + 1)).

    // Inspired by the method for finding an integer log base 10 from here:
    // http://graphics.stanford.edu/~seander/bithacks.html#IntegerLog10
    static final int kSmallPowersOfTen[] =
    {0, 1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000,
            1000000000};

    // Returns the biggest power of ten that is less than or equal than the given
    // number. We furthermore receive the maximum number of bits 'number' has.
    // If number_bits == 0 then 0^-1 is returned
    // The number of bits must be <= 32.
    // Precondition: (1 << number_bits) <= number < (1 << (number_bits + 1)).
    static long biggestPowerTen(final int number,
                                final int number_bits) {
        final int power, exponent_plus_one;
        assert ((number & 0xFFFFFFFFL) < (1l << (number_bits + 1)));
        // 1233/4096 is approximately 1/lg(10).
        int exponent_plus_one_guess = ((number_bits + 1) * 1233 >>> 12);
        // We increment to skip over the first entry in the kPowersOf10 table.
        // Note: kPowersOf10[i] == 10^(i-1).
        exponent_plus_one_guess++;
        // We don't have any guarantees that 2^number_bits <= number.
        if (number < kSmallPowersOfTen[exponent_plus_one_guess]) {
            exponent_plus_one_guess--;
        }
        power = kSmallPowersOfTen[exponent_plus_one_guess];
        exponent_plus_one = exponent_plus_one_guess;

        return ((long) power << 32) | (long) exponent_plus_one;
    }

    // Generates the digits of input number w.
    // w is a floating-point number (DiyFp), consisting of a significand and an
    // exponent. Its exponent is bounded by kMinimalTargetExponent and
    // kMaximalTargetExponent.
    //       Hence -60 <= w.e() <= -32.
    //
    // Returns false if it fails, in which case the generated digits in the buffer
    // should not be used.
    // Preconditions:
    //  * low, w and high are correct up to 1 ulp (unit in the last place). That
    //    is, their error must be less than a unit of their last digits.
    //  * low.e() == w.e() == high.e()
    //  * low < w < high, and taking into account their error: low~ <= high~
    //  * kMinimalTargetExponent <= w.e() <= kMaximalTargetExponent
    // Postconditions: returns false if procedure fails.
    //   otherwise:
    //     * buffer is not null-terminated, but len contains the number of digits.
    //     * buffer contains the shortest possible decimal digit-sequence
    //       such that LOW < buffer * 10^kappa < HIGH, where LOW and HIGH are the
    //       correct values of low and high (without their error).
    //     * if more than one decimal representation gives the minimal number of
    //       decimal digits then the one closest to W (where W is the correct value
    //       of w) is chosen.
    // Remark: this procedure takes into account the imprecision of its input
    //   numbers. If the precision is not enough to guarantee all the postconditions
    //   then false is returned. This usually happens rarely (~0.5%).
    //
    // Say, for the sake of example, that
    //   w.e() == -48, and w.f() == 0x1234567890abcdef
    // w's value can be computed by w.f() * 2^w.e()
    // We can obtain w's integral digits by simply shifting w.f() by -w.e().
    //  -> w's integral part is 0x1234
    //  w's fractional part is therefore 0x567890abcdef.
    // Printing w's integral part is easy (simply print 0x1234 in decimal).
    // In order to print its fraction we repeatedly multiply the fraction by 10 and
    // get each digit. Example the first digit after the point would be computed by
    //   (0x567890abcdef * 10) >> 48. -> 3
    // The whole thing becomes slightly more complicated because we want to stop
    // once we have enough digits. That is, once the digits inside the buffer
    // represent 'w' we can stop. Everything inside the interval low - high
    // represents w. However we have to pay attention to low, high and w's
    // imprecision.
    static boolean digitGen(final DiyFp low,
                            final DiyFp w,
                            final DiyFp high,
                            final DtoaBuffer buffer,
                            final int mk) {
        assert(low.e() == w.e() && w.e() == high.e());
        assert Long.compareUnsigned(low.f() + 1, high.f() - 1) <= 0;
        assert(kMinimalTargetExponent <= w.e() && w.e() <= kMaximalTargetExponent);
        // low, w and high are imprecise, but by less than one ulp (unit in the last
        // place).
        // If we remove (resp. add) 1 ulp from low (resp. high) we are certain that
        // the new numbers are outside of the interval we want the final
        // representation to lie in.
        // Inversely adding (resp. removing) 1 ulp from low (resp. high) would yield
        // numbers that are certain to lie in the interval. We will use this fact
        // later on.
        // We will now start by generating the digits within the uncertain
        // interval. Later we will weed out representations that lie outside the safe
        // interval and thus _might_ lie outside the correct interval.
        long unit = 1;
        final DiyFp too_low = new DiyFp(low.f() - unit, low.e());
        final DiyFp too_high = new DiyFp(high.f() + unit, high.e());
        // too_low and too_high are guaranteed to lie outside the interval we want the
        // generated number in.
        final DiyFp unsafe_interval = DiyFp.minus(too_high, too_low);
        // We now cut the input number into two parts: the integral digits and the
        // fractionals. We will not write any decimal separator though, but adapt
        // kappa instead.
        // Reminder: we are currently computing the digits (stored inside the buffer)
        // such that:   too_low < buffer * 10^kappa < too_high
        // We use too_high for the digit_generation and stop as soon as possible.
        // If we stop early we effectively round down.
        final DiyFp one = new DiyFp(1l << -w.e(), w.e());
        // Division by one is a shift.
        int integrals = (int)(too_high.f() >>> -one.e());
        // Modulo by one is an and.
        long fractionals = too_high.f() & (one.f() - 1);
        int divisor;
        final int divisor_exponent_plus_one;
        final long result = biggestPowerTen(integrals, DiyFp.kSignificandSize - (-one.e()));
        divisor = (int) (result >>> 32);
        divisor_exponent_plus_one = (int) result;
        int kappa = divisor_exponent_plus_one;
        // Loop invariant: buffer = too_high / 10^kappa  (integer division)
        // The invariant holds for the first iteration: kappa has been initialized
        // with the divisor exponent + 1. And the divisor is the biggest power of ten
        // that is smaller than integrals.
        while (kappa > 0) {
            final int digit = integrals / divisor;
            assert (digit <= 9);
            buffer.append((char) ('0' + digit));
            integrals %= divisor;
            kappa--;
            // Note that kappa now equals the exponent of the divisor and that the
            // invariant thus holds again.
            final long rest =
                    ((long) integrals << -one.e()) + fractionals;
            // Invariant: too_high = buffer * 10^kappa + DiyFp(rest, one.e())
            // Reminder: unsafe_interval.e() == one.e()
            if (Long.compareUnsigned(rest, unsafe_interval.f()) < 0) {
                // Rounding down (by not emitting the remaining digits) yields a number
                // that lies within the unsafe interval.
                buffer.decimalPoint = buffer.length - mk + kappa;
                return roundWeed(buffer, DiyFp.minus(too_high, w).f(),
                        unsafe_interval.f(), rest,
                        (long) divisor << -one.e(), unit);
            }
            divisor /= 10;
        }

        // The integrals have been generated. We are at the point of the decimal
        // separator. In the following loop we simply multiply the remaining digits by
        // 10 and divide by one. We just need to pay attention to multiply associated
        // data (like the interval or 'unit'), too.
        // Note that the multiplication by 10 does not overflow, because w.e >= -60
        // and thus one.e >= -60.
        assert (one.e() >= -60);
        assert (fractionals < one.f());
        assert (Long.compareUnsigned(Long.divideUnsigned(0xFFFFFFFFFFFFFFFFL, 10), one.f()) >= 0);
        for (;;) {
            fractionals *= 10;
            unit *= 10;
            unsafe_interval.setF(unsafe_interval.f() * 10);
            // Integer division by one.
            final int digit = (int) (fractionals >>> -one.e());
            assert (digit <= 9);
            buffer.append((char) ('0' + digit));
            fractionals &= one.f() - 1;  // Modulo by one.
            kappa--;
            if (Long.compareUnsigned(fractionals, unsafe_interval.f()) < 0) {
                buffer.decimalPoint = buffer.length - mk + kappa;
                return roundWeed(buffer, DiyFp.minus(too_high, w).f() * unit,
                        unsafe_interval.f(), fractionals, one.f(), unit);
            }
        }
    }

    // Generates (at most) requested_digits digits of input number w.
    // w is a floating-point number (DiyFp), consisting of a significand and an
    // exponent. Its exponent is bounded by kMinimalTargetExponent and
    // kMaximalTargetExponent.
    //       Hence -60 <= w.e() <= -32.
    //
    // Returns false if it fails, in which case the generated digits in the buffer
    // should not be used.
    // Preconditions:
    //  * w is correct up to 1 ulp (unit in the last place). That
    //    is, its error must be strictly less than a unit of its last digit.
    //  * kMinimalTargetExponent <= w.e() <= kMaximalTargetExponent
    //
    // Postconditions: returns false if procedure fails.
    //   otherwise:
    //     * buffer is not null-terminated, but length contains the number of
    //       digits.
    //     * the representation in buffer is the most precise representation of
    //       requested_digits digits.
    //     * buffer contains at most requested_digits digits of w. If there are less
    //       than requested_digits digits then some trailing '0's have been removed.
    //     * kappa is such that
    //            w = buffer * 10^kappa + eps with |eps| < 10^kappa / 2.
    //
    // Remark: This procedure takes into account the imprecision of its input
    //   numbers. If the precision is not enough to guarantee all the postconditions
    //   then false is returned. This usually happens rarely, but the failure-rate
    //   increases with higher requested_digits.
    static boolean digitGenCounted(final DiyFp w,
                                   int requested_digits,
                                   final DtoaBuffer buffer,
                                   final int mk) {
        assert (kMinimalTargetExponent <= w.e() && w.e() <= kMaximalTargetExponent);
        assert (kMinimalTargetExponent >= -60);
        assert (kMaximalTargetExponent <= -32);
        // w is assumed to have an error less than 1 unit. Whenever w is scaled we
        // also scale its error.
        long w_error = 1;
        // We cut the input number into two parts: the integral digits and the
        // fractional digits. We don't emit any decimal separator, but adapt kappa
        // instead. Example: instead of writing "1.2" we put "12" into the buffer and
        // increase kappa by 1.
        final DiyFp one = new DiyFp(1l << -w.e(), w.e());
        // Division by one is a shift.
        int integrals = (int) (w.f() >>> -one.e());
        // Modulo by one is an and.
        long fractionals = w.f() & (one.f() - 1);
        int divisor;
        final int divisor_exponent_plus_one;
        final long biggestPower = biggestPowerTen(integrals, DiyFp.kSignificandSize - (-one.e()));
        divisor = (int) (biggestPower >>> 32);
        divisor_exponent_plus_one = (int) biggestPower;
        int kappa = divisor_exponent_plus_one;

        // Loop invariant: buffer = w / 10^kappa  (integer division)
        // The invariant holds for the first iteration: kappa has been initialized
        // with the divisor exponent + 1. And the divisor is the biggest power of ten
        // that is smaller than 'integrals'.
        while (kappa > 0) {
            final int digit = integrals / divisor;
            assert (digit <= 9);
            buffer.append((char) ('0' + digit));
            requested_digits--;
            integrals %= divisor;
            kappa--;
            // Note that kappa now equals the exponent of the divisor and that the
            // invariant thus holds again.
            if (requested_digits == 0) break;
            divisor /= 10;
        }

        if (requested_digits == 0) {
            final long rest =
                    ((long) (integrals) << -one.e()) + fractionals;
            final int result = roundWeedCounted(buffer.chars, buffer.length, rest,
                    (long) divisor << -one.e(), w_error);
            buffer.decimalPoint = buffer.length - mk + kappa + (result == 2 ? 1 : 0);
            return result > 0;
        }

        // The integrals have been generated. We are at the decimalPoint of the decimal
        // separator. In the following loop we simply multiply the remaining digits by
        // 10 and divide by one. We just need to pay attention to multiply associated
        // data (the 'unit'), too.
        // Note that the multiplication by 10 does not overflow, because w.e >= -60
        // and thus one.e >= -60.
        assert (one.e() >= -60);
        assert (fractionals < one.f());
        assert (Long.compareUnsigned(Long.divideUnsigned(0xFFFFFFFFFFFFFFFFL, 10), one.f()) >= 0);
        while (requested_digits > 0 && fractionals > w_error) {
            fractionals *= 10;
            w_error *= 10;
            // Integer division by one.
            final int digit = (int) (fractionals >>> -one.e());
            assert (digit <= 9);
            buffer.append((char) ('0' + digit));
            requested_digits--;
            fractionals &= one.f() - 1;  // Modulo by one.
            kappa--;
        }
        if (requested_digits != 0) return false;
        final int result = roundWeedCounted(buffer.chars, buffer.length, fractionals, one.f(), w_error);
        buffer.decimalPoint = buffer.length - mk + kappa + (result == 2 ? 1 : 0);
        return result > 0;
    }


    // Provides a decimal representation of v.
    // Returns true if it succeeds, otherwise the result cannot be trusted.
    // There will be *length digits inside the buffer (not null-terminated).
    // If the function returns true then
    //        v == (double) (buffer * 10^decimal_exponent).
    // The digits in the buffer are the shortest representation possible: no
    // 0.09999999999999999 instead of 0.1. The shorter representation will even be
    // chosen even if the longer one would be closer to v.
    // The last digit will be closest to the actual v. That is, even if several
    // digits might correctly yield 'v' when read again, the closest will be
    // computed.
    static boolean grisu3(final double v, final DtoaBuffer buffer) {
        final long d64 = IeeeDouble.doubleToLong(v);
        final DiyFp w = IeeeDouble.asNormalizedDiyFp(d64);
        // boundary_minus and boundary_plus are the boundaries between v and its
        // closest floating-point neighbors. Any number strictly between
        // boundary_minus and boundary_plus will round to v when convert to a double.
        // Grisu3 will never output representations that lie exactly on a boundary.
        final DiyFp boundary_minus = new DiyFp(), boundary_plus = new DiyFp();
        IeeeDouble.normalizedBoundaries(d64, boundary_minus, boundary_plus);
        assert(boundary_plus.e() == w.e());
        final DiyFp ten_mk = new DiyFp();  // Cached power of ten: 10^-k
        final int mk;                      // -k
        final int ten_mk_minimal_binary_exponent =
                kMinimalTargetExponent - (w.e() + DiyFp.kSignificandSize);
        final int ten_mk_maximal_binary_exponent =
                kMaximalTargetExponent - (w.e() + DiyFp.kSignificandSize);
        mk = CachedPowers.getCachedPowerForBinaryExponentRange(
                ten_mk_minimal_binary_exponent,
                ten_mk_maximal_binary_exponent,
           ten_mk);
        assert(kMinimalTargetExponent <= w.e() + ten_mk.e() +
                DiyFp.kSignificandSize &&
                kMaximalTargetExponent >= w.e() + ten_mk.e() +
                        DiyFp.kSignificandSize);
        // Note that ten_mk is only an approximation of 10^-k. A DiyFp only contains a
        // 64 bit significand and ten_mk is thus only precise up to 64 bits.

        // The DiyFp::Times procedure rounds its result, and ten_mk is approximated
        // too. The variable scaled_w (as well as scaled_boundary_minus/plus) are now
        // off by a small amount.
        // In fact: scaled_w - w*10^k < 1ulp (unit in the last place) of scaled_w.
        // In other words: let f = scaled_w.f() and e = scaled_w.e(), then
        //           (f-1) * 2^e < w*10^k < (f+1) * 2^e
        final DiyFp scaled_w = DiyFp.times(w, ten_mk);
        assert(scaled_w.e() ==
                boundary_plus.e() + ten_mk.e() + DiyFp.kSignificandSize);
        // In theory it would be possible to avoid some recomputations by computing
        // the difference between w and boundary_minus/plus (a power of 2) and to
        // compute scaled_boundary_minus/plus by subtracting/adding from
        // scaled_w. However the code becomes much less readable and the speed
        // enhancements are not terriffic.
        final DiyFp scaled_boundary_minus = DiyFp.times(boundary_minus, ten_mk);
        final DiyFp scaled_boundary_plus  = DiyFp.times(boundary_plus,  ten_mk);

        // DigitGen will generate the digits of scaled_w. Therefore we have
        // v == (double) (scaled_w * 10^-mk).
        // Set decimal_exponent == -mk and pass it to DigitGen. If scaled_w is not an
        // integer than it will be updated. For instance if scaled_w == 1.23 then
        // the buffer will be filled with "123" und the decimal_exponent will be
        // decreased by 2.
        final boolean result = digitGen(scaled_boundary_minus, scaled_w, scaled_boundary_plus,
                buffer, mk);
        return result;
    }

    // The "counted" version of grisu3 (see above) only generates requested_digits
    // number of digits. This version does not generate the shortest representation,
    // and with enough requested digits 0.1 will at some point print as 0.9999999...
    // Grisu3 is too imprecise for real halfway cases (1.5 will not work) and
    // therefore the rounding strategy for halfway cases is irrelevant.
    static boolean grisu3Counted(final double v,
                                 final int requested_digits,
                                 final DtoaBuffer buffer) {
        final long d64 = IeeeDouble.doubleToLong(v);
        final DiyFp w = IeeeDouble.asNormalizedDiyFp(d64);
        final DiyFp ten_mk = new DiyFp();  // Cached power of ten: 10^-k
        final int mk;                      // -k
        final int ten_mk_minimal_binary_exponent =
                kMinimalTargetExponent - (w.e() + DiyFp.kSignificandSize);
        final int ten_mk_maximal_binary_exponent =
                kMaximalTargetExponent - (w.e() + DiyFp.kSignificandSize);
        mk = CachedPowers.getCachedPowerForBinaryExponentRange(
                ten_mk_minimal_binary_exponent,
                ten_mk_maximal_binary_exponent,
                ten_mk);
        assert ((kMinimalTargetExponent <= w.e() + ten_mk.e() +
                DiyFp.kSignificandSize) &&
                (kMaximalTargetExponent >= w.e() + ten_mk.e() +
                        DiyFp.kSignificandSize));
        // Note that ten_mk is only an approximation of 10^-k. A DiyFp only contains a
        // 64 bit significand and ten_mk is thus only precise up to 64 bits.

        // The DiyFp::Times procedure rounds its result, and ten_mk is approximated
        // too. The variable scaled_w (as well as scaled_boundary_minus/plus) are now
        // off by a small amount.
        // In fact: scaled_w - w*10^k < 1ulp (unit in the last place) of scaled_w.
        // In other words: let f = scaled_w.f() and e = scaled_w.e(), then
        //           (f-1) * 2^e < w*10^k < (f+1) * 2^e
        final DiyFp scaled_w = DiyFp.times(w, ten_mk);

        // We now have (double) (scaled_w * 10^-mk).
        // DigitGen will generate the first requested_digits digits of scaled_w and
        // return together with a kappa such that scaled_w ~= buffer * 10^kappa. (It
        // will not always be exactly the same since DigitGenCounted only produces a
        // limited number of digits.)
        final boolean result = digitGenCounted(scaled_w, requested_digits,
                buffer, mk);
        return result;
    }

}