/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.math;

import jdk.internal.vm.annotation.Stable;

import static jdk.internal.math.FDBigInteger.valueOfMulPow52;
import static jdk.internal.math.FDBigInteger.valueOfPow52;

/**
 * A class for converting between ASCII and decimal representations of a single
 * or double precision floating point number. Most conversions are provided via
 * static convenience methods, although a {@link BinaryToASCIIBuffer}
 * instance may be obtained.
 */
public final class FloatingDecimal {
    //
    // Constants of the implementation;
    // most are IEEE-754 related.
    //
    private static final int    EXP_SHIFT = DoubleConsts.SIGNIFICAND_WIDTH - 1;
    private static final long   FRACT_HOB = 1L << EXP_SHIFT; // assumed High-Order bit
    private static final long   EXP_ONE   = (long) DoubleConsts.EXP_BIAS << EXP_SHIFT; // exponent of 1.0
    private static final int    MAX_SMALL_BIN_EXP = 62;
    private static final int    MIN_SMALL_BIN_EXP = -63 / 3;
    private static final int    MAX_DEC_DIGITS = 15;  // max{n : 10^n ≤ 2^P}
    private static final int    FLOG_10_MAX_LONG = 18;  // max{i : 10^i ≤ Long.MAX_VALUE}

    /**
     * Converts a {@link String} to a double precision floating point value.
     *
     * @param s The {@link String} to convert.
     * @return The double precision value.
     * @throws NumberFormatException If the {@link String} does not
     * represent a properly formatted double precision value.
     */
    public static double parseDouble(String s) throws NumberFormatException {
        return readJavaFormatString(s, BINARY_64_IX);
    }

    /**
     * Converts a {@link String} to a single precision floating point value.
     *
     * @param s The {@link String} to convert.
     * @return The single precision value.
     * @throws NumberFormatException If the {@link String} does not
     * represent a properly formatted single precision value.
     */
    public static float parseFloat(String s) throws NumberFormatException {
        return (float) readJavaFormatString(s, BINARY_32_IX);
    }

    /**
     * Converts a sequence of digits ('0'-'9') as well as an exponent to a positive
     * double value
     *
     * @param decExp The decimal exponent of the value to generate
     * @param d The digits of the significand.
     * @param length Number of digits to use
     * @return The double-precision value of the conversion
     */
    public static double parseDoubleSignlessDigits(int decExp, byte[] d, int length) {
        return new ASCIIToBinaryBuffer(false, decExp, d, length).doubleValue();
    }

    private static final String INFINITY_REP = "Infinity";
    private static final String NAN_REP = "NaN";

    private static final BinaryToASCIIBuffer B2AC_POSITIVE_ZERO =
            new BinaryToASCIIBuffer(new byte[] {'0'});
    private static final BinaryToASCIIBuffer B2AC_NEGATIVE_ZERO =
            new BinaryToASCIIBuffer(new byte[] {'0'});

    public static final class BinaryToASCIIBuffer {
        private int decExponent;
        private int firstDigitIndex;
        private int nDigits;
        private final byte[] digits;

        //
        // The fields below provide additional information about the result of
        // the binary to decimal digits conversion done in dtoa() and roundup()
        // methods. They are changed if needed by those two methods.
        //

        // True if the dtoa() binary to decimal conversion was exact.
        private boolean exactDecimalConversion = false;

        // True if the result of the binary to decimal conversion was rounded-up
        // at the end of the conversion process, i.e. roundUp() method was called.
        private boolean decimalDigitsRoundedUp = false;

        /**
         * Default constructor; used for non-zero values,
         * {@link BinaryToASCIIBuffer} may be thread-local and reused
         */
        private BinaryToASCIIBuffer() {
            this.digits = new byte[20];
        }

        /**
         * Creates a specialized value (positive and negative zeros).
         */
        private BinaryToASCIIBuffer(byte[] digits){
            this.decExponent  = 0;
            this.digits = digits;
            this.firstDigitIndex = 0;
            this.nDigits = digits.length;
        }

        public int getDecimalExponent() {
            return decExponent;
        }

        public int getDigits(byte[] digits) {
            System.arraycopy(this.digits, firstDigitIndex, digits, 0, this.nDigits);
            return this.nDigits;
        }

        public boolean digitsRoundedUp() {
            return decimalDigitsRoundedUp;
        }

        public boolean decimalDigitsExact() {
            return exactDecimalConversion;
        }

        /**
         * This is the easy subcase --
         * all the significant bits, after scaling, are held in lvalue.
         * negSign and decExponent tell us what processing and scaling
         * has already been done. Exceptional cases have already been
         * stripped out.
         * In particular:
         * lvalue is a finite number (not Inf, nor NaN)
         * lvalue > 0L (not zero, nor negative).
         *<p>
         * The only reason that we develop the digits here, rather than
         * calling on Long.toString() is that we can do it a little faster,
         * and besides want to treat trailing 0s specially. If Long.toString
         * changes, we should re-evaluate this strategy!
         */
        private void developLongDigits( long lvalue, int insignificantDigits ){
            int decExponent = 0;
            if ( insignificantDigits != 0 ){
                // Discard non-significant low-order bits, while rounding,
                // up to insignificant value.
                long pow10 = FDBigInteger.LONG_5_POW[insignificantDigits] << insignificantDigits; // 10^i == 5^i * 2^i;
                long residue = lvalue % pow10;
                lvalue /= pow10;
                decExponent += insignificantDigits;
                if ( residue >= (pow10>>1) ){
                    // round up based on the low-order bits we're discarding
                    lvalue++;
                }
            }
            int  digitno = digits.length -1;
            int  c;
            if ( lvalue <= Integer.MAX_VALUE ){
                assert lvalue > 0L : lvalue; // lvalue <= 0
                // even easier subcase!
                // can do int arithmetic rather than long!
                int  ivalue = (int)lvalue;
                c = ivalue%10;
                ivalue /= 10;
                while ( c == 0 ){
                    decExponent++;
                    c = ivalue%10;
                    ivalue /= 10;
                }
                while ( ivalue != 0){
                    digits[digitno--] = (byte)(c+'0');
                    decExponent++;
                    c = ivalue%10;
                    ivalue /= 10;
                }
                digits[digitno] = (byte)(c+'0');
            } else {
                // same algorithm as above (same bugs, too )
                // but using long arithmetic.
                c = (int)(lvalue%10L);
                lvalue /= 10L;
                while ( c == 0 ){
                    decExponent++;
                    c = (int)(lvalue%10L);
                    lvalue /= 10L;
                }
                while ( lvalue != 0L ){
                    digits[digitno--] = (byte) (c+'0');
                    decExponent++;
                    c = (int)(lvalue%10L);
                    lvalue /= 10;
                }
                digits[digitno] = (byte)(c+'0');
            }
            this.decExponent = decExponent+1;
            this.firstDigitIndex = digitno;
            this.nDigits = this.digits.length - digitno;
        }

        private void dtoa( int binExp, long fractBits, int nSignificantBits)
        {
            assert fractBits > 0 ; // fractBits here can't be zero or negative
            assert (fractBits & FRACT_HOB)!=0  ; // Hi-order bit should be set
            // Examine number. Determine if it is an easy case,
            // which we can do pretty trivially using float/long conversion,
            // or whether we must do real work.
            final int tailZeros = Long.numberOfTrailingZeros(fractBits);

            // number of significant bits of fractBits;
            final int nFractBits = EXP_SHIFT+1-tailZeros;

            // reset flags to default values as dtoa() does not always set these
            // flags and a prior call to dtoa() might have set them to incorrect
            // values with respect to the current state.
            decimalDigitsRoundedUp = false;
            exactDecimalConversion = false;

            // number of significant bits to the right of the point.
            int nTinyBits = Math.max( 0, nFractBits - binExp - 1 );
            if ( binExp <= MAX_SMALL_BIN_EXP && binExp >= MIN_SMALL_BIN_EXP ){
                // Look more closely at the number to decide if,
                // with scaling by 10^nTinyBits, the result will fit in
                // a long.
                if ( (nTinyBits < FDBigInteger.LONG_5_POW.length) && ((nFractBits + N_5_BITS[nTinyBits]) < 64 ) ){
                    //
                    // We can do this:
                    // take the fraction bits, which are normalized.
                    // (a) nTinyBits == 0: Shift left or right appropriately
                    //     to align the binary point at the extreme right, i.e.
                    //     where a long int point is expected to be. The integer
                    //     result is easily converted to a string.
                    // (b) nTinyBits > 0: Shift right by EXP_SHIFT-nFractBits,
                    //     which effectively converts to long and scales by
                    //     2^nTinyBits. Then multiply by 5^nTinyBits to
                    //     complete the scaling. We know this won't overflow
                    //     because we just counted the number of bits necessary
                    //     in the result. The integer you get from this can
                    //     then be converted to a string pretty easily.
                    //
                    if ( nTinyBits == 0 ) {
                        int insignificant;
                        if ( binExp > nSignificantBits ){
                            insignificant = insignificantDigitsForPow2(binExp-nSignificantBits-1);
                        } else {
                            insignificant = 0;
                        }
                        if ( binExp >= EXP_SHIFT ){
                            fractBits <<= (binExp-EXP_SHIFT);
                        } else {
                            fractBits >>>= (EXP_SHIFT-binExp) ;
                        }
                        developLongDigits( fractBits, insignificant );
                        return;
                    }
                    //
                    // The following causes excess digits to be printed
                    // out in the single-float case. Our manipulation of
                    // halfULP here is apparently not correct. If we
                    // better understand how this works, perhaps we can
                    // use this special case again. But for the time being,
                    // we do not.
                    // else {
                    //     fractBits >>>= EXP_SHIFT+1-nFractBits;
                    //     fractBits//= long5pow[ nTinyBits ];
                    //     halfULP = long5pow[ nTinyBits ] >> (1+nSignificantBits-nFractBits);
                    //     developLongDigits( -nTinyBits, fractBits, insignificantDigits(halfULP) );
                    //     return;
                    // }
                    //
                }
            }
            //
            // This is the hard case. We are going to compute large positive
            // integers B and S and integer decExp, s.t.
            //      d = ( B / S )// 10^decExp
            //      1 <= B / S < 10
            // Obvious choices are:
            //      decExp = floor( log10(d) )
            //      B      = d// 2^nTinyBits// 10^max( 0, -decExp )
            //      S      = 10^max( 0, decExp)// 2^nTinyBits
            // (noting that nTinyBits has already been forced to non-negative)
            // I am also going to compute a large positive integer
            //      M      = (1/2^nSignificantBits)// 2^nTinyBits// 10^max( 0, -decExp )
            // i.e. M is (1/2) of the ULP of d, scaled like B.
            // When we iterate through dividing B/S and picking off the
            // quotient bits, we will know when to stop when the remainder
            // is <= M.
            //
            // We keep track of powers of 2 and powers of 5.
            //
            int decExp = estimateDecExp(fractBits,binExp);
            int B2, B5; // powers of 2 and powers of 5, respectively, in B
            int S2, S5; // powers of 2 and powers of 5, respectively, in S
            int M2, M5; // powers of 2 and powers of 5, respectively, in M

            B5 = Math.max( 0, -decExp );
            B2 = B5 + nTinyBits + binExp;

            S5 = Math.max( 0, decExp );
            S2 = S5 + nTinyBits;

            M5 = B5;
            M2 = B2 - nSignificantBits;

            //
            // the long integer fractBits contains the (nFractBits) interesting
            // bits from the mantissa of d ( hidden 1 added if necessary) followed
            // by (EXP_SHIFT+1-nFractBits) zeros. In the interest of compactness,
            // I will shift out those zeros before turning fractBits into a
            // FDBigInteger. The resulting whole number will be
            //      d * 2^(nFractBits-1-binExp).
            //
            fractBits >>>= tailZeros;
            B2 -= nFractBits-1;
            int common2factor = Math.min( B2, S2 );
            B2 -= common2factor;
            S2 -= common2factor;
            M2 -= common2factor;

            //
            // HACK!! For exact powers of two, the next smallest number
            // is only half as far away as we think (because the meaning of
            // ULP changes at power-of-two bounds) for this reason, we
            // hack M2. Hope this works.
            //
            if ( nFractBits == 1 ) {
                M2 -= 1;
            }

            if ( M2 < 0 ){
                // oops.
                // since we cannot scale M down far enough,
                // we must scale the other values up.
                B2 -= M2;
                S2 -= M2;
                M2 =  0;
            }
            //
            // Construct, Scale, iterate.
            // Some day, we'll write a stopping test that takes
            // account of the asymmetry of the spacing of floating-point
            // numbers below perfect powers of 2
            // 26 Sept 96 is not that day.
            // So we use a symmetric test.
            //
            int ndigit;
            boolean low, high;
            long lowDigitDifference;
            int  q;

            //
            // Detect the special cases where all the numbers we are about
            // to compute will fit in int or long integers.
            // In these cases, we will avoid doing FDBigInteger arithmetic.
            // We use the same algorithms, except that we "normalize"
            // our FDBigIntegers before iterating. This is to make division easier,
            // as it makes our fist guess (quotient of high-order words)
            // more accurate!
            //
            // Some day, we'll write a stopping test that takes
            // account of the asymmetry of the spacing of floating-point
            // numbers below perfect powers of 2
            // 26 Sept 96 is not that day.
            // So we use a symmetric test.
            //
            // binary digits needed to represent B, approx.
            int Bbits = nFractBits + B2 + (( B5 < N_5_BITS.length )? N_5_BITS[B5] : ( B5*3 ));

            // binary digits needed to represent 10*S, approx.
            int tenSbits = S2+1 + (( (S5+1) < N_5_BITS.length )? N_5_BITS[(S5+1)] : ( (S5+1)*3 ));
            if ( Bbits < 64 && tenSbits < 64){
                if ( Bbits < 32 && tenSbits < 32){
                    // wa-hoo! They're all ints!
                    int b = ((int)fractBits * FDBigInteger.SMALL_5_POW[B5] ) << B2;
                    int s = FDBigInteger.SMALL_5_POW[S5] << S2;
                    int m = FDBigInteger.SMALL_5_POW[M5] << M2;
                    int tens = s * 10;
                    //
                    // Unroll the first iteration. If our decExp estimate
                    // was too high, our first quotient will be zero. In this
                    // case, we discard it and decrement decExp.
                    //
                    ndigit = 0;
                    q = b / s;
                    b = 10 * ( b % s );
                    m *= 10;
                    low  = (b <  m );
                    high = (b+m > tens );
                    assert q < 10 : q; // excessively large digit
                    if ( (q == 0) && ! high ){
                        // oops. Usually ignore leading zero.
                        decExp--;
                    } else {
                        digits[ndigit++] = (byte)('0' + q);
                    }
                    //
                    // HACK! Java spec sez that we always have at least
                    // one digit after the . in either F- or E-form output.
                    // Thus we will need more than one digit if we're using
                    // E-form
                    //
                    if (decExp < -3 || decExp >= 8){
                        high = low = false;
                    }
                    while( ! low && ! high ){
                        q = b / s;
                        b = 10 * ( b % s );
                        m *= 10;
                        assert q < 10 : q; // excessively large digit
                        if ( m > 0L ){
                            low  = (b <  m );
                            high = (b+m > tens );
                        } else {
                            // hack -- m might overflow!
                            // in this case, it is certainly > b,
                            // which won't
                            // and b+m > tens, too, since that has overflowed
                            // either!
                            low = true;
                            high = true;
                        }
                        digits[ndigit++] = (byte)('0' + q);
                    }
                    lowDigitDifference = (b<<1) - tens;
                    exactDecimalConversion  = (b == 0);
                } else {
                    // still good! they're all longs!
                    long b = (fractBits * FDBigInteger.LONG_5_POW[B5] ) << B2;
                    long s = FDBigInteger.LONG_5_POW[S5] << S2;
                    long m = FDBigInteger.LONG_5_POW[M5] << M2;
                    long tens = s * 10L;
                    //
                    // Unroll the first iteration. If our decExp estimate
                    // was too high, our first quotient will be zero. In this
                    // case, we discard it and decrement decExp.
                    //
                    ndigit = 0;
                    q = (int) ( b / s );
                    b = 10L * ( b % s );
                    m *= 10L;
                    low  = (b <  m );
                    high = (b+m > tens );
                    assert q < 10 : q; // excessively large digit
                    if ( (q == 0) && ! high ){
                        // oops. Usually ignore leading zero.
                        decExp--;
                    } else {
                        digits[ndigit++] = (byte)('0' + q);
                    }
                    //
                    // HACK! Java spec sez that we always have at least
                    // one digit after the . in either F- or E-form output.
                    // Thus we will need more than one digit if we're using
                    // E-form
                    //
                    if (decExp < -3 || decExp >= 8){
                        high = low = false;
                    }
                    while( ! low && ! high ){
                        q = (int) ( b / s );
                        b = 10 * ( b % s );
                        m *= 10;
                        assert q < 10 : q;  // excessively large digit
                        if ( m > 0L ){
                            low  = (b <  m );
                            high = (b+m > tens );
                        } else {
                            // hack -- m might overflow!
                            // in this case, it is certainly > b,
                            // which won't
                            // and b+m > tens, too, since that has overflowed
                            // either!
                            low = true;
                            high = true;
                        }
                        digits[ndigit++] = (byte)('0' + q);
                    }
                    lowDigitDifference = (b<<1) - tens;
                    exactDecimalConversion  = (b == 0);
                }
            } else {
                //
                // We really must do FDBigInteger arithmetic.
                // Fist, construct our FDBigInteger initial values.
                //
                FDBigInteger Sval = valueOfPow52(S5, S2);
                int shiftBias = Sval.getNormalizationBias();
                Sval = Sval.leftShift(shiftBias); // normalize so that division works better

                FDBigInteger Bval = valueOfMulPow52(fractBits, B5, B2 + shiftBias);
                FDBigInteger Mval = valueOfPow52(M5 + 1, M2 + shiftBias + 1);

                FDBigInteger tenSval = valueOfPow52(S5 + 1, S2 + shiftBias + 1); //Sval.mult( 10 );
                //
                // Unroll the first iteration. If our decExp estimate
                // was too high, our first quotient will be zero. In this
                // case, we discard it and decrement decExp.
                //
                ndigit = 0;
                q = Bval.quoRemIteration( Sval );
                low  = (Bval.cmp( Mval ) < 0);
                high = tenSval.addAndCmp(Bval,Mval)<=0;

                assert q < 10 : q; // excessively large digit
                if ( (q == 0) && ! high ){
                    // oops. Usually ignore leading zero.
                    decExp--;
                } else {
                    digits[ndigit++] = (byte)('0' + q);
                }
                //
                // HACK! Java spec sez that we always have at least
                // one digit after the . in either F- or E-form output.
                // Thus we will need more than one digit if we're using
                // E-form
                //
                if (decExp < -3 || decExp >= 8){
                    high = low = false;
                }
                while( ! low && ! high ){
                    q = Bval.quoRemIteration( Sval );
                    assert q < 10 : q;  // excessively large digit
                    Mval = Mval.multBy10(); //Mval = Mval.mult( 10 );
                    low  = (Bval.cmp( Mval ) < 0);
                    high = tenSval.addAndCmp(Bval,Mval)<=0;
                    digits[ndigit++] = (byte)('0' + q);
                }
                if ( high && low ){
                    Bval = Bval.leftShift(1);
                    lowDigitDifference = Bval.cmp(tenSval);
                } else {
                    lowDigitDifference = 0L; // this here only for flow analysis!
                }
                exactDecimalConversion  = Bval.isZero();
            }
            this.decExponent = decExp+1;
            this.firstDigitIndex = 0;
            this.nDigits = ndigit;
            //
            // Last digit gets rounded based on stopping condition.
            //
            if ( high ){
                if ( low ){
                    if ( lowDigitDifference == 0L ){
                        // it's a tie!
                        // choose based on which digits we like.
                        if ( (digits[firstDigitIndex+nDigits-1]&1) != 0 ) {
                            roundup();
                        }
                    } else if ( lowDigitDifference > 0 ){
                        roundup();
                    }
                } else {
                    roundup();
                }
            }
        }

        // add one to the least significant digit.
        // in the unlikely event there is a carry out, deal with it.
        // assert that this will only happen where there
        // is only one digit, e.g. (float)1e-44 seems to do it.
        //
        private void roundup() {
            int i = (firstDigitIndex + nDigits - 1);
            int q = digits[i];
            if (q == '9') {
                while (q == '9' && i > firstDigitIndex) {
                    digits[i] = '0';
                    q = digits[--i];
                }
                if (q == '9') {
                    // carryout! High-order 1, rest 0s, larger exp.
                    decExponent += 1;
                    digits[firstDigitIndex] = '1';
                    return;
                }
                // else fall through.
            }
            digits[i] = (byte) (q + 1);
            decimalDigitsRoundedUp = true;
        }

        /**
         * Estimate decimal exponent. (If it is small-ish,
         * we could double-check.)
         *<p>
         * First, scale the mantissa bits such that 1 <= d2 < 2.
         * We are then going to estimate
         *          log10(d2) ~=~  (d2-1.5)/1.5 + log(1.5)
         * and so we can estimate
         *      log10(d) ~=~ log10(d2) + binExp * log10(2)
         * take the floor and call it decExp.
         */
        private static int estimateDecExp(long fractBits, int binExp) {
            double d2 = Double.longBitsToDouble( EXP_ONE | ( fractBits & DoubleConsts.SIGNIF_BIT_MASK ) );
            double d = (d2-1.5D)*0.289529654D + 0.176091259 + (double)binExp * 0.301029995663981;
            long dBits = Double.doubleToRawLongBits(d);  //can't be NaN here so use raw
            int exponent = (int)((dBits & DoubleConsts.EXP_BIT_MASK) >> EXP_SHIFT) - DoubleConsts.EXP_BIAS;
            boolean isNegative = (dBits & DoubleConsts.SIGN_BIT_MASK) != 0; // discover sign
            if(exponent>=0 && exponent<52) { // hot path
                long mask   = DoubleConsts.SIGNIF_BIT_MASK >> exponent;
                int r = (int)(( (dBits&DoubleConsts.SIGNIF_BIT_MASK) | FRACT_HOB )>>(EXP_SHIFT-exponent));
                return isNegative ? (((mask & dBits) == 0L ) ? -r : -r-1 ) : r;
            } else if (exponent < 0) {
                return (((dBits&~DoubleConsts.SIGN_BIT_MASK) == 0) ? 0 :
                        ( (isNegative) ? -1 : 0) );
            } else { //if (exponent >= 52)
                return (int)d;
            }
        }

        /**
         * Calculates
         */
        private static int insignificantDigitsForPow2(int p2) {
            if (p2 > 1 && p2 < insignificantDigitsNumber.length) {
                return insignificantDigitsNumber[p2];
            }
            return 0;
        }

        /**
         *  If insignificant==(1L << ixd)
         *  i = insignificantDigitsNumber[idx] is the same as:
         *  int i;
         *  for ( i = 0; insignificant >= 10L; i++ )
         *         insignificant /= 10L;
         */
        @Stable
        private static final int[] insignificantDigitsNumber = {
            0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 3,
            4, 4, 4, 5, 5, 5, 6, 6, 6, 6, 7, 7, 7,
            8, 8, 8, 9, 9, 9, 9, 10, 10, 10, 11, 11, 11,
            12, 12, 12, 12, 13, 13, 13, 14, 14, 14,
            15, 15, 15, 15, 16, 16, 16, 17, 17, 17,
            18, 18, 18, 19,
        };

        // approximately ceil( log2( long5pow[i] ) )
        @Stable
        private static final int[] N_5_BITS = {
                0,
                3,
                5,
                7,
                10,
                12,
                14,
                17,
                19,
                21,
                24,
                26,
                28,
                31,
                33,
                35,
                38,
                40,
                42,
                45,
                47,
                49,
                52,
                54,
                56,
                59,
                61,
                63,
        };

    }

    private static final ThreadLocal<BinaryToASCIIBuffer> threadLocalBinaryToASCIIBuffer =
            ThreadLocal.withInitial(BinaryToASCIIBuffer::new);

    private static BinaryToASCIIBuffer getBinaryToASCIIBuffer() {
        return threadLocalBinaryToASCIIBuffer.get();
    }

    /*
     * The mathematical value x of an instance is
     *      ±<0.d_1...d_n> 10^e
     * where d_i = d[i-1] - '0' (0 < i ≤ n) is the i-th digit.
     * It is assumed that d_1 > 0.
     * isNegative denotes the - sign.
     */
    private static final class ASCIIToBinaryBuffer {

        private final boolean isNegative;
        private final int e;
        private final int n;
        private final byte[] d;

        private ASCIIToBinaryBuffer(boolean isNegative, int e, byte[] d, int n) {
            this.isNegative = isNegative;
            this.e = e;
            this.d = d;
            this.n = n;
        }

        /* Assumes n ≤ 19 and returns a decimal prefix of f as an unsigned long. */
        private long toLong(int n) {
            long f = 0;
            for (int i = 0; i < n; ++i) {
                f = 10 * f + (d[i] - '0');
            }
            return f;
        }

        private double doubleValue() {
            /*
             * As described above, the magnitude of the mathematical value is
             *      x = <0.d_1...d_n> 10^e = <d_1...d_n> 10^(e-n) = f 10^ep
             * where f = <d_1...d_n> and ep = e - n are integers.
             *
             * Let r_e denote the roundTiesToEven rounding.
             * This method returns ±r_e(x).
             */

             /* Filter out extremely small or extremely large x. */
            if (e <= DoubleToDecimal.E_THR_Z) {
                /* Test cases: "0.9e-324", "3e-500" */
                return signed(0.0);
            }
            if (e >= DoubleToDecimal.E_THR_I) {
                /* Test cases: "0.1e310", "4e500" */
                return signed(Double.POSITIVE_INFINITY);
            }

            /*
             * Attempt some fast paths before resorting to higher precision.
             * Here, let P = Double.PRECISION = 53.
             *
             * Below, fl is an unsigned long, thus we require n ≤ 19 because
             * 10^19 < 2^64 < 10^20.
             */
            int n = this.n;
            int ep = e - n;
            double v;
            int m = Math.min(n, MathUtils.N);
            long fl = toLong(m);  // unsigned
            if (n <= MathUtils.N && 0 <= ep && e <= MathUtils.N) {
                /*
                 * Here, n ≤ 19, hence f = fl < 10^19.
                 * Since e = n + ep and 0 ≤ ep ∧ n + ep ≤ 19 we see that
                 * x = f 10^ep < 10^n 10^ep = 10^(n+ep) ≤ 10^19.
                 * Thus, x = fl 10^ep fits in an unsigned long as well.
                 * If its most significant bit is 0, the long is non-negative.
                 * Otherwise, fl ≥ 2^63, so there's room for P precision bits,
                 * +1 rounding bit, +1 sticky bit.
                 * In both cases, correct rounding is achieved as below.
                 * All integer x < 10^19 are covered here.
                 */

                /*
                 * Test cases:
                 *      for fl < 2^63: "1", "2.34000e2", "9.223e18";
                 *      for fl ≥ 2^63: "9.876e18", "9223372036854776833" (this
                 *          is 2^63 + 2^10 + 1, rounding up due to sticky bit),
                 *          "9223372036854776832" (this is 2^63 + 2^10, halfway
                 *          value rounding down to even);
                 */
                fl *= MathUtils.pow10(ep);  // 0 ≤ ep < 19
                v = fl >= 0 ? fl : 2.0 * (fl >>> 1 | fl & 0b1);
                return signed(v);
            }

            if (n <= FLOG_10_MAX_LONG && -MAX_SMALL_TEN <= ep) {
                v = fl;
                /*
                 * Here, -22 ≤ ep.
                 * Further, fl < 10^18, so fl is an exact double iff
                 * (long) v == fl holds.
                 * If fl is not an exact double, resort to higher precision.
                 */
                boolean isExact = (long) v == fl;
                if (isExact && ep <= MAX_SMALL_TEN) {
                    /*
                     * Here, -22 ≤ ep ≤ 22, so 10^|ep| is an exact double.
                     * The product or quotient below operate on exact doubles,
                     * so the result is correctly rounded.
                     */

                    /*
                     * Test cases:
                     *      for ep < 0: "1.23", "0.000234";
                     *      for ep > 0: "3.45e23", "576460752303423616e20" (the
                     *          significand is 2^59 + 2^7, an exact double);
                     */
                    v = ep >= 0 ? v * SMALL_10_POW[ep] : v / SMALL_10_POW[-ep];
                    return signed(v);
                }

                /*
                 * Here, fl < 10^18 is not an exact double, or ep > 22.
                 * If fl is not an exact double, resort to higher precision.
                 */
                if (isExact) {  // v and fl are mathematically equal.
                    /*
                     * Here, ep > 22.
                     * We have f = fl = v.
                     * Note that 2^P = 9007199254740992 has 16 digits.
                     * If f does not start with 9 let ef = 16 - n, otherwise
                     * let ef = 15 - n.
                     * If ef < 0 then resort to higher precision.
                     * Otherwise, if f does not start with 9 we have n ≤ 16,
                     * so f 10^ef < 9 10^(n-1) 10^ef = 9 10^15 < 2^P.
                     * If f starts with 9 we have n ≤ 15, hence f 10^ef <
                     * 10^n 10^ef = 10^15 < 2^P.
                     *
                     * Hence, when ef ≥ 0 and ep - ef ≤ 22 we know that
                     * fl 10^ep = (fl 10^ef) 10^(ep-ef), with fl, (fl 10^ef),
                     * and 10^(ep-ef) all exact doubles.
                     */
                    int ef = (d[0] < '9' ? MAX_DEC_DIGITS + 1 : MAX_DEC_DIGITS) - n;
                    if (ef >= 0 && ep - ef <= MAX_SMALL_TEN) {
                        /*
                         * Test cases:
                         *      f does not start with 9: "1e37", "8999e34";
                         *      f starts with 9: "0.9999e36", "0.9876e37";
                         */

                        /* Rely on left-to-right evaluation. */
                        v = v * SMALL_10_POW[ef] * SMALL_10_POW[ep - ef];
                        return signed(v);
                    }
                }
            }

            /*
             * Here, the above fast paths have failed to return.
             * Force ll, lh in [10^(N-1), 10^N] to have more high order bits.
             */
            long ll = fl;  // unsigned
            long lh;  // unsigned
            if (n <= MathUtils.N) {  // ll = f
                ll *= MathUtils.pow10(MathUtils.N - n);
                lh = ll;
            } else {  // ll is an N digits long prefix of f
                lh = ll + 1;
            }
            int el = e - MathUtils.N;
            /*
             * We now have
             *      x = f 10^ep
             *      ll 10^el ≤ x ≤ lh 10^el
             *      2^59 < 10^(N-1) ≤ ll ≤ lh ≤ 10^N < 2^64
             *
             * Rather than rounding x directly, which requires full precision
             * arithmetic, approximate x as follows.
             * Let integers g and r such that (see comments in MathUtils)
             *      (g - 1) 2^r ≤ 10^el < g 2^r
             * and split g into the lower 63 bits g0 and the higher bits g1:
             *      g = g1 2^63 + g0
             * where
             *      2^62 < g1 + 1 < 2^63, 0 < g0 < 2^63
             * We have
             *      g - 1 = g1 2^63 + g0 - 1 ≥ g1 2^63
             *      g = g1 2^63 + g0 < g1 2^63 + 2^63 = (g1 + 1) 2^63
             * Let
             *      nl = ll g1          nh = lh (g1 + 1)
             * These lead to
             *      nl 2^(r+63) ≤ x < nh 2^(r+63)
             * Let
             *      v = r_e(nl 2^(r+63))        vh = r_e(nh 2^(r+63))
             * If v = vh then r_e(x) = v.
             *
             * We also have
             *      2^121 = 2^59 2^62 < nl < nh < 2^64 2^63 = 2^127
             * Therefore, each of nl and nh fits in two longs.
             * Split them into the lower 64 bits and the higher bits.
             *      nl = nl1 2^64 + nl0     2^57 ≤ nl1 < 2^63
             *      nh = nh1 2^64 + nh0     2^57 ≤ nh1 < 2^63
             * Let bl and bh be the bitlength of nl1 and nh1, resp.
             * Both bl and bh lie in the interval [58, 63], and all of nl1, nh1,
             * nl, and nh are in the normal range of double.
             * As nl ≤ nh ≤ nl + 2 ll, and as ll < 2^64, then either bh = bl,
             * or more rarely bh = bl + 1.
             *
             * As mentioned above, if v = vh then r_e(x) = v.
             * Rather than rounding nl 2^(r+63), nh 2^(r+63) boundaries directly,
             * first round nl and nh to obtain doubles wl and wh, resp.
             *      wl = r_e(nl)        wh = r_e(nh)
             * Note that both wl and wh are normal doubles.
             *
             * Assume wl = wh.
             * There's a good chance that v = scalb(wl, r + 63) holds.
             * In fact, if x ≥ MIN_NORMAL then it can be (tediously) shown that
             * v = scalb(wl, r + 63) holds, even when v overflows.
             * If x < MIN_NORMAL, and since wl is normal and v ≤ MIN_NORMAL,
             * the precision might be lowered, so scalb(wl, r + 63) might incur
             * two rounding errors and could slightly differ from v.
             *
             * It is costly to precisely determine whether x ≥ MIN_NORMAL.
             * However, bl + r > MIN_EXPONENT - 127 implies x ≥ MIN_NORMAL,
             * and bh + r ≤ MIN_EXPONENT - 127 entails x < MIN_NORMAL.
             * Finally, when bl + r ≤ MIN_EXPONENT - 127 < bh + r we see that
             * bl + r = MIN_EXPONENT - 127 and bh = bl + 1 must hold.
             *
             * As noted, nh ≤ nl + 2 ll.
             * This means
             *      nh1 ≤ nh 2^(-64) ≤ (nl + 2 ll) 2^(-64) < (nl1 + 1) + 2
             * and thus
             *      nh1 ≤ nl1 + 2
             */
            int rp = MathUtils.flog2pow10(el) + 2;  // r + 127
            long g1 = MathUtils.g1(el);
            long nl1 = Math.unsignedMultiplyHigh(ll, g1);
            long nl0 = ll * g1;
            long nh1 = Math.unsignedMultiplyHigh(lh, g1 + 1);
            long nh0 = lh * (g1 + 1);
            int bl = Long.SIZE - Long.numberOfLeadingZeros(nl1);
            if (bl + rp > Double.MIN_EXPONENT) {  // implies x is normal
                /*
                 * To round nl we need its most significant P bits, the rounding
                 * bit immediately to the right, and an indication (sticky bit)
                 * of whether there are "1" bits following the rounding bit.
                 * The sticky bit can be placed anywhere after the rounding bit.
                 * Since bl ≥ 58, the P = 53 bits, the rounding bit, and space
                 * for the sticky bit are all located in nl1.
                 *
                 * When nl0 = 0, the indication of whether there are "1" bits
                 * to the right of the rounding bit is already contained in nl1.
                 * Rounding nl to wl is the same as rounding nl1 to ul and then
                 * multiplying this by 2^64.
                 * that is, given wl = r_e(nl), ul = r_e(nl1), we get
                 * wl = scalb(ul, 64).
                 * The same holds for nh, wh, nh1, and uh.
                 * So, if ul = uh then wl = wh, thus v = scalb(ul, r + 127).
                 *
                 * When nl1 ≠ 0, there are indeed "1" bits to the right of the
                 * rounding bit.
                 * We force the rightmost bit of nl1 to 1, obtaining nl1'.
                 * Then, again, rounding nl to wl is the same as rounding nl1'
                 * to ul and multiplying this by 2^64.
                 * Analogously for nh, wh, nh1, and uh.
                 * Again, if ul = uh then wl = wh, thus v = scalb(ul, r + 127).
                 *
                 * Since nh1 ≤ nl1 + 2, then either uh = ul or uh = nextUp(ul).
                 * This means that when ul ≠ uh then
                 *      v ≤ r_e(x) ≤ nextUp(v)
                 */
                double ul = nl1 | (nl0 != 0 ? 1 : 0);
                double uh = nh1 | (nh0 != 0 ? 1 : 0);
                v = Math.scalb(ul, rp);
                if (ul == uh || v == Double.POSITIVE_INFINITY) {
                    /*
                     * Test cases:
                     *      for ll = lh ∧ ul = uh: "1.2e-200", "2.3e100";
                     *      for ll ≠ lh ∧ ul = uh: "1.2000000000000000003e-200",
                     *          "2.3000000000000000004e100";
                     *      for ll = lh ∧ v = ∞: "5.249320425370670463e308";
                     *      for ll ≠ lh ∧ v = ∞: "5.2493204253706704633e308";
                     */
                    return signed(v);
                }
            } else {
                int bh = Long.SIZE - Long.numberOfLeadingZeros(nh1);
                if (bh + rp <= Double.MIN_EXPONENT) {  // implies x is subnormal
                    /*
                     * We need to reduce the precision to avoid double rounding
                     * issues.
                     * Shifting to the right while keeping room for the rounding
                     * and the sticky bit is one way to go.
                     * Other than that, the reasoning is similar to the above case.
                     */
                    int sh = DoubleToDecimal.Q_MIN - rp;  // shift distance
                    long sbMask = -1L >>> 1 - sh;

                    long nl1p = nl1 >>> sh;
                    long rb = nl1 >>> sh - 1;
                    long sb = (nl1 & sbMask | nl0) != 0 ? 1 : 0;
                    long corr = rb & (sb | nl1p) & 0b1;
                    double ul = nl1p + corr;

                    long nh1p = nh1 >>> sh;
                    rb = nh1 >>> sh - 1;
                    sb = (nh1 & sbMask | nh0) != 0 ? 1 : 0;
                    corr = rb & (sb | nh1p) & 0b1;
                    double uh = nh1p + corr;
                    v = Math.scalb(ul, rp + sh);
                    if (ul == uh) {
                        /*
                         * Test cases:
                         *      for ll = lh: "1.2e-320";
                         *      for ll ≠ lh: "1.2000000000000000003e-320";
                         */
                        return signed(v);
                    }
                } else {
                    /*
                     * Here, bl + r ≤ MIN_EXPONENT - 127 < bh + r.
                     * As mentioned before, this means bh = bl + 1 and
                     * rp = MIN_EXPONENT - bl.
                     * As nh1 ≤ nl1 + 2, nl1 ≥ 2^57, bh = bl + 1 happens only if
                     * the most significant P + 2 bits in nl1 are all "1" bits,
                     * so wl = r_e(nl) = r_e(nh) = wh = 2^(bl+64), and
                     * thus v = vh = 2^(bl+127) = 2^MIN_EXPONENT = MIN_NORMAL.
                     */

                    /*
                     * Test cases:
                     *      for ll = lh: "2.225073858507201383e-308"
                     *      for ll ≠ lh: "2.2250738585072013831e-308"
                     */
                    return signed(Double.MIN_NORMAL);
                }
            }

            /*
             * Measurements show that the failure rate of the above fast paths
             * on the outcomes of Double.toString() on uniformly distributed
             * double bit patterns is around 0.04%.
             *
             * Here, v ≤ r_e(x) ≤ nextUp(v), with v = c 2^q (c, q are as in
             * IEEE-754 2019).
             *
             * Let vr = v + ulp(v)/2 = (c + 1/2) 2^q, the number halfway between
             * v and nextUp(v).
             * With cr = (2 c + 1), qr = q - 1 we get vr = cr 2^qr.
             */
            long bits = Double.doubleToRawLongBits(v);
            int be = (int) ((bits & DoubleConsts.EXP_BIT_MASK) >>> DoubleConsts.SIGNIFICAND_WIDTH - 1);
            int qr = be - (DoubleConsts.EXP_BIAS + DoubleConsts.SIGNIFICAND_WIDTH - 1)
                    - (be != 0 ? 1 : 0);
            long cr = 2 * (bits & DoubleConsts.SIGNIF_BIT_MASK | (be != 0 ? DoubleToDecimal.C_MIN : 0)) + 1;

            /*
             * The test vr ⋚ x is equivalent to cr 2^qr ⋚ f 10^ep.
             * This is in turn equivalent to one of 4 cases, where all exponents
             * are non-negative:
             *      ep ≥ 0 ∧ ep ≥ qr:                     cr ⋚ f 5^ep 2^(ep-qr)
             *      ep ≥ 0 ∧ ep < qr:           cr 2^(qr-ep) ⋚ f 5^ep
             *      ep < 0 ∧ ep ≥ qr:             cr 5^(-ep) ⋚ f 2^(ep-qr)
             *      ep < 0 ∧ ep < qr:   cr 5^(-ep) 2^(qr-ep) ⋚ f
             */
            FDBigInteger lhs = valueOfMulPow52(cr, Math.max(-ep, 0), Math.max(qr - ep, 0));
            FDBigInteger rhs = new FDBigInteger(fl, d, m, n)
                    .multByPow52(Math.max(ep, 0), Math.max(ep - qr, 0));
            int cmp = lhs.cmp(rhs);
            v = Double.longBitsToDouble(cmp < 0
                    ? bits + 1
                    : cmp > 0
                    ? bits
                    : bits + (bits & 0b1));
            return signed(v);
        }

        private double signed(double v) {
            return isNegative ? -v : v;
        }

        private float floatValue() {
            /* For details not covered here, see comments in doubleValue(). */
            if (e <= E_THR_Z[BINARY_32_IX]) {
                return signed(0.0f);
            }
            if (e >= E_THR_I[BINARY_32_IX]) {
                return signed(Float.POSITIVE_INFINITY);
            }
            int n = this.n;
            int ep = e - n;
            float v;
            int m = Math.min(n, MathUtils.N);
            long fl = toLong(m);
            if (n <= MathUtils.N && 0 <= ep && e <= MathUtils.N) {
                fl *= MathUtils.pow10(ep);  // 0 ≤ ep < 19
                v = fl >= 0 ? fl : 2.0f * (fl >>> 1 | fl & 0b1);
                return signed(v);
            }
            if (n <= FLOG_10_MAX_LONG && -SINGLE_MAX_SMALL_TEN <= ep) {
                v = fl;
                boolean isExact = (long) v == fl;
                if (isExact && ep <= SINGLE_MAX_SMALL_TEN) {
                    v = ep >= 0 ? v * SINGLE_SMALL_10_POW[ep] : v / SINGLE_SMALL_10_POW[-ep];
                    return signed(v);
                }
                /*
                 * The similar case in doubleValue() where fl is exact and
                 * ep is somewhat larger than MAX_SMALL_TEN is already covered
                 * above for float.
                 */
            }
            long ll = fl;
            long lh;
            if (n <= MathUtils.N) {
                ll *= MathUtils.pow10(MathUtils.N - n);
                lh = ll;
            } else {
                lh = ll + 1;
            }
            int el = e - MathUtils.N;
            int rp = MathUtils.flog2pow10(el) + 2;
            long g1 = MathUtils.g1(el);
            long nl1 = Math.unsignedMultiplyHigh(ll, g1);
            long nl0 = ll * g1;
            long nh1 = Math.unsignedMultiplyHigh(lh, g1 + 1);
            long nh0 = lh * (g1 + 1);
            int bl = Long.SIZE - Long.numberOfLeadingZeros(nl1);
            if (bl + rp > Float.MIN_EXPONENT) {
                float ul = nl1 | (nl0 != 0 ? 1 : 0);
                float uh = nh1 | (nh0 != 0 ? 1 : 0);
                v = Math.scalb(ul, rp);
                if (ul == uh || v == Float.POSITIVE_INFINITY) {
                    return signed(v);
                }
            } else {
                int bh = Long.SIZE - Long.numberOfLeadingZeros(nh1);
                if (bh + rp <= Float.MIN_EXPONENT) {
                    int sh = FloatToDecimal.Q_MIN - rp;
                    long sbMask = -1L >>> 1 - sh;

                    long nl1p = nl1 >>> sh;
                    long rb = nl1 >>> sh - 1;
                    long sb = (nl1 & sbMask | nl0) != 0 ? 1 : 0;
                    long corr = rb & (sb | nl1p) & 0b1;
                    float ul = nl1p + corr;

                    long nh1p = nh1 >>> sh;
                    rb = nh1 >>> sh - 1;
                    sb = (nh1 & sbMask | nh0) != 0 ? 1 : 0;
                    corr = rb & (sb | nh1p) & 0b1;
                    float uh = nh1p + corr;
                    v = Math.scalb(ul, rp + sh);
                    if (ul == uh) {
                        return signed(v);
                    }
                } else {
                    return signed(Float.MIN_NORMAL);
                }
            }
            int bits = Float.floatToRawIntBits(v);
            int be = (bits & FloatConsts.EXP_BIT_MASK) >>> FloatConsts.SIGNIFICAND_WIDTH - 1;
            int qr = be - (FloatConsts.EXP_BIAS + FloatConsts.SIGNIFICAND_WIDTH - 1)
                    - (be != 0 ? 1 : 0);
            int cr = 2 * (bits & FloatConsts.SIGNIF_BIT_MASK | (be != 0 ? FloatToDecimal.C_MIN : 0)) + 1;
            FDBigInteger lhs = valueOfMulPow52(cr, Math.max(-ep, 0), Math.max(qr - ep, 0));
            FDBigInteger rhs = new FDBigInteger(fl, d, m, n)
                    .multByPow52(Math.max(ep, 0), Math.max(ep - qr, 0));
            int cmp = lhs.cmp(rhs);
            v = Float.intBitsToFloat(cmp < 0
                    ? bits + 1
                    : cmp > 0
                    ? bits
                    : bits + (bits & 0b1));
            return signed(v);
        }

        private float signed(float v) {
            return isNegative ? -v : v;
        }

        /* All the powers of 10 that can be represented exactly in double. */
        @Stable
        private static final double[] SMALL_10_POW = {
                1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9,
                1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19,
                1e20, 1e21, 1e22,
        };

        /* All the powers of 10 that can be represented exactly in float. */
        @Stable
        private static final float[] SINGLE_SMALL_10_POW = {
                1e0f, 1e1f, 1e2f, 1e3f, 1e4f, 1e5f, 1e6f, 1e7f, 1e8f, 1e9f,
                1e10f,
        };

        private static final int MAX_SMALL_TEN = SMALL_10_POW.length - 1;
        private static final int SINGLE_MAX_SMALL_TEN = SINGLE_SMALL_10_POW.length - 1;

    }

    /**
     * Returns a <code>BinaryToASCIIConverter</code> for a <code>double</code>.
     * The returned object is a <code>ThreadLocal</code> variable of this class.
     *
     * @param d      The double precision value to convert.
     * @param compat    compatibility with releases < JDK 21
     * @return The converter.
     */
    public static BinaryToASCIIBuffer getBinaryToASCIIConverter(double d, boolean compat) {
        return compat
                ? getCompatBinaryToASCIIConverter(d)
                : getBinaryToASCIIConverter(d);
    }

    private static BinaryToASCIIBuffer getBinaryToASCIIConverter(double d) {
        assert Double.isFinite(d);

        FormattedFPDecimal dec = FormattedFPDecimal.split(d);
        BinaryToASCIIBuffer buf = getBinaryToASCIIBuffer();

        buf.nDigits = dec.getPrecision();
        buf.decExponent = dec.getExp() + buf.nDigits;
        buf.firstDigitIndex = 0;
        buf.exactDecimalConversion = dec.getExact();
        buf.decimalDigitsRoundedUp = dec.getAway();

        long f = dec.getSignificand();
        byte[] digits = buf.digits;
        for (int i = buf.nDigits - 1; i >= 0; --i) {
            long q = f / 10;
            digits[i] = (byte) ((f - 10 * q) + '0');
            f = q;
        }
        return buf;
    }

    /*
     * The old implementation of getBinaryToASCIIConverter().
     * Should be removed in the future, along with its dependent methods and
     * fields (> 550 lines).
     */
    private static BinaryToASCIIBuffer getCompatBinaryToASCIIConverter(double d) {
        long dBits = Double.doubleToRawLongBits(d);
        boolean isNegative = (dBits&DoubleConsts.SIGN_BIT_MASK) != 0; // discover sign
        long fractBits = dBits & DoubleConsts.SIGNIF_BIT_MASK;
        int  binExp = (int)( (dBits&DoubleConsts.EXP_BIT_MASK) >> EXP_SHIFT );
        // Discover obvious special cases of NaN and Infinity.
        if ( binExp == (int)(DoubleConsts.EXP_BIT_MASK>>EXP_SHIFT) ) {
            if ( fractBits == 0L ){
                throw new IllegalArgumentException((isNegative ? "-" : "") + INFINITY_REP);
            } else {
                throw new IllegalArgumentException(NAN_REP);
            }
        }
        // Finish unpacking
        // Normalize denormalized numbers.
        // Insert assumed high-order bit for normalized numbers.
        // Subtract exponent bias.
        int  nSignificantBits;
        if ( binExp == 0 ){
            if ( fractBits == 0L ){
                // not a denorm, just a 0!
                return isNegative ? B2AC_NEGATIVE_ZERO : B2AC_POSITIVE_ZERO;
            }
            int leadingZeros = Long.numberOfLeadingZeros(fractBits);
            int shift = leadingZeros-(63-EXP_SHIFT);
            fractBits <<= shift;
            binExp = 1 - shift;
            nSignificantBits =  64-leadingZeros; // recall binExp is  - shift count.
        } else {
            fractBits |= FRACT_HOB;
            nSignificantBits = EXP_SHIFT+1;
        }
        binExp -= DoubleConsts.EXP_BIAS;
        BinaryToASCIIBuffer buf = getBinaryToASCIIBuffer();
        // call the routine that actually does all the hard work.
        buf.dtoa(binExp, fractBits, nSignificantBits);
        return buf;
    }

    /**
     * The input must match the {@link Double#valueOf(String) rules described here},
     * about leading and trailing whitespaces, and the grammar.
     *
     * @param in the non-null input
     * @param ix one of the {@code BINARY_<S>_IX} constants, where {@code <S>}
     *           is one of 16, 32, 64
     * @return an appropriate binary converter
     * @throws NullPointerException  if the input is null
     * @throws NumberFormatException if the input is malformed
     */
    private static double readJavaFormatString(String in, int ix) {
        /*
         * The scanning proper does not allocate any object,
         * nor does it perform any costly computation.
         * This means that all scanning errors are detected without consuming
         * any heap, before actually throwing.
         *
         * Once scanning is complete, the method determines the length
         * of a prefix of the significand that is sufficient for correct
         * rounding according to roundTiesToEven.
         * The actual value of the prefix length might not be optimal,
         * but is always a safe choice.
         *
         * For hexadecimal input, the prefix is processed by this method directly,
         * without allocating objects before creating the returned instance.
         *
         * For decimal input, the prefix is copied to the returned instance,
         * along with the other information needed for the conversion.
         * For comparison, the prefix length is at most
         *       23 for BINARY_16_IX (Float16, once integrated in java.base)
         *      114 for BINARY_32_IX (float)
         *      769 for BINARY_64_IX (double)
         * but is much shorter in common cases.
         */
        int len = in.length();  // fail fast on null

        /* Skip leading whitespaces. */
        int i = skipWhitespaces(in, 0);  // main running index
        if (i == len) {
            throw new NumberFormatException("empty String");
        }

        /* Scan opt significand sign. */
        int ch;  // running char
        int ssign = ' ';  // ' ' iff sign is implicit
        if ((ch = in.charAt(i)) == '-' || ch == '+') {  // i < len
            ssign = ch;
            ++i;
        }

        /* Determine whether we are facing a symbolic value or hex notation. */
        boolean isDec = true;  // decimal input until proven to the contrary
        if (i < len) {
            ch = in.charAt(i);
            if (ch == 'I') {
                scanSymbolic(in, i, INFINITY_REP);
                return ssign != '-' ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }
            if (ch == 'N') {
                scanSymbolic(in, i, NAN_REP);
                return Double.NaN;  // ignore sign
            }
            if (ch == '0' && i + 1 < len && toLowerCase(in.charAt(i + 1)) == 'x') {
                isDec = false;
                i += 2;
            }
        }

        int pt = 0;  // index after point, 0 iff absent
        int start = i;  // index of start of the significand, excluding opt sign

        /* Skip opt leading zeros, including an opt point. */
        while (i < len && ((ch = in.charAt(i)) == '0' || ch == '.')) {
            ++i;
            if (ch == '.') {
                pt = checkMultiplePoints(pt, i);
            }
        }
        int lz = i;  // index after leading group of zeros or point

        /*
         * Scan all remaining chars of the significand, including an opt point.
         * Also locate the index after the end of the trailing group of non-zeros
         * inside this range of the input.
         */
        int tnz = 0;  // index after trailing group of non-zeros, 0 iff absent
        while (i < len && (isDigit(ch = in.charAt(i), isDec) || ch == '.')) {
            i++;
            if (ch == '.') {
                pt = checkMultiplePoints(pt, i);
            } else if (ch != '0') {
                tnz = i;
            }
        }
        check(in, i - start > (pt != 0 ? 1 : 0));  // must have at least one digit
        int stop = i;  // index after the significand

        /* Scan exponent part, optional for dec, mandatory for hex. */
        long ep = 0;  // exponent, implicitly 0
        boolean hasExp = false;
        if (i < len && ((ch = toLowerCase(in.charAt(i))) == 'e' && isDec
                || ch == 'p' && !isDec)) {
            ++i;

            /* Scan opt exponent sign. */
            int esign = ' ';  // esign == ' ' iff the sign is implicit
            if (i < len && ((ch = in.charAt(i)) == '-' || ch == '+')) {
                esign = ch;
                ++i;
            }

            /* Scan the exponent digits. Accumulate in ep, clamping at 10^10. */
            while (i < len && isDigit(ch = in.charAt(i), true)) {  // ep is decimal
                ++i;
                ep = appendDecDigit(ep, ch);
            }
            check(in, i - stop >= 3  // at least 3 chars after significand
                    || i - stop == 2 && esign == ' ');  // 2 chars, one is digit
            if (esign == '-') {
                ep = -ep;
            }
            hasExp = true;
        }
        /*
         * |ep| < 10^10, or |ep| = 10^10 when considered "large".
         * A "large" ep either generates a zero or an infinity.
         */
        check(in, isDec | hasExp);

        /* Skip opt [FfDd]? suffix. */
        if (i < len && ((ch = toLowerCase(in.charAt(i))) == 'f' || ch == 'd')) {
            ++i;
        }

        /* Skip optional trailing whitespaces, then must be at the end of input. */
        check(in, skipWhitespaces(in, i) == len);

        /* By now, the input is syntactically correct. */
        if (tnz == 0) {  // all zero digits, so ignore ep and point
            return ssign != '-' ? 0.0 : -0.0;
        }

        /*
         * Virtually adjust the point position to be just after
         * the last non-zero digit by adjusting the exponent accordingly
         * (without modifying the physical pt, as it is used later on).
         *
         * Determine the count of digits, excluding leading and trailing zeros.
         *
         * These are the possible situations:
         *         |lz               |tnz     |stop
         * 00000000123456000000234567000000000
         *
         *  |pt     |lz               |tnz     |stop
         * .00000000123456000000234567000000000
         *
         *    |pt   |lz               |tnz     |stop
         * 00.000000123456000000234567000000000
         *
         *          |pt=lz            |tnz     |stop
         * 00000000.123456000000234567000000000
         *
         *         |lz  |pt           |tnz     |stop
         * 000000001234.56000000234567000000000
         *
         *         |lz      |pt       |tnz     |stop
         * 0000000012345600.0000234567000000000
         *
         *         |lz          |pt   |tnz     |stop
         * 00000000123456000000.234567000000000
         *
         *         |lz            |pt |tnz     |stop
         * 0000000012345600000023.4567000000000
         *
         *         |lz                |pt=tnz  |stop
         * 00000000123456000000234567.000000000
         *
         *         |lz               |tnz  |pt |stop
         * 0000000012345600000023456700000.0000
         *
         *         |lz               |tnz      |pt=stop
         * 00000000123456000000234567000000000.
         *
         * In decimal, moving the point by one position means correcting ep by 1.
         * In hexadecimal, it means correcting ep by 4.
         */
        long emult = isDec ? 1L : 4L;
        int n = tnz - lz;  // number of significant digits, 1st approximation
        if (pt == 0) {
            ep += emult * (stop - tnz);
        } else {
            ep += emult * (pt - tnz);
            if (pt > tnz) {  // '.' was counted as a position, adjust ep
                ep -= emult;
            } else if (lz < pt) {  // lz < pt ≤ tnz
                n -= 1;
            }
        }
        /*
         * n = number of significant digits (that is, not counting leading nor
         * trailing zeros)
         * |ep| < 10^11
         *
         * The magnitude x of the input meets
         *      x = f 10^ep  (decimal)
         *      x = f 2^ep  (hexadecimal)
         * Integer f = <d_1 ... d_n> consists of the n decimal or hexadecimal
         * digits found in part [lz, tnz) of the input, and d_1 > 0, d_n > 0.
         */

        if (!isDec) {  // hexadecimal conversion is performed entirely here
            /*
             * Rounding the leftmost P bits +1 rounding bit +1 sticky bit
             * has the same outcome as rounding all bits.
             * In terms of hex digits, we need room for HEX_COUNT of them.
             */
            int j = 0;
            i = lz;
            long c = 0;
            int le = Math.min(n, HEX_COUNT[ix]);
            while (j < le) {
                if ((ch = in.charAt(i++)) != '.') {
                    ++j;
                    c = c << 4 | digitFor(ch);
                }
            }
            if (n > le) {
                c |= 0b1;  // force a sticky bit
                ep += 4L * (n - le);
            }

            int bl = Long.SIZE - Long.numberOfLeadingZeros(c);  // bitlength
            /*
             * Let x = c 2^ep, so 2^(ep+bl-1) ≤ x < 2^(ep+bl)
             * When ep + bl < Q_MIN then x certainly rounds to zero.
             * When ep + bl > QE_MAX then x surely rounds to infinity.
             */
            if (ep < Q_MIN[ix] - bl) {
                return ssign != '-' ? 0.0 : -0.0;
            }
            if (ep > QP_MAX[ix] - bl) {
                return ssign != '-' ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }
            int q = (int) ep;  // narrowing conversion is safe
            int shr;  // (sh)ift to (r)ight iff shr > 0
            if (q >= QP_MIN[ix] - bl) {
                shr = bl - P[ix];
                q += shr;
            } else {
                shr = Q_MIN[ix] - q;
                q = Q_MIN[ix];
            }
            if (shr > 0) {
                long thr = 1L << shr;
                long tail = (c & thr - 1) << 1;
                c >>>= shr;
                if (tail > thr || tail == thr && (c & 0b1) != 0) {
                    c += 1;
                    if (c >= 1L << P[ix]) {  // but in fact it can't be >
                        c >>>= 1;
                        q += 1;
                    }
                }
            } else {
                c <<= -shr;
            }

            /* For now throw on BINARY_16_IX, until Float16 is integrated in java.base. */
            return switch (ix) {
                case BINARY_32_IX -> buildFloat(ssign, q, c);
                case BINARY_64_IX -> buildDouble(ssign, q, c);
                default -> throw new AssertionError("unexpected");
            };
        }

        /*
         * For decimal inputs, we copy an appropriate prefix of the input and
         * rely on another method to do the (sometimes intensive) math conversion.
         *
         * Define e = n + ep, which leads to
         *      x = 0.d_1 ... d_n 10^e, 10^(e-1) ≤ x < 10^e
         * If e ≤ E_THR_Z then x rounds to zero.
         * Similarly, if e ≥ E_THR_I then x rounds to infinity.
         * We return immediately in these cases.
         * Otherwise, e fits in an int, aptly named e as well.
         */
        int e = Math.clamp(ep + n, E_THR_Z[ix], E_THR_I[ix]);
        if (e == E_THR_Z[ix]) {  // the true mathematical e ≤ E_THR_Z
            return ssign != '-' ? 0.0 : -0.0;
        }
        if (e == E_THR_I[ix]) {  // the true mathematical e ≥ E_THR_I
            return ssign != '-' ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }

        /*
         * For further considerations, x also needs to be seen as
         *      x = beta 2^q
         * with real beta and integer q meeting
         *      q ≥ Q_MIN
         * and
         *      either  2^(P-1) ≤ beta < 2^P
         *      or      0 < beta < 2^(P-1) and q = Q_MIN
         * The (unique) solution is
         *      q = max(floor(log2(x)) - (P-1), Q_MIN), beta = x 2^(-q)
         * It's usually costly to determine q as here.
         * However, estimates to q are cheaper and quick to compute.
         *
         * Indeed, it's a matter of some simple maths to show that, by defining
         *      ql = max(floor((e-1) log2(10)) - (P-1), Q_MIN)
         *      qh = max(floor(e log2(10)) - (P-1), Q_MIN)
         * then the following hold
         *      ql ≤ q ≤ qh, and qh - ql ≤ 4
         * Since by now e is relatively small, we can leverage flog2pow10().
         *
         * Consider the half-open interval [ 2^(P-1+q), 2^(P+q) ).
         * It contains all floating-point values of the form
         *      c 2^q, c integer, 2^(P-1) ≤ c < 2^P (normal values)
         * When q = Q_MIN also consider the interval half-open [0, 2^(P-1+q) ),
         * which contains all floating-point values of the form
         *      c 2^q, c integer, 0 ≤ c < 2^(P-1) (subnormal values and zero)
         * For these c values, all numbers of the form
         *      (c + 1/2) 2^q
         * also belong to the intervals.
         * These are the boundaries of the rounding intervals and are key for
         * correct rounding.
         *
         * First assume ql > 0, so q > 0.
         * All rounding boundaries (c + 1/2) 2^q are integers.
         *
         * Hence, to correctly round x, it's enough to retain its integer part,
         * +1 non-zero sticky digit iff the fractional part is non-zero.
         * (Well, the sticky digit is only needed when the integer part
         * coincides with a boundary, but that's hard to detect at this stage.
         * Adding the sticky digit is always safe.)
         * If n > e we pass the digits <d_1...d_e 8> (8 is as good as any other
         * non-zero sticky digit) and the exponent e to the conversion routine.
         * If n ≤ e we pass all the digits <d_1...d_n> (no sticky digit,
         * as the fractional part is empty) and the exponent e to the converter.
         *
         * Now assume qh ≤ 0, so q ≤ 0.
         * The boundaries (c + 1/2) 2^q = (2c + 1) 2^(q-1) have a fractional part
         * of 1 - q digits: some (or zero) leading zeros, the rightmost is 5.
         * A correct rounding needs to retain the integer part of x (if any),
         * 1 - q digits of the fractional part, +1 non-zero sticky digit iff
         * the rest of the fractional part beyond the 1 - q digits is non-zero.
         * (Again, the sticky digit is only needed when the digit in f at the
         * same position as the last 5 of the rounding boundary is 5 as well.
         * But let's keep it simple for now.)
         * However, q is unknown, so use the conservative ql instead.
         * More precisely, if n > e + 1 - ql we pass the leftmost e + 1 - ql
         * digits of f, sticky 8 (the "most even" digit), and e.
         * Otherwise, n ≤ e + 1 - ql.
         * We pass all n digits of f, no sticky digit, and e to the converter.
         *
         * Otherwise, ql ≤ 0 < qh, so -4 < q ≤ 4.
         * Again, since q is not known exactly, we proceed as in the previous
         * case, with ql as a safe replacement for q.
         */
        int ql = Math.max(MathUtils.flog2pow10(e - 1) - (P[ix] - 1), Q_MIN[ix]);
        int np = e + Math.max(2 - ql, 1);
        byte[] d = new byte[Math.min(n, np)];
        if (n >= np) {
            copyDigits(in, d, np - 1, lz);
            d[np - 1] = '8';  // append the "most even" non-zero sticky digit
        } else {
            copyDigits(in, d, n, lz);
        }
        /* For now throw on BINARY_16_IX, until Float16 is integrated in java.base. */
        return switch (ix) {
            case BINARY_32_IX ->
                    new ASCIIToBinaryBuffer(ssign == '-', e, d, d.length).floatValue();
            case BINARY_64_IX ->
                    new ASCIIToBinaryBuffer(ssign == '-', e, d, d.length).doubleValue();
            default -> throw new AssertionError("unexpected");
        };
    }

    private static int toLowerCase(int ch) {
        return ch | 0b10_0000;
    }

    private static double buildDouble(int ssign, int q, long c) {
        long be = c < 1L << P[BINARY_64_IX] - 1
                ? 0
                : q + ((DoubleConsts.EXP_BIAS - 1) + P[BINARY_64_IX]);
        long bits = (ssign != '-' ? 0L : 1L << Double.SIZE - 1)
                | be << P[BINARY_64_IX] - 1
                | c & DoubleConsts.SIGNIF_BIT_MASK;
        return Double.longBitsToDouble(bits);
    }

    private static float buildFloat(int ssign, int q, long c) {
        int be = c < 1L << P[BINARY_32_IX] - 1
                ? 0
                : q + ((FloatConsts.EXP_BIAS - 1) + P[BINARY_32_IX]);
        int bits = (ssign != '-' ? 0 : 1 << Float.SIZE - 1)
                | be << P[BINARY_32_IX] - 1
                | (int) c & FloatConsts.SIGNIF_BIT_MASK;
        return Float.intBitsToFloat(bits);
    }

    private static void copyDigits(String in, byte[] d, int len, int i) {
        int ch;
        int j = 0;
        while (j < len) {
            if ((ch = in.charAt(i++)) != '.') {
                d[j++] = (byte) ch;
            }
        }
    }

    /* Arithmetically "appends the dec digit" ch to v ≥ 0, clamping at 10^10. */
    private static long appendDecDigit(long v, int ch) {
        return v < 10_000_000_000L / 10 ? 10 * v + (ch - '0') : 10_000_000_000L;
    }

    /* Whether ch is a digit char '0-9', 'A-F', or 'a-f', depending on isDec. */
    private static boolean isDigit(int ch, boolean isDec) {
        int lch;  // lowercase ch
        return '0' <= ch && ch <= '9' ||
                !isDec && 'a' <= (lch = toLowerCase(ch)) && lch <= 'f';
    }

    /* Returns the numeric value of ch, assuming it is a hexdigit. */
    private static int digitFor(int ch) {
        return ch <= '9' ? ch - '0' : toLowerCase(ch) - ('a' - 10);
    }

    /*
     * Starting at i, skips all chars in ['\0', ' '].
     * Returns the index after the whitespaces.
     */
    private static int skipWhitespaces(String in, int i) {
        int len = in.length();
        for (; i < len && in.charAt(i) <= ' '; ++i);  // empty body
        return i;
    }

    /*
     * Attempts to scan sub and optional trailing whitespaces, starting at index i.
     * The optional whitespaces must be at the end of in.
     */
    private static void scanSymbolic(String in, int i, String sub) {
        int high = i + sub.length();  // might overflow, checked in next line
        check(in, i <= high && high <= in.length()
                        && in.indexOf(sub, i, high) == i
                        && skipWhitespaces(in, high) == in.length());
    }

    /*
     * Returns i if this is the first time the scanner detects a point.
     * Throws otherwise.
     */
    private static int checkMultiplePoints(int pt, int i) {
        if (pt != 0) {
            throw new NumberFormatException("multiple points");
        }
        return i;
    }

    private static final int MAX_OUT = 1_000;
    private static final String OMITTED = " ... ";
    private static final int L_HALF = (MAX_OUT - OMITTED.length()) / 2;
    private static final int R_HALF = MAX_OUT - (L_HALF + OMITTED.length());

    private static void check(String in, boolean expected) {
        if (!expected) {
            int len = in.length();
            if (len > MAX_OUT) {  // discard middle chars to achieve a length of MAX_OUT
                in = in.substring(0, L_HALF) + OMITTED + in.substring(len - R_HALF);
            }
            throw new NumberFormatException("For input string: \"" + in + "\"");
        }
    }

    /*
     * According to IEEE 754-2019, a finite positive binary floating-point
     * value of precision P is (uniquely) expressed as
     *      c 2^q
     * where integers c and q meet
     *      Q_MIN ≤ q ≤ Q_MAX
     *      either      2^(P-1) ≤ c < 2^P                   (normal)
     *      or          0 < c < 2^(P-1)  &  q = Q_MIN       (subnormal)
     *      c = <b_1...b_P>  (b_i in [0, 2)),   b_1 > 0 iff normal
     *
     * Equivalently, the floating-point value can be (uniquely) expressed as
     *      m 2^qp
     * where integer qp and real m meet
     *      qp = q + P
     *      m = c 2^(-P)
     * Hence,
     *      QP_MIN = Q_MIN + P, QP_MAX = Q_MAX + P,
     *      2^(-1) ≤ m < 1      (normal)
     *      m < 2^(-1)          (subnormal)
     *      m = <0.b_1...b_P>
     */

    /*
     * These constants are used to indicate the IEEE binary floating-point format
     * as an index (ix) to some methods and static arrays in this class.
     */
    private static final int BINARY_16_IX = 0;
    private static final int BINARY_32_IX = 1;
    private static final int BINARY_64_IX = 2;
    // private static final int BINARY_128_IX = 3;
    // private static final int BINARY_256_IX = 4;

    @Stable
    private static final int[] P = {
            11,  // 11
            FloatToDecimal.P,  // 24
            DoubleToDecimal.P,  // 53
            // 113,
            // 237,
    };

    /* Minimum exponent in the c 2^q representation. */
    @Stable
    private static final int[] Q_MIN = {
            -24,  // Float16ToDecimal.Q_MIN,  // -24
            FloatToDecimal.Q_MIN,  // -149
            DoubleToDecimal.Q_MIN,  // -1_074
            // -16_494,
            // -262_378,
    };

    /* Minimum exponent in the m 2^qp representation. */
    @Stable
    private static final int[] QP_MIN = {
            Q_MIN[BINARY_16_IX] + P[BINARY_16_IX],  // -13
            Q_MIN[BINARY_32_IX] + P[BINARY_32_IX],  // -125
            Q_MIN[BINARY_64_IX] + P[BINARY_64_IX],  // -1_021
            // Q_MIN[BINARY_128_IX] + P[BINARY_128_IX],  // -16_381
            // Q_MIN[BINARY_256_IX] + P[BINARY_256_IX],  // -262_141
    };

    /* Maximum exponent in the m 2^qp representation. */
    @Stable
    private static final int[] QP_MAX = {
            3 - QP_MIN[BINARY_16_IX],  // 16
            3 - QP_MIN[BINARY_32_IX],  // 128
            3 - QP_MIN[BINARY_64_IX],  // 1_024
            // 3 - QP_MIN[BINARY_128_IX],  // 16_384
            // 3 - QP_MIN[BINARY_256_IX],  // 262_144
    };

    /*
     * For each binary floating-point format, let
     *      THR_Z = ulp(0.0) / 2 = MIN_VALUE / 2
     * THR_Z is the zero threshold.
     * Real x rounds to 0 by roundTiesToEven iff |x| ≤ THR_Z.
     *
     * E_THR_Z = max{e : 10^e ≤ THR_Z}.
     */
    @Stable
    private static final int[] E_THR_Z = {
            -8,  // -8
            FloatToDecimal.E_THR_Z,  // -46
            DoubleToDecimal.E_THR_Z,  // -324
            // -4_966,
            // -78_985,
    };

    /*
     * For each binary floating-point format, let
     *      THR_I = MAX_VALUE + ulp(MAX_VALUE) / 2
     * THR_I is the infinity threshold.
     * Real x rounds to infinity by roundTiesToEven iff |x| ≥ THR_I.
     *
     * E_THR_I = min{e : THR_I ≤ 10^(e-1)}.
     */
    @Stable
    private static final int[] E_THR_I = {
            6,  // 6
            FloatToDecimal.E_THR_I,  // 40
            DoubleToDecimal.E_THR_I,  // 310
            // 4_934,
            // 78_915,
    };

    /*
     * The most significant P +1 rounding bit +1 sticky bit = P + 2 bits in a
     * hexadecimal string need up to HEX_COUNT = floor(P/4) + 2 hex digits.
     */
    @Stable
    private static final int[] HEX_COUNT = {
            P[BINARY_16_IX] / 4 + 2,  // 4
            P[BINARY_32_IX] / 4 + 2,  // 8
            P[BINARY_64_IX] / 4 + 2,  // 15
            // P[BINARY_128_IX] / 4 + 2,  // 30
            // P[BINARY_256_IX] / 4 + 2,  // 61
    };

}
