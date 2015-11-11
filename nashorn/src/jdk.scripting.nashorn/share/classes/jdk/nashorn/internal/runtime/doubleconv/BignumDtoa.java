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

// Dtoa implementation based on our own Bignum implementation, supporting
// all conversion modes but slightly slower than the specialized implementations.
class BignumDtoa {

    private static int normalizedExponent(long significand, int exponent) {
        assert (significand != 0);
        while ((significand & IeeeDouble.kHiddenBit) == 0) {
            significand = significand << 1;
            exponent = exponent - 1;
        }
        return exponent;
    }

    // Converts the given double 'v' to ascii.
    // The result should be interpreted as buffer * 10^(point-length).
    // The buffer will be null-terminated.
    //
    // The input v must be > 0 and different from NaN, and Infinity.
    //
    // The output depends on the given mode:
    //  - SHORTEST: produce the least amount of digits for which the internal
    //   identity requirement is still satisfied. If the digits are printed
    //   (together with the correct exponent) then reading this number will give
    //   'v' again. The buffer will choose the representation that is closest to
    //   'v'. If there are two at the same distance, than the number is round up.
    //   In this mode the 'requested_digits' parameter is ignored.
    //  - FIXED: produces digits necessary to print a given number with
    //   'requested_digits' digits after the decimal point. The produced digits
    //   might be too short in which case the caller has to fill the gaps with '0's.
    //   Example: toFixed(0.001, 5) is allowed to return buffer="1", point=-2.
    //   Halfway cases are rounded up. The call toFixed(0.15, 2) thus returns
    //     buffer="2", point=0.
    //   Note: the length of the returned buffer has no meaning wrt the significance
    //   of its digits. That is, just because it contains '0's does not mean that
    //   any other digit would not satisfy the internal identity requirement.
    //  - PRECISION: produces 'requested_digits' where the first digit is not '0'.
    //   Even though the length of produced digits usually equals
    //   'requested_digits', the function is allowed to return fewer digits, in
    //   which case the caller has to fill the missing digits with '0's.
    //   Halfway cases are again rounded up.
    // 'BignumDtoa' expects the given buffer to be big enough to hold all digits
    // and a terminating null-character.
    static void bignumDtoa(final double v, final DtoaMode mode, final int requested_digits,
                    final DtoaBuffer buffer) {
        assert (v > 0);
        assert (!IeeeDouble.isSpecial(IeeeDouble.doubleToLong(v)));
        final long significand;
        final int exponent;
        final boolean lower_boundary_is_closer;

        final long l = IeeeDouble.doubleToLong(v);
        significand = IeeeDouble.significand(l);
        exponent = IeeeDouble.exponent(l);
        lower_boundary_is_closer = IeeeDouble.lowerBoundaryIsCloser(l);

        final boolean need_boundary_deltas = mode == DtoaMode.SHORTEST;

        final boolean is_even = (significand & 1) == 0;
        assert (significand != 0);
        final int normalizedExponent = normalizedExponent(significand, exponent);
        // estimated_power might be too low by 1.
        final int estimated_power = estimatePower(normalizedExponent);

        // Shortcut for Fixed.
        // The requested digits correspond to the digits after the point. If the
        // number is much too small, then there is no need in trying to get any
        // digits.
        if (mode == DtoaMode.FIXED && -estimated_power - 1 > requested_digits) {
            buffer.reset();
            // Set decimal-point to -requested_digits. This is what Gay does.
            // Note that it should not have any effect anyways since the string is
            // empty.
            buffer.decimalPoint = -requested_digits;
            return;
        }

        final Bignum numerator = new Bignum();
        final Bignum denominator = new Bignum();
        final Bignum delta_minus = new Bignum();
        final Bignum delta_plus = new Bignum();
        // Make sure the bignum can grow large enough. The smallest double equals
        // 4e-324. In this case the denominator needs fewer than 324*4 binary digits.
        // The maximum double is 1.7976931348623157e308 which needs fewer than
        // 308*4 binary digits.
        assert (Bignum.kMaxSignificantBits >= 324*4);
        initialScaledStartValues(significand, exponent, lower_boundary_is_closer,
                estimated_power, need_boundary_deltas,
                numerator, denominator,
                delta_minus, delta_plus);
        // We now have v = (numerator / denominator) * 10^estimated_power.
        buffer.decimalPoint = fixupMultiply10(estimated_power, is_even,
                numerator, denominator,
                delta_minus, delta_plus);
        // We now have v = (numerator / denominator) * 10^(decimal_point-1), and
        //  1 <= (numerator + delta_plus) / denominator < 10
        switch (mode) {
            case SHORTEST:
                generateShortestDigits(numerator, denominator,
                        delta_minus, delta_plus,
                        is_even, buffer);
                break;
            case FIXED:
                bignumToFixed(requested_digits,
                        numerator, denominator,
                        buffer);
                break;
            case PRECISION:
                generateCountedDigits(requested_digits,
                        numerator, denominator,
                        buffer);
                break;
            default:
                throw new RuntimeException();
        }
    }


    // The procedure starts generating digits from the left to the right and stops
    // when the generated digits yield the shortest decimal representation of v. A
    // decimal representation of v is a number lying closer to v than to any other
    // double, so it converts to v when read.
    //
    // This is true if d, the decimal representation, is between m- and m+, the
    // upper and lower boundaries. d must be strictly between them if !is_even.
    //           m- := (numerator - delta_minus) / denominator
    //           m+ := (numerator + delta_plus) / denominator
    //
    // Precondition: 0 <= (numerator+delta_plus) / denominator < 10.
    //   If 1 <= (numerator+delta_plus) / denominator < 10 then no leading 0 digit
    //   will be produced. This should be the standard precondition.
    static void generateShortestDigits(final Bignum numerator, final Bignum denominator,
                                       final Bignum delta_minus, Bignum delta_plus,
                                       final boolean is_even,
                                       final DtoaBuffer buffer) {
        // Small optimization: if delta_minus and delta_plus are the same just reuse
        // one of the two bignums.
        if (Bignum.equal(delta_minus, delta_plus)) {
            delta_plus = delta_minus;
        }
        for (;;) {
            final char digit;
            digit = numerator.divideModuloIntBignum(denominator);
            assert (digit <= 9);  // digit is a uint16_t and therefore always positive.
            // digit = numerator / denominator (integer division).
            // numerator = numerator % denominator.
            buffer.append((char) (digit + '0'));

            // Can we stop already?
            // If the remainder of the division is less than the distance to the lower
            // boundary we can stop. In this case we simply round down (discarding the
            // remainder).
            // Similarly we test if we can round up (using the upper boundary).
            final boolean in_delta_room_minus;
            final boolean in_delta_room_plus;
            if (is_even) {
                in_delta_room_minus = Bignum.lessEqual(numerator, delta_minus);
            } else {
                in_delta_room_minus = Bignum.less(numerator, delta_minus);
            }
            if (is_even) {
                in_delta_room_plus =
                        Bignum.plusCompare(numerator, delta_plus, denominator) >= 0;
            } else {
                in_delta_room_plus =
                        Bignum.plusCompare(numerator, delta_plus, denominator) > 0;
            }
            if (!in_delta_room_minus && !in_delta_room_plus) {
                // Prepare for next iteration.
                numerator.times10();
                delta_minus.times10();
                // We optimized delta_plus to be equal to delta_minus (if they share the
                // same value). So don't multiply delta_plus if they point to the same
                // object.
                if (delta_minus != delta_plus) {
                    delta_plus.times10();
                }
            } else if (in_delta_room_minus && in_delta_room_plus) {
                // Let's see if 2*numerator < denominator.
                // If yes, then the next digit would be < 5 and we can round down.
                final int compare = Bignum.plusCompare(numerator, numerator, denominator);
                if (compare < 0) {
                    // Remaining digits are less than .5. -> Round down (== do nothing).
                } else if (compare > 0) {
                    // Remaining digits are more than .5 of denominator. -> Round up.
                    // Note that the last digit could not be a '9' as otherwise the whole
                    // loop would have stopped earlier.
                    // We still have an assert here in case the preconditions were not
                    // satisfied.
                    assert (buffer.chars[buffer.length - 1] != '9');
                    buffer.chars[buffer.length - 1]++;
                } else {
                    // Halfway case.
                    // TODO(floitsch): need a way to solve half-way cases.
                    //   For now let's round towards even (since this is what Gay seems to
                    //   do).

                    if ((buffer.chars[buffer.length - 1] - '0') % 2 == 0) {
                        // Round down => Do nothing.
                    } else {
                        assert (buffer.chars[buffer.length - 1] != '9');
                        buffer.chars[buffer.length - 1]++;
                    }
                }
                return;
            } else if (in_delta_room_minus) {
                // Round down (== do nothing).
                return;
            } else {  // in_delta_room_plus
                // Round up.
                // Note again that the last digit could not be '9' since this would have
                // stopped the loop earlier.
                // We still have an ASSERT here, in case the preconditions were not
                // satisfied.
                assert (buffer.chars[buffer.length -1] != '9');
                buffer.chars[buffer.length - 1]++;
                return;
            }
        }
    }


    // Let v = numerator / denominator < 10.
    // Then we generate 'count' digits of d = x.xxxxx... (without the decimal point)
    // from left to right. Once 'count' digits have been produced we decide wether
    // to round up or down. Remainders of exactly .5 round upwards. Numbers such
    // as 9.999999 propagate a carry all the way, and change the
    // exponent (decimal_point), when rounding upwards.
    static void generateCountedDigits(final int count,
                                      final Bignum numerator, final Bignum denominator,
                                      final DtoaBuffer buffer) {
        assert (count >= 0);
        for (int i = 0; i < count - 1; ++i) {
            final char digit;
            digit = numerator.divideModuloIntBignum(denominator);
            assert (digit <= 9);  // digit is a uint16_t and therefore always positive.
            // digit = numerator / denominator (integer division).
            // numerator = numerator % denominator.
            buffer.chars[i] = (char)(digit + '0');
            // Prepare for next iteration.
            numerator.times10();
        }
        // Generate the last digit.
        char digit;
        digit = numerator.divideModuloIntBignum(denominator);
        if (Bignum.plusCompare(numerator, numerator, denominator) >= 0) {
            digit++;
        }
        assert (digit <= 10);
        buffer.chars[count - 1] = (char) (digit + '0');
        // Correct bad digits (in case we had a sequence of '9's). Propagate the
        // carry until we hat a non-'9' or til we reach the first digit.
        for (int i = count - 1; i > 0; --i) {
            if (buffer.chars[i] != '0' + 10) break;
            buffer.chars[i] = '0';
            buffer.chars[i - 1]++;
        }
        if (buffer.chars[0] == '0' + 10) {
            // Propagate a carry past the top place.
            buffer.chars[0] = '1';
            buffer.decimalPoint++;
        }
        buffer.length = count;
    }


    // Generates 'requested_digits' after the decimal point. It might omit
    // trailing '0's. If the input number is too small then no digits at all are
    // generated (ex.: 2 fixed digits for 0.00001).
    //
    // Input verifies:  1 <= (numerator + delta) / denominator < 10.
    static void bignumToFixed(final int requested_digits,
                              final Bignum numerator, final Bignum denominator,
                              final DtoaBuffer buffer) {
        // Note that we have to look at more than just the requested_digits, since
        // a number could be rounded up. Example: v=0.5 with requested_digits=0.
        // Even though the power of v equals 0 we can't just stop here.
        if (-buffer.decimalPoint > requested_digits) {
            // The number is definitively too small.
            // Ex: 0.001 with requested_digits == 1.
            // Set decimal-decimalPoint to -requested_digits. This is what Gay does.
            // Note that it should not have any effect anyways since the string is
            // empty.
            buffer.decimalPoint = -requested_digits;
            buffer.length = 0;
            // return;
        } else if (-buffer.decimalPoint == requested_digits) {
            // We only need to verify if the number rounds down or up.
            // Ex: 0.04 and 0.06 with requested_digits == 1.
            assert (buffer.decimalPoint == -requested_digits);
            // Initially the fraction lies in range (1, 10]. Multiply the denominator
            // by 10 so that we can compare more easily.
            denominator.times10();
            if (Bignum.plusCompare(numerator, numerator, denominator) >= 0) {
                // If the fraction is >= 0.5 then we have to include the rounded
                // digit.
                buffer.chars[0] = '1';
                buffer.length = 1;
                buffer.decimalPoint++;
            } else {
                // Note that we caught most of similar cases earlier.
                buffer.length = 0;
            }
            // return;
        } else {
            // The requested digits correspond to the digits after the point.
            // The variable 'needed_digits' includes the digits before the point.
            final int needed_digits = buffer.decimalPoint + requested_digits;
            generateCountedDigits(needed_digits,
                    numerator, denominator,
                    buffer);
        }
    }


    // Returns an estimation of k such that 10^(k-1) <= v < 10^k where
    // v = f * 2^exponent and 2^52 <= f < 2^53.
    // v is hence a normalized double with the given exponent. The output is an
    // approximation for the exponent of the decimal approimation .digits * 10^k.
    //
    // The result might undershoot by 1 in which case 10^k <= v < 10^k+1.
    // Note: this property holds for v's upper boundary m+ too.
    //    10^k <= m+ < 10^k+1.
    //   (see explanation below).
    //
    // Examples:
    //  EstimatePower(0)   => 16
    //  EstimatePower(-52) => 0
    //
    // Note: e >= 0 => EstimatedPower(e) > 0. No similar claim can be made for e<0.
    static int estimatePower(final int exponent) {
        // This function estimates log10 of v where v = f*2^e (with e == exponent).
        // Note that 10^floor(log10(v)) <= v, but v <= 10^ceil(log10(v)).
        // Note that f is bounded by its container size. Let p = 53 (the double's
        // significand size). Then 2^(p-1) <= f < 2^p.
        //
        // Given that log10(v) == log2(v)/log2(10) and e+(len(f)-1) is quite close
        // to log2(v) the function is simplified to (e+(len(f)-1)/log2(10)).
        // The computed number undershoots by less than 0.631 (when we compute log3
        // and not log10).
        //
        // Optimization: since we only need an approximated result this computation
        // can be performed on 64 bit integers. On x86/x64 architecture the speedup is
        // not really measurable, though.
        //
        // Since we want to avoid overshooting we decrement by 1e10 so that
        // floating-point imprecisions don't affect us.
        //
        // Explanation for v's boundary m+: the computation takes advantage of
        // the fact that 2^(p-1) <= f < 2^p. Boundaries still satisfy this requirement
        // (even for denormals where the delta can be much more important).

        final double k1Log10 = 0.30102999566398114;  // 1/lg(10)

        // For doubles len(f) == 53 (don't forget the hidden bit).
        final int kSignificandSize = IeeeDouble.kSignificandSize;
        final double estimate = Math.ceil((exponent + kSignificandSize - 1) * k1Log10 - 1e-10);
        return (int) estimate;
    }


    // See comments for InitialScaledStartValues.
    static void initialScaledStartValuesPositiveExponent(
            final long significand, final int exponent,
            final int estimated_power, final boolean need_boundary_deltas,
            final Bignum numerator, final Bignum denominator,
            final Bignum delta_minus, final Bignum delta_plus) {
        // A positive exponent implies a positive power.
        assert (estimated_power >= 0);
        // Since the estimated_power is positive we simply multiply the denominator
        // by 10^estimated_power.

        // numerator = v.
        numerator.assignUInt64(significand);
        numerator.shiftLeft(exponent);
        // denominator = 10^estimated_power.
        denominator.assignPowerUInt16(10, estimated_power);

        if (need_boundary_deltas) {
            // Introduce a common denominator so that the deltas to the boundaries are
            // integers.
            denominator.shiftLeft(1);
            numerator.shiftLeft(1);
            // Let v = f * 2^e, then m+ - v = 1/2 * 2^e; With the common
            // denominator (of 2) delta_plus equals 2^e.
            delta_plus.assignUInt16((char) 1);
            delta_plus.shiftLeft(exponent);
            // Same for delta_minus. The adjustments if f == 2^p-1 are done later.
            delta_minus.assignUInt16((char) 1);
            delta_minus.shiftLeft(exponent);
        }
    }


    // See comments for InitialScaledStartValues
    static void initialScaledStartValuesNegativeExponentPositivePower(
            final long significand, final int exponent,
            final int estimated_power, final boolean need_boundary_deltas,
            final Bignum numerator, final Bignum denominator,
            final Bignum delta_minus, final Bignum delta_plus) {
        // v = f * 2^e with e < 0, and with estimated_power >= 0.
        // This means that e is close to 0 (have a look at how estimated_power is
        // computed).

        // numerator = significand
        //  since v = significand * 2^exponent this is equivalent to
        //  numerator = v * / 2^-exponent
        numerator.assignUInt64(significand);
        // denominator = 10^estimated_power * 2^-exponent (with exponent < 0)
        denominator.assignPowerUInt16(10, estimated_power);
        denominator.shiftLeft(-exponent);

        if (need_boundary_deltas) {
            // Introduce a common denominator so that the deltas to the boundaries are
            // integers.
            denominator.shiftLeft(1);
            numerator.shiftLeft(1);
            // Let v = f * 2^e, then m+ - v = 1/2 * 2^e; With the common
            // denominator (of 2) delta_plus equals 2^e.
            // Given that the denominator already includes v's exponent the distance
            // to the boundaries is simply 1.
            delta_plus.assignUInt16((char) 1);
            // Same for delta_minus. The adjustments if f == 2^p-1 are done later.
            delta_minus.assignUInt16((char) 1);
        }
    }


    // See comments for InitialScaledStartValues
    static void initialScaledStartValuesNegativeExponentNegativePower(
            final long significand, final int exponent,
            final int estimated_power, final boolean need_boundary_deltas,
            final Bignum numerator, final Bignum denominator,
            final Bignum delta_minus, final Bignum delta_plus) {
        // Instead of multiplying the denominator with 10^estimated_power we
        // multiply all values (numerator and deltas) by 10^-estimated_power.

        // Use numerator as temporary container for power_ten.
        final Bignum power_ten = numerator;
        power_ten.assignPowerUInt16(10, -estimated_power);

        if (need_boundary_deltas) {
            // Since power_ten == numerator we must make a copy of 10^estimated_power
            // before we complete the computation of the numerator.
            // delta_plus = delta_minus = 10^estimated_power
            delta_plus.assignBignum(power_ten);
            delta_minus.assignBignum(power_ten);
        }

        // numerator = significand * 2 * 10^-estimated_power
        //  since v = significand * 2^exponent this is equivalent to
        // numerator = v * 10^-estimated_power * 2 * 2^-exponent.
        // Remember: numerator has been abused as power_ten. So no need to assign it
        //  to itself.
        assert (numerator == power_ten);
        numerator.multiplyByUInt64(significand);

        // denominator = 2 * 2^-exponent with exponent < 0.
        denominator.assignUInt16((char) 1);
        denominator.shiftLeft(-exponent);

        if (need_boundary_deltas) {
            // Introduce a common denominator so that the deltas to the boundaries are
            // integers.
            numerator.shiftLeft(1);
            denominator.shiftLeft(1);
            // With this shift the boundaries have their correct value, since
            // delta_plus = 10^-estimated_power, and
            // delta_minus = 10^-estimated_power.
            // These assignments have been done earlier.
            // The adjustments if f == 2^p-1 (lower boundary is closer) are done later.
        }
    }


    // Let v = significand * 2^exponent.
    // Computes v / 10^estimated_power exactly, as a ratio of two bignums, numerator
    // and denominator. The functions GenerateShortestDigits and
    // GenerateCountedDigits will then convert this ratio to its decimal
    // representation d, with the required accuracy.
    // Then d * 10^estimated_power is the representation of v.
    // (Note: the fraction and the estimated_power might get adjusted before
    // generating the decimal representation.)
    //
    // The initial start values consist of:
    //  - a scaled numerator: s.t. numerator/denominator == v / 10^estimated_power.
    //  - a scaled (common) denominator.
    //  optionally (used by GenerateShortestDigits to decide if it has the shortest
    //  decimal converting back to v):
    //  - v - m-: the distance to the lower boundary.
    //  - m+ - v: the distance to the upper boundary.
    //
    // v, m+, m-, and therefore v - m- and m+ - v all share the same denominator.
    //
    // Let ep == estimated_power, then the returned values will satisfy:
    //  v / 10^ep = numerator / denominator.
    //  v's boundarys m- and m+:
    //    m- / 10^ep == v / 10^ep - delta_minus / denominator
    //    m+ / 10^ep == v / 10^ep + delta_plus / denominator
    //  Or in other words:
    //    m- == v - delta_minus * 10^ep / denominator;
    //    m+ == v + delta_plus * 10^ep / denominator;
    //
    // Since 10^(k-1) <= v < 10^k    (with k == estimated_power)
    //  or       10^k <= v < 10^(k+1)
    //  we then have 0.1 <= numerator/denominator < 1
    //           or    1 <= numerator/denominator < 10
    //
    // It is then easy to kickstart the digit-generation routine.
    //
    // The boundary-deltas are only filled if the mode equals BIGNUM_DTOA_SHORTEST
    // or BIGNUM_DTOA_SHORTEST_SINGLE.

    static void initialScaledStartValues(final long significand,
                                         final int exponent,
                                         final boolean lower_boundary_is_closer,
                                         final int estimated_power,
                                         final boolean need_boundary_deltas,
                                         final Bignum numerator,
                                         final Bignum denominator,
                                         final Bignum delta_minus,
                                         final Bignum delta_plus) {
        if (exponent >= 0) {
            initialScaledStartValuesPositiveExponent(
                    significand, exponent, estimated_power, need_boundary_deltas,
                    numerator, denominator, delta_minus, delta_plus);
        } else if (estimated_power >= 0) {
            initialScaledStartValuesNegativeExponentPositivePower(
                    significand, exponent, estimated_power, need_boundary_deltas,
                    numerator, denominator, delta_minus, delta_plus);
        } else {
            initialScaledStartValuesNegativeExponentNegativePower(
                    significand, exponent, estimated_power, need_boundary_deltas,
                    numerator, denominator, delta_minus, delta_plus);
        }

        if (need_boundary_deltas && lower_boundary_is_closer) {
            // The lower boundary is closer at half the distance of "normal" numbers.
            // Increase the common denominator and adapt all but the delta_minus.
            denominator.shiftLeft(1);  // *2
            numerator.shiftLeft(1);    // *2
            delta_plus.shiftLeft(1);   // *2
        }
    }


    // This routine multiplies numerator/denominator so that its values lies in the
    // range 1-10. That is after a call to this function we have:
    //    1 <= (numerator + delta_plus) /denominator < 10.
    // Let numerator the input before modification and numerator' the argument
    // after modification, then the output-parameter decimal_point is such that
    //  numerator / denominator * 10^estimated_power ==
    //    numerator' / denominator' * 10^(decimal_point - 1)
    // In some cases estimated_power was too low, and this is already the case. We
    // then simply adjust the power so that 10^(k-1) <= v < 10^k (with k ==
    // estimated_power) but do not touch the numerator or denominator.
    // Otherwise the routine multiplies the numerator and the deltas by 10.
    static int fixupMultiply10(final int estimated_power, final boolean is_even,
                                final Bignum numerator, final Bignum denominator,
                                final Bignum delta_minus, final Bignum delta_plus) {
        final boolean in_range;
        final int decimal_point;
        if (is_even) {
            // For IEEE doubles half-way cases (in decimal system numbers ending with 5)
            // are rounded to the closest floating-point number with even significand.
            in_range = Bignum.plusCompare(numerator, delta_plus, denominator) >= 0;
        } else {
            in_range = Bignum.plusCompare(numerator, delta_plus, denominator) > 0;
        }
        if (in_range) {
            // Since numerator + delta_plus >= denominator we already have
            // 1 <= numerator/denominator < 10. Simply update the estimated_power.
            decimal_point = estimated_power + 1;
        } else {
            decimal_point = estimated_power;
            numerator.times10();
            if (Bignum.equal(delta_minus, delta_plus)) {
                delta_minus.times10();
                delta_plus.assignBignum(delta_minus);
            } else {
                delta_minus.times10();
                delta_plus.times10();
            }
        }
        return decimal_point;
    }


}
