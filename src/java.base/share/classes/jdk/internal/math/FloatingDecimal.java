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

import java.util.Arrays;

/**
 * A class for converting between ASCII and decimal representations of a single
 * or double precision floating point number. Most conversions are provided via
 * static convenience methods, although a <code>BinaryToASCIIConverter</code>
 * instance may be obtained and reused.
 */
public class FloatingDecimal{
    //
    // Constants of the implementation;
    // most are IEEE-754 related.
    // (There are more really boring constants at the end.)
    //
    static final int    EXP_SHIFT = DoubleConsts.SIGNIFICAND_WIDTH - 1;
    static final long   FRACT_HOB = ( 1L<<EXP_SHIFT ); // assumed High-Order bit
    static final long   EXP_ONE   = ((long)DoubleConsts.EXP_BIAS)<<EXP_SHIFT; // exponent of 1.0
    static final int    MAX_SMALL_BIN_EXP = 62;
    static final int    MIN_SMALL_BIN_EXP = -( 63 / 3 );
    static final int    MAX_DECIMAL_DIGITS = 15;
    static final int    MAX_DECIMAL_EXPONENT = 308;
    static final int    MIN_DECIMAL_EXPONENT = -324;
    static final int    MAX_NDIGITS = 1100;

    static final int    SINGLE_EXP_SHIFT  =   FloatConsts.SIGNIFICAND_WIDTH - 1;
    static final int    SINGLE_FRACT_HOB  =   1<<SINGLE_EXP_SHIFT;
    static final int    SINGLE_MAX_DECIMAL_DIGITS = 7;
    static final int    SINGLE_MAX_DECIMAL_EXPONENT = 38;
    static final int    SINGLE_MIN_DECIMAL_EXPONENT = -45;
    static final int    SINGLE_MAX_NDIGITS = 200;

    static final int    INT_DECIMAL_DIGITS = 9;

    /**
     * Converts a double precision floating point value to a <code>String</code>.
     *
     * @param d The double precision value.
     * @return The value converted to a <code>String</code>.
     */
    public static String toJavaFormatString(double d) {
        return getBinaryToASCIIConverter(d).toJavaFormatString();
    }

    /**
     * Converts a single precision floating point value to a <code>String</code>.
     *
     * @param f The single precision value.
     * @return The value converted to a <code>String</code>.
     */
    public static String toJavaFormatString(float f) {
        return getBinaryToASCIIConverter(f).toJavaFormatString();
    }

    /**
     * Appends a double precision floating point value to an <code>Appendable</code>.
     * @param d The double precision value.
     * @param buf The <code>Appendable</code> with the value appended.
     */
    public static void appendTo(double d, Appendable buf) {
        getBinaryToASCIIConverter(d).appendTo(buf);
    }

    /**
     * Appends a single precision floating point value to an <code>Appendable</code>.
     * @param f The single precision value.
     * @param buf The <code>Appendable</code> with the value appended.
     */
    public static void appendTo(float f, Appendable buf) {
        getBinaryToASCIIConverter(f).appendTo(buf);
    }

    /**
     * Converts a <code>String</code> to a double precision floating point value.
     *
     * @param s The <code>String</code> to convert.
     * @return The double precision value.
     * @throws NumberFormatException If the <code>String</code> does not
     * represent a properly formatted double precision value.
     */
    public static double parseDouble(String s) throws NumberFormatException {
        return readJavaFormatString(s, BINARY_64_IX).doubleValue();
    }

    /**
     * Converts a <code>String</code> to a single precision floating point value.
     *
     * @param s The <code>String</code> to convert.
     * @return The single precision value.
     * @throws NumberFormatException If the <code>String</code> does not
     * represent a properly formatted single precision value.
     */
    public static float parseFloat(String s) throws NumberFormatException {
        return readJavaFormatString(s, BINARY_32_IX).floatValue();
    }

    /**
     * Converts a sequence of digits ('0'-'9') as well as an exponent to a positive
     * double value
     *
     * @param decExp The decimal exponent of the value to generate
     * @param digits The digits of the significand.
     * @param length Number of digits to use
     * @return The double-precision value of the conversion
     */
    public static double parseDoubleSignlessDigits(int decExp, char[] digits, int length) {
        return readDoubleSignlessDigits(decExp, digits, length).doubleValue();
    }

    /**
     * A converter which can process single or double precision floating point
     * values into an ASCII <code>String</code> representation.
     */
    public interface BinaryToASCIIConverter {
        /**
         * Converts a floating point value into an ASCII <code>String</code>.
         * @return The value converted to a <code>String</code>.
         */
        String toJavaFormatString();

        /**
         * Appends a floating point value to an <code>Appendable</code>.
         * @param buf The <code>Appendable</code> to receive the value.
         */
        void appendTo(Appendable buf);

        /**
         * Retrieves the decimal exponent most closely corresponding to this value.
         * @return The decimal exponent.
         */
        int getDecimalExponent();

        /**
         * Retrieves the value as an array of digits.
         * @param digits The digit array.
         * @return The number of valid digits copied into the array.
         */
        int getDigits(char[] digits);

        /**
         * Indicates the sign of the value.
         * @return {@code value < 0.0}.
         */
        boolean isNegative();

        /**
         * Indicates whether the value is either infinite or not a number.
         *
         * @return <code>true</code> if and only if the value is <code>NaN</code>
         * or infinite.
         */
        boolean isExceptional();

        /**
         * Indicates whether the value was rounded up during the binary to ASCII
         * conversion.
         *
         * @return <code>true</code> if and only if the value was rounded up.
         */
        boolean digitsRoundedUp();

        /**
         * Indicates whether the binary to ASCII conversion was exact.
         *
         * @return <code>true</code> if any only if the conversion was exact.
         */
        boolean decimalDigitsExact();
    }

    /**
     * A <code>BinaryToASCIIConverter</code> which represents <code>NaN</code>
     * and infinite values.
     */
    private static class ExceptionalBinaryToASCIIBuffer implements BinaryToASCIIConverter {
        private final String image;
        private final boolean isNegative;

        public ExceptionalBinaryToASCIIBuffer(String image, boolean isNegative) {
            this.image = image;
            this.isNegative = isNegative;
        }

        @Override
        public String toJavaFormatString() {
            return image;
        }

        @Override
        public void appendTo(Appendable buf) {
            if (buf instanceof StringBuilder) {
                ((StringBuilder) buf).append(image);
            } else if (buf instanceof StringBuffer) {
                ((StringBuffer) buf).append(image);
            } else {
                assert false;
            }
        }

        @Override
        public int getDecimalExponent() {
            throw new IllegalArgumentException("Exceptional value does not have an exponent");
        }

        @Override
        public int getDigits(char[] digits) {
            throw new IllegalArgumentException("Exceptional value does not have digits");
        }

        @Override
        public boolean isNegative() {
            return isNegative;
        }

        @Override
        public boolean isExceptional() {
            return true;
        }

        @Override
        public boolean digitsRoundedUp() {
            throw new IllegalArgumentException("Exceptional value is not rounded");
        }

        @Override
        public boolean decimalDigitsExact() {
            throw new IllegalArgumentException("Exceptional value is not exact");
        }
    }

    private static final String INFINITY_REP = "Infinity";
    private static final String NAN_REP = "NaN";

    private static final BinaryToASCIIConverter B2AC_POSITIVE_INFINITY = new ExceptionalBinaryToASCIIBuffer(INFINITY_REP, false);
    private static final BinaryToASCIIConverter B2AC_NEGATIVE_INFINITY = new ExceptionalBinaryToASCIIBuffer("-" + INFINITY_REP, true);
    private static final BinaryToASCIIConverter B2AC_NOT_A_NUMBER = new ExceptionalBinaryToASCIIBuffer(NAN_REP, false);
    private static final BinaryToASCIIConverter B2AC_POSITIVE_ZERO = new BinaryToASCIIBuffer(false, new char[]{'0'});
    private static final BinaryToASCIIConverter B2AC_NEGATIVE_ZERO = new BinaryToASCIIBuffer(true,  new char[]{'0'});

    /**
     * A buffered implementation of <code>BinaryToASCIIConverter</code>.
     */
    static class BinaryToASCIIBuffer implements BinaryToASCIIConverter {
        private boolean isNegative;
        private int decExponent;
        private int firstDigitIndex;
        private int nDigits;
        private final char[] digits;
        private final char[] buffer = new char[26];

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
         * <code>BinaryToASCIIBuffer</code> may be thread-local and reused
         */
        BinaryToASCIIBuffer(){
            this.digits = new char[20];
        }

        /**
         * Creates a specialized value (positive and negative zeros).
         */
        BinaryToASCIIBuffer(boolean isNegative, char[] digits){
            this.isNegative = isNegative;
            this.decExponent  = 0;
            this.digits = digits;
            this.firstDigitIndex = 0;
            this.nDigits = digits.length;
        }

        @Override
        public String toJavaFormatString() {
            int len = getChars(buffer);
            return new String(buffer, 0, len);
        }

        @Override
        public void appendTo(Appendable buf) {
            int len = getChars(buffer);
            if (buf instanceof StringBuilder) {
                ((StringBuilder) buf).append(buffer, 0, len);
            } else if (buf instanceof StringBuffer) {
                ((StringBuffer) buf).append(buffer, 0, len);
            } else {
                assert false;
            }
        }

        @Override
        public int getDecimalExponent() {
            return decExponent;
        }

        @Override
        public int getDigits(char[] digits) {
            System.arraycopy(this.digits, firstDigitIndex, digits, 0, this.nDigits);
            return this.nDigits;
        }

        @Override
        public boolean isNegative() {
            return isNegative;
        }

        @Override
        public boolean isExceptional() {
            return false;
        }

        @Override
        public boolean digitsRoundedUp() {
            return decimalDigitsRoundedUp;
        }

        @Override
        public boolean decimalDigitsExact() {
            return exactDecimalConversion;
        }

        private void setSign(boolean isNegative) {
            this.isNegative = isNegative;
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
         *
         * The only reason that we develop the digits here, rather than
         * calling on Long.toString() is that we can do it a little faster,
         * and besides want to treat trailing 0s specially. If Long.toString
         * changes, we should re-evaluate this strategy!
         */
        private void developLongDigits( int decExponent, long lvalue, int insignificantDigits ){
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
                    digits[digitno--] = (char)(c+'0');
                    decExponent++;
                    c = ivalue%10;
                    ivalue /= 10;
                }
                digits[digitno] = (char)(c+'0');
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
                    digits[digitno--] = (char)(c+'0');
                    decExponent++;
                    c = (int)(lvalue%10L);
                    lvalue /= 10;
                }
                digits[digitno] = (char)(c+'0');
            }
            this.decExponent = decExponent+1;
            this.firstDigitIndex = digitno;
            this.nDigits = this.digits.length - digitno;
        }

        private void dtoa( int binExp, long fractBits, int nSignificantBits, boolean isCompatibleFormat)
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
                        developLongDigits( 0, fractBits, insignificant );
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
            int ndigit = 0;
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
                        digits[ndigit++] = (char)('0' + q);
                    }
                    //
                    // HACK! Java spec sez that we always have at least
                    // one digit after the . in either F- or E-form output.
                    // Thus we will need more than one digit if we're using
                    // E-form
                    //
                    if ( !isCompatibleFormat ||decExp < -3 || decExp >= 8 ){
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
                        digits[ndigit++] = (char)('0' + q);
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
                        digits[ndigit++] = (char)('0' + q);
                    }
                    //
                    // HACK! Java spec sez that we always have at least
                    // one digit after the . in either F- or E-form output.
                    // Thus we will need more than one digit if we're using
                    // E-form
                    //
                    if ( !isCompatibleFormat || decExp < -3 || decExp >= 8 ){
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
                        digits[ndigit++] = (char)('0' + q);
                    }
                    lowDigitDifference = (b<<1) - tens;
                    exactDecimalConversion  = (b == 0);
                }
            } else {
                //
                // We really must do FDBigInteger arithmetic.
                // Fist, construct our FDBigInteger initial values.
                //
                FDBigInteger Sval = FDBigInteger.valueOfPow52(S5, S2);
                int shiftBias = Sval.getNormalizationBias();
                Sval = Sval.leftShift(shiftBias); // normalize so that division works better

                FDBigInteger Bval = FDBigInteger.valueOfMulPow52(fractBits, B5, B2 + shiftBias);
                FDBigInteger Mval = FDBigInteger.valueOfPow52(M5 + 1, M2 + shiftBias + 1);

                FDBigInteger tenSval = FDBigInteger.valueOfPow52(S5 + 1, S2 + shiftBias + 1); //Sval.mult( 10 );
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
                    digits[ndigit++] = (char)('0' + q);
                }
                //
                // HACK! Java spec sez that we always have at least
                // one digit after the . in either F- or E-form output.
                // Thus we will need more than one digit if we're using
                // E-form
                //
                if (!isCompatibleFormat || decExp < -3 || decExp >= 8 ){
                    high = low = false;
                }
                while( ! low && ! high ){
                    q = Bval.quoRemIteration( Sval );
                    assert q < 10 : q;  // excessively large digit
                    Mval = Mval.multBy10(); //Mval = Mval.mult( 10 );
                    low  = (Bval.cmp( Mval ) < 0);
                    high = tenSval.addAndCmp(Bval,Mval)<=0;
                    digits[ndigit++] = (char)('0' + q);
                }
                if ( high && low ){
                    Bval = Bval.leftShift(1);
                    lowDigitDifference = Bval.cmp(tenSval);
                } else {
                    lowDigitDifference = 0L; // this here only for flow analysis!
                }
                exactDecimalConversion  = (Bval.cmp( FDBigInteger.ZERO ) == 0);
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
            digits[i] = (char) (q + 1);
            decimalDigitsRoundedUp = true;
        }

        /**
         * Estimate decimal exponent. (If it is small-ish,
         * we could double-check.)
         *
         * First, scale the mantissa bits such that 1 <= d2 < 2.
         * We are then going to estimate
         *          log10(d2) ~=~  (d2-1.5)/1.5 + log(1.5)
         * and so we can estimate
         *      log10(d) ~=~ log10(d2) + binExp * log10(2)
         * take the floor and call it decExp.
         */
        static int estimateDecExp(long fractBits, int binExp) {
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

        private static int insignificantDigits(long insignificant) {
            int i;
            for ( i = 0; insignificant >= 10L; i++ ) {
                insignificant /= 10L;
            }
            return i;
        }

        /**
         * Calculates
         * <pre>
         * insignificantDigitsForPow2(v) == insignificantDigits(1L<<v)
         * </pre>
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
        private static final int[] insignificantDigitsNumber = {
            0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 3,
            4, 4, 4, 5, 5, 5, 6, 6, 6, 6, 7, 7, 7,
            8, 8, 8, 9, 9, 9, 9, 10, 10, 10, 11, 11, 11,
            12, 12, 12, 12, 13, 13, 13, 14, 14, 14,
            15, 15, 15, 15, 16, 16, 16, 17, 17, 17,
            18, 18, 18, 19
        };

        // approximately ceil( log2( long5pow[i] ) )
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
        };

        private int getChars(char[] result) {
            assert nDigits <= 19 : nDigits; // generous bound on size of nDigits
            int i = 0;
            if (isNegative) {
                result[0] = '-';
                i = 1;
            }
            if (decExponent > 0 && decExponent < 8) {
                // print digits.digits.
                int charLength = Math.min(nDigits, decExponent);
                System.arraycopy(digits, firstDigitIndex, result, i, charLength);
                i += charLength;
                if (charLength < decExponent) {
                    charLength = decExponent - charLength;
                    Arrays.fill(result,i,i+charLength,'0');
                    i += charLength;
                    result[i++] = '.';
                    result[i++] = '0';
                } else {
                    result[i++] = '.';
                    if (charLength < nDigits) {
                        int t = nDigits - charLength;
                        System.arraycopy(digits, firstDigitIndex+charLength, result, i, t);
                        i += t;
                    } else {
                        result[i++] = '0';
                    }
                }
            } else if (decExponent <= 0 && decExponent > -3) {
                result[i++] = '0';
                result[i++] = '.';
                if (decExponent != 0) {
                    Arrays.fill(result, i, i-decExponent, '0');
                    i -= decExponent;
                }
                System.arraycopy(digits, firstDigitIndex, result, i, nDigits);
                i += nDigits;
            } else {
                result[i++] = digits[firstDigitIndex];
                result[i++] = '.';
                if (nDigits > 1) {
                    System.arraycopy(digits, firstDigitIndex+1, result, i, nDigits - 1);
                    i += nDigits - 1;
                } else {
                    result[i++] = '0';
                }
                result[i++] = 'E';
                int e;
                if (decExponent <= 0) {
                    result[i++] = '-';
                    e = -decExponent + 1;
                } else {
                    e = decExponent - 1;
                }
                // decExponent has 1, 2, or 3, digits
                if (e <= 9) {
                    result[i++] = (char) (e + '0');
                } else if (e <= 99) {
                    result[i++] = (char) (e / 10 + '0');
                    result[i++] = (char) (e % 10 + '0');
                } else {
                    result[i++] = (char) (e / 100 + '0');
                    e %= 100;
                    result[i++] = (char) (e / 10 + '0');
                    result[i++] = (char) (e % 10 + '0');
                }
            }
            return i;
        }

    }

    private static final ThreadLocal<BinaryToASCIIBuffer> threadLocalBinaryToASCIIBuffer =
            new ThreadLocal<BinaryToASCIIBuffer>() {
                @Override
                protected BinaryToASCIIBuffer initialValue() {
                    return new BinaryToASCIIBuffer();
                }
            };

    private static BinaryToASCIIBuffer getBinaryToASCIIBuffer() {
        return threadLocalBinaryToASCIIBuffer.get();
    }

    /**
     * A converter which can process an ASCII <code>String</code> representation
     * of a single or double precision floating point value into a
     * <code>float</code> or a <code>double</code>.
     */
    interface ASCIIToBinaryConverter {

        double doubleValue();

        float floatValue();

    }

    /**
     * A <code>ASCIIToBinaryConverter</code> container for a <code>double</code>.
     */
    static class PreparedASCIIToBinaryBuffer implements ASCIIToBinaryConverter {
        private final double doubleVal;
        private final float floatVal;

        public PreparedASCIIToBinaryBuffer(double doubleVal, float floatVal) {
            this.doubleVal = doubleVal;
            this.floatVal = floatVal;
        }

        @Override
        public double doubleValue() {
            return doubleVal;
        }

        @Override
        public float floatValue() {
            return floatVal;
        }
    }

    static final ASCIIToBinaryConverter A2BC_POSITIVE_INFINITY = new PreparedASCIIToBinaryBuffer(Double.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    static final ASCIIToBinaryConverter A2BC_NEGATIVE_INFINITY = new PreparedASCIIToBinaryBuffer(Double.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
    static final ASCIIToBinaryConverter A2BC_NOT_A_NUMBER  = new PreparedASCIIToBinaryBuffer(Double.NaN, Float.NaN);
    static final ASCIIToBinaryConverter A2BC_POSITIVE_ZERO = new PreparedASCIIToBinaryBuffer(0.0d, 0.0f);
    static final ASCIIToBinaryConverter A2BC_NEGATIVE_ZERO = new PreparedASCIIToBinaryBuffer(-0.0d, -0.0f);

    /**
     * A buffered implementation of <code>ASCIIToBinaryConverter</code>.
     */
    static class ASCIIToBinaryBuffer implements ASCIIToBinaryConverter {
        boolean     isNegative;
        int         decExponent;
        byte[]      digits;
        int         nDigits;

        ASCIIToBinaryBuffer( boolean negSign, int decExponent, byte[] digits, int n)
        {
            this.isNegative = negSign;
            this.decExponent = decExponent;
            this.digits = digits;
            this.nDigits = n;
        }

        /**
         * Takes a FloatingDecimal, which we presumably just scanned in,
         * and finds out what its value is, as a double.
         *
         * AS A SIDE EFFECT, SET roundDir TO INDICATE PREFERRED
         * ROUNDING DIRECTION in case the result is really destined
         * for a single-precision float.
         */
        @Override
        public double doubleValue() {
            int kDigits = Math.min(nDigits, MAX_DECIMAL_DIGITS + 1);
            //
            // convert the lead kDigits to a long integer.
            //
            // (special performance hack: start to do it using int)
            int iValue = (int) digits[0] - (int) '0';
            int iDigits = Math.min(kDigits, INT_DECIMAL_DIGITS);
            for (int i = 1; i < iDigits; i++) {
                iValue = iValue * 10 + (int) digits[i] - (int) '0';
            }
            long lValue = (long) iValue;
            for (int i = iDigits; i < kDigits; i++) {
                lValue = lValue * 10L + (long) ((int) digits[i] - (int) '0');
            }
            double dValue = (double) lValue;
            int exp = decExponent - kDigits;
            //
            // lValue now contains a long integer with the value of
            // the first kDigits digits of the number.
            // dValue contains the (double) of the same.
            //

            if (nDigits <= MAX_DECIMAL_DIGITS) {
                //
                // possibly an easy case.
                // We know that the digits can be represented
                // exactly. And if the exponent isn't too outrageous,
                // the whole thing can be done with one operation,
                // thus one rounding error.
                // Note that all our constructors trim all leading and
                // trailing zeros, so simple values (including zero)
                // will always end up here
                //
                if (exp == 0 || dValue == 0.0) {
                    return (isNegative) ? -dValue : dValue; // small floating integer
                }
                else if (exp >= 0) {
                    if (exp <= MAX_SMALL_TEN) {
                        //
                        // Can get the answer with one operation,
                        // thus one roundoff.
                        //
                        double rValue = dValue * SMALL_10_POW[exp];
                        return (isNegative) ? -rValue : rValue;
                    }
                    int slop = MAX_DECIMAL_DIGITS - kDigits;
                    if (exp <= MAX_SMALL_TEN + slop) {
                        //
                        // We can multiply dValue by 10^(slop)
                        // and it is still "small" and exact.
                        // Then we can multiply by 10^(exp-slop)
                        // with one rounding.
                        //
                        dValue *= SMALL_10_POW[slop];
                        double rValue = dValue * SMALL_10_POW[exp - slop];
                        return (isNegative) ? -rValue : rValue;
                    }
                    //
                    // Else we have a hard case with a positive exp.
                    //
                } else {
                    if (exp >= -MAX_SMALL_TEN) {
                        //
                        // Can get the answer in one division.
                        //
                        double rValue = dValue / SMALL_10_POW[-exp];
                        return (isNegative) ? -rValue : rValue;
                    }
                    //
                    // Else we have a hard case with a negative exp.
                    //
                }
            }

            //
            // Harder cases:
            // The sum of digits plus exponent is greater than
            // what we think we can do with one error.
            //
            // Start by approximating the right answer by,
            // naively, scaling by powers of 10.
            //
            if (exp > 0) {
                if (decExponent > MAX_DECIMAL_EXPONENT + 1) {
                    //
                    // Lets face it. This is going to be
                    // Infinity. Cut to the chase.
                    //
                    return (isNegative) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                }
                if ((exp & 15) != 0) {
                    dValue *= SMALL_10_POW[exp & 15];
                }
                if ((exp >>= 4) != 0) {
                    int j;
                    for (j = 0; exp > 1; j++, exp >>= 1) {
                        if ((exp & 1) != 0) {
                            dValue *= BIG_10_POW[j];
                        }
                    }
                    //
                    // The reason for the weird exp > 1 condition
                    // in the above loop was so that the last multiply
                    // would get unrolled. We handle it here.
                    // It could overflow.
                    //
                    double t = dValue * BIG_10_POW[j];
                    if (Double.isInfinite(t)) {
                        //
                        // It did overflow.
                        // Look more closely at the result.
                        // If the exponent is just one too large,
                        // then use the maximum finite as our estimate
                        // value. Else call the result infinity
                        // and punt it.
                        // ( I presume this could happen because
                        // rounding forces the result here to be
                        // an ULP or two larger than
                        // Double.MAX_VALUE ).
                        //
                        t = dValue / 2.0;
                        t *= BIG_10_POW[j];
                        if (Double.isInfinite(t)) {
                            return (isNegative) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                        }
                        t = Double.MAX_VALUE;
                    }
                    dValue = t;
                }
            } else if (exp < 0) {
                exp = -exp;
                if (decExponent < MIN_DECIMAL_EXPONENT - 1) {
                    //
                    // Lets face it. This is going to be
                    // zero. Cut to the chase.
                    //
                    return (isNegative) ? -0.0 : 0.0;
                }
                if ((exp & 15) != 0) {
                    dValue /= SMALL_10_POW[exp & 15];
                }
                if ((exp >>= 4) != 0) {
                    int j;
                    for (j = 0; exp > 1; j++, exp >>= 1) {
                        if ((exp & 1) != 0) {
                            dValue *= TINY_10_POW[j];
                        }
                    }
                    //
                    // The reason for the weird exp > 1 condition
                    // in the above loop was so that the last multiply
                    // would get unrolled. We handle it here.
                    // It could underflow.
                    //
                    double t = dValue * TINY_10_POW[j];
                    if (t == 0.0) {
                        //
                        // It did underflow.
                        // Look more closely at the result.
                        // If the exponent is just one too small,
                        // then use the minimum finite as our estimate
                        // value. Else call the result 0.0
                        // and punt it.
                        // ( I presume this could happen because
                        // rounding forces the result here to be
                        // an ULP or two less than
                        // Double.MIN_VALUE ).
                        //
                        t = dValue * 2.0;
                        t *= TINY_10_POW[j];
                        if (t == 0.0) {
                            return (isNegative) ? -0.0 : 0.0;
                        }
                        t = Double.MIN_VALUE;
                    }
                    dValue = t;
                }
            }

            //
            // dValue is now approximately the result.
            // The hard part is adjusting it, by comparison
            // with FDBigInteger arithmetic.
            // Formulate the EXACT big-number result as
            // bigD0 * 10^exp
            //
            if (nDigits > MAX_NDIGITS) {
                nDigits = MAX_NDIGITS + 1;
                digits[MAX_NDIGITS] = '1';
            }
            FDBigInteger bigD0 = new FDBigInteger(lValue, digits, kDigits, nDigits);
            exp = decExponent - nDigits;

            long ieeeBits = Double.doubleToRawLongBits(dValue); // IEEE-754 bits of double candidate
            final int B5 = Math.max(0, -exp); // powers of 5 in bigB, value is not modified inside correctionLoop
            final int D5 = Math.max(0, exp); // powers of 5 in bigD, value is not modified inside correctionLoop
            bigD0 = bigD0.multByPow52(D5, 0);
            bigD0.makeImmutable();   // prevent bigD0 modification inside correctionLoop
            FDBigInteger bigD = null;
            int prevD2 = 0;

            correctionLoop:
            while (true) {
                // here ieeeBits can't be NaN, Infinity or zero
                int binexp = (int) (ieeeBits >>> EXP_SHIFT);
                long bigBbits = ieeeBits & DoubleConsts.SIGNIF_BIT_MASK;
                if (binexp > 0) {
                    bigBbits |= FRACT_HOB;
                } else { // Normalize denormalized numbers.
                    assert bigBbits != 0L : bigBbits; // doubleToBigInt(0.0)
                    int leadingZeros = Long.numberOfLeadingZeros(bigBbits);
                    int shift = leadingZeros - (63 - EXP_SHIFT);
                    bigBbits <<= shift;
                    binexp = 1 - shift;
                }
                binexp -= DoubleConsts.EXP_BIAS;
                int lowOrderZeros = Long.numberOfTrailingZeros(bigBbits);
                bigBbits >>>= lowOrderZeros;
                final int bigIntExp = binexp - EXP_SHIFT + lowOrderZeros;
                final int bigIntNBits = EXP_SHIFT + 1 - lowOrderZeros;

                //
                // Scale bigD, bigB appropriately for
                // big-integer operations.
                // Naively, we multiply by powers of ten
                // and powers of two. What we actually do
                // is keep track of the powers of 5 and
                // powers of 2 we would use, then factor out
                // common divisors before doing the work.
                //
                int B2 = B5; // powers of 2 in bigB
                int D2 = D5; // powers of 2 in bigD
                int Ulp2;   // powers of 2 in halfUlp.
                if (bigIntExp >= 0) {
                    B2 += bigIntExp;
                } else {
                    D2 -= bigIntExp;
                }
                Ulp2 = B2;
                // shift bigB and bigD left by a number s. t.
                // halfUlp is still an integer.
                int hulpbias;
                if (binexp <= -DoubleConsts.EXP_BIAS) {
                    // This is going to be a denormalized number
                    // (if not actually zero).
                    // half an ULP is at 2^-(DoubleConsts.EXP_BIAS+EXP_SHIFT+1)
                    hulpbias = binexp + lowOrderZeros + DoubleConsts.EXP_BIAS;
                } else {
                    hulpbias = 1 + lowOrderZeros;
                }
                B2 += hulpbias;
                D2 += hulpbias;
                // if there are common factors of 2, we might just as well
                // factor them out, as they add nothing useful.
                int common2 = Math.min(B2, Math.min(D2, Ulp2));
                B2 -= common2;
                D2 -= common2;
                Ulp2 -= common2;
                // do multiplications by powers of 5 and 2
                FDBigInteger bigB = FDBigInteger.valueOfMulPow52(bigBbits, B5, B2);
                if (bigD == null || prevD2 != D2) {
                    bigD = bigD0.leftShift(D2);
                    prevD2 = D2;
                }
                //
                // to recap:
                // bigB is the scaled-big-int version of our floating-point
                // candidate.
                // bigD is the scaled-big-int version of the exact value
                // as we understand it.
                // halfUlp is 1/2 an ulp of bigB, except for special cases
                // of exact powers of 2
                //
                // the plan is to compare bigB with bigD, and if the difference
                // is less than halfUlp, then we're satisfied. Otherwise,
                // use the ratio of difference to halfUlp to calculate a fudge
                // factor to add to the floating value, then go 'round again.
                //
                FDBigInteger diff;
                int cmpResult;
                boolean overvalue;
                if ((cmpResult = bigB.cmp(bigD)) > 0) {
                    overvalue = true; // our candidate is too big.
                    diff = bigB.leftInplaceSub(bigD); // bigB is not user further - reuse
                    if ((bigIntNBits == 1) && (bigIntExp > -DoubleConsts.EXP_BIAS + 1)) {
                        // candidate is a normalized exact power of 2 and
                        // is too big (larger than Double.MIN_NORMAL). We will be subtracting.
                        // For our purposes, ulp is the ulp of the
                        // next smaller range.
                        Ulp2 -= 1;
                        if (Ulp2 < 0) {
                            // rats. Cannot de-scale ulp this far.
                            // must scale diff in other direction.
                            Ulp2 = 0;
                            diff = diff.leftShift(1);
                        }
                    }
                } else if (cmpResult < 0) {
                    overvalue = false; // our candidate is too small.
                    diff = bigD.rightInplaceSub(bigB); // bigB is not user further - reuse
                } else {
                    // the candidate is exactly right!
                    // this happens with surprising frequency
                    break correctionLoop;
                }
                cmpResult = diff.cmpPow52(B5, Ulp2);
                if ((cmpResult) < 0) {
                    // difference is small.
                    // this is close enough
                    break correctionLoop;
                } else if (cmpResult == 0) {
                    // difference is exactly half an ULP
                    // round to some other value maybe, then finish
                    if ((ieeeBits & 1) != 0) { // half ties to even
                        ieeeBits += overvalue ? -1 : 1; // nextDown or nextUp
                    }
                    break correctionLoop;
                } else {
                    // difference is non-trivial.
                    // could scale addend by ratio of difference to
                    // halfUlp here, if we bothered to compute that difference.
                    // Most of the time ( I hope ) it is about 1 anyway.
                    ieeeBits += overvalue ? -1 : 1; // nextDown or nextUp
                    if (ieeeBits == 0 || ieeeBits == DoubleConsts.EXP_BIT_MASK) { // 0.0 or Double.POSITIVE_INFINITY
                        break correctionLoop; // oops. Fell off end of range.
                    }
                    continue; // try again.
                }

            }
            if (isNegative) {
                ieeeBits |= DoubleConsts.SIGN_BIT_MASK;
            }
            return Double.longBitsToDouble(ieeeBits);
        }

        /**
         * Takes a FloatingDecimal, which we presumably just scanned in,
         * and finds out what its value is, as a float.
         * This is distinct from doubleValue() to avoid the extremely
         * unlikely case of a double rounding error, wherein the conversion
         * to double has one rounding error, and the conversion of that double
         * to a float has another rounding error, IN THE WRONG DIRECTION,
         * ( because of the preference to a zero low-order bit ).
         */
        @Override
        public float floatValue() {
            int kDigits = Math.min(nDigits, SINGLE_MAX_DECIMAL_DIGITS + 1);
            //
            // convert the lead kDigits to an integer.
            //
            int iValue = (int) digits[0] - (int) '0';
            for (int i = 1; i < kDigits; i++) {
                iValue = iValue * 10 + (int) digits[i] - (int) '0';
            }
            float fValue = (float) iValue;
            int exp = decExponent - kDigits;
            //
            // iValue now contains an integer with the value of
            // the first kDigits digits of the number.
            // fValue contains the (float) of the same.
            //

            if (nDigits <= SINGLE_MAX_DECIMAL_DIGITS) {
                //
                // possibly an easy case.
                // We know that the digits can be represented
                // exactly. And if the exponent isn't too outrageous,
                // the whole thing can be done with one operation,
                // thus one rounding error.
                // Note that all our constructors trim all leading and
                // trailing zeros, so simple values (including zero)
                // will always end up here.
                //
                if (exp == 0 || fValue == 0.0f) {
                    return (isNegative) ? -fValue : fValue; // small floating integer
                } else if (exp >= 0) {
                    if (exp <= SINGLE_MAX_SMALL_TEN) {
                        //
                        // Can get the answer with one operation,
                        // thus one roundoff.
                        //
                        fValue *= SINGLE_SMALL_10_POW[exp];
                        return (isNegative) ? -fValue : fValue;
                    }
                    int slop = SINGLE_MAX_DECIMAL_DIGITS - kDigits;
                    if (exp <= SINGLE_MAX_SMALL_TEN + slop) {
                        //
                        // We can multiply fValue by 10^(slop)
                        // and it is still "small" and exact.
                        // Then we can multiply by 10^(exp-slop)
                        // with one rounding.
                        //
                        fValue *= SINGLE_SMALL_10_POW[slop];
                        fValue *= SINGLE_SMALL_10_POW[exp - slop];
                        return (isNegative) ? -fValue : fValue;
                    }
                    //
                    // Else we have a hard case with a positive exp.
                    //
                } else {
                    if (exp >= -SINGLE_MAX_SMALL_TEN) {
                        //
                        // Can get the answer in one division.
                        //
                        fValue /= SINGLE_SMALL_10_POW[-exp];
                        return (isNegative) ? -fValue : fValue;
                    }
                    //
                    // Else we have a hard case with a negative exp.
                    //
                }
            } else if ((decExponent >= nDigits) && (nDigits + decExponent <= MAX_DECIMAL_DIGITS)) {
                //
                // In double-precision, this is an exact floating integer.
                // So we can compute to double, then shorten to float
                // with one round, and get the right answer.
                //
                // First, finish accumulating digits.
                // Then convert that integer to a double, multiply
                // by the appropriate power of ten, and convert to float.
                //
                long lValue = (long) iValue;
                for (int i = kDigits; i < nDigits; i++) {
                    lValue = lValue * 10L + (long) ((int) digits[i] - (int) '0');
                }
                double dValue = (double) lValue;
                exp = decExponent - nDigits;
                dValue *= SMALL_10_POW[exp];
                fValue = (float) dValue;
                return (isNegative) ? -fValue : fValue;

            }
            //
            // Harder cases:
            // The sum of digits plus exponent is greater than
            // what we think we can do with one error.
            //
            // Start by approximating the right answer by,
            // naively, scaling by powers of 10.
            // Scaling uses doubles to avoid overflow/underflow.
            //
            double dValue = fValue;
            if (exp > 0) {
                if (decExponent > SINGLE_MAX_DECIMAL_EXPONENT + 1) {
                    //
                    // Lets face it. This is going to be
                    // Infinity. Cut to the chase.
                    //
                    return (isNegative) ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
                }
                if ((exp & 15) != 0) {
                    dValue *= SMALL_10_POW[exp & 15];
                }
                if ((exp >>= 4) != 0) {
                    int j;
                    for (j = 0; exp > 0; j++, exp >>= 1) {
                        if ((exp & 1) != 0) {
                            dValue *= BIG_10_POW[j];
                        }
                    }
                }
            } else if (exp < 0) {
                exp = -exp;
                if (decExponent < SINGLE_MIN_DECIMAL_EXPONENT - 1) {
                    //
                    // Lets face it. This is going to be
                    // zero. Cut to the chase.
                    //
                    return (isNegative) ? -0.0f : 0.0f;
                }
                if ((exp & 15) != 0) {
                    dValue /= SMALL_10_POW[exp & 15];
                }
                if ((exp >>= 4) != 0) {
                    int j;
                    for (j = 0; exp > 0; j++, exp >>= 1) {
                        if ((exp & 1) != 0) {
                            dValue *= TINY_10_POW[j];
                        }
                    }
                }
            }
            fValue = Math.clamp((float) dValue, Float.MIN_VALUE, Float.MAX_VALUE);

            //
            // fValue is now approximately the result.
            // The hard part is adjusting it, by comparison
            // with FDBigInteger arithmetic.
            // Formulate the EXACT big-number result as
            // bigD0 * 10^exp
            //
            if (nDigits > SINGLE_MAX_NDIGITS) {
                nDigits = SINGLE_MAX_NDIGITS + 1;
                digits[SINGLE_MAX_NDIGITS] = '1';
            }
            FDBigInteger bigD0 = new FDBigInteger(iValue, digits, kDigits, nDigits);
            exp = decExponent - nDigits;

            int ieeeBits = Float.floatToRawIntBits(fValue); // IEEE-754 bits of float candidate
            final int B5 = Math.max(0, -exp); // powers of 5 in bigB, value is not modified inside correctionLoop
            final int D5 = Math.max(0, exp); // powers of 5 in bigD, value is not modified inside correctionLoop
            bigD0 = bigD0.multByPow52(D5, 0);
            bigD0.makeImmutable();   // prevent bigD0 modification inside correctionLoop
            FDBigInteger bigD = null;
            int prevD2 = 0;

            correctionLoop:
            while (true) {
                // here ieeeBits can't be NaN, Infinity or zero
                int binexp = ieeeBits >>> SINGLE_EXP_SHIFT;
                int bigBbits = ieeeBits & FloatConsts.SIGNIF_BIT_MASK;
                if (binexp > 0) {
                    bigBbits |= SINGLE_FRACT_HOB;
                } else { // Normalize denormalized numbers.
                    assert bigBbits != 0 : bigBbits; // floatToBigInt(0.0)
                    int leadingZeros = Integer.numberOfLeadingZeros(bigBbits);
                    int shift = leadingZeros - (31 - SINGLE_EXP_SHIFT);
                    bigBbits <<= shift;
                    binexp = 1 - shift;
                }
                binexp -= FloatConsts.EXP_BIAS;
                int lowOrderZeros = Integer.numberOfTrailingZeros(bigBbits);
                bigBbits >>>= lowOrderZeros;
                final int bigIntExp = binexp - SINGLE_EXP_SHIFT + lowOrderZeros;
                final int bigIntNBits = SINGLE_EXP_SHIFT + 1 - lowOrderZeros;

                //
                // Scale bigD, bigB appropriately for
                // big-integer operations.
                // Naively, we multiply by powers of ten
                // and powers of two. What we actually do
                // is keep track of the powers of 5 and
                // powers of 2 we would use, then factor out
                // common divisors before doing the work.
                //
                int B2 = B5; // powers of 2 in bigB
                int D2 = D5; // powers of 2 in bigD
                int Ulp2;   // powers of 2 in halfUlp.
                if (bigIntExp >= 0) {
                    B2 += bigIntExp;
                } else {
                    D2 -= bigIntExp;
                }
                Ulp2 = B2;
                // shift bigB and bigD left by a number s. t.
                // halfUlp is still an integer.
                int hulpbias;
                if (binexp <= -FloatConsts.EXP_BIAS) {
                    // This is going to be a denormalized number
                    // (if not actually zero).
                    // half an ULP is at 2^-(FloatConsts.EXP_BIAS+SINGLE_EXP_SHIFT+1)
                    hulpbias = binexp + lowOrderZeros + FloatConsts.EXP_BIAS;
                } else {
                    hulpbias = 1 + lowOrderZeros;
                }
                B2 += hulpbias;
                D2 += hulpbias;
                // if there are common factors of 2, we might just as well
                // factor them out, as they add nothing useful.
                int common2 = Math.min(B2, Math.min(D2, Ulp2));
                B2 -= common2;
                D2 -= common2;
                Ulp2 -= common2;
                // do multiplications by powers of 5 and 2
                FDBigInteger bigB = FDBigInteger.valueOfMulPow52(bigBbits, B5, B2);
                if (bigD == null || prevD2 != D2) {
                    bigD = bigD0.leftShift(D2);
                    prevD2 = D2;
                }
                //
                // to recap:
                // bigB is the scaled-big-int version of our floating-point
                // candidate.
                // bigD is the scaled-big-int version of the exact value
                // as we understand it.
                // halfUlp is 1/2 an ulp of bigB, except for special cases
                // of exact powers of 2
                //
                // the plan is to compare bigB with bigD, and if the difference
                // is less than halfUlp, then we're satisfied. Otherwise,
                // use the ratio of difference to halfUlp to calculate a fudge
                // factor to add to the floating value, then go 'round again.
                //
                FDBigInteger diff;
                int cmpResult;
                boolean overvalue;
                if ((cmpResult = bigB.cmp(bigD)) > 0) {
                    overvalue = true; // our candidate is too big.
                    diff = bigB.leftInplaceSub(bigD); // bigB is not user further - reuse
                    if ((bigIntNBits == 1) && (bigIntExp > -FloatConsts.EXP_BIAS + 1)) {
                        // candidate is a normalized exact power of 2 and
                        // is too big (larger than Float.MIN_NORMAL). We will be subtracting.
                        // For our purposes, ulp is the ulp of the
                        // next smaller range.
                        Ulp2 -= 1;
                        if (Ulp2 < 0) {
                            // rats. Cannot de-scale ulp this far.
                            // must scale diff in other direction.
                            Ulp2 = 0;
                            diff = diff.leftShift(1);
                        }
                    }
                } else if (cmpResult < 0) {
                    overvalue = false; // our candidate is too small.
                    diff = bigD.rightInplaceSub(bigB); // bigB is not user further - reuse
                } else {
                    // the candidate is exactly right!
                    // this happens with surprising frequency
                    break correctionLoop;
                }
                cmpResult = diff.cmpPow52(B5, Ulp2);
                if ((cmpResult) < 0) {
                    // difference is small.
                    // this is close enough
                    break correctionLoop;
                } else if (cmpResult == 0) {
                    // difference is exactly half an ULP
                    // round to some other value maybe, then finish
                    if ((ieeeBits & 1) != 0) { // half ties to even
                        ieeeBits += overvalue ? -1 : 1; // nextDown or nextUp
                    }
                    break correctionLoop;
                } else {
                    // difference is non-trivial.
                    // could scale addend by ratio of difference to
                    // halfUlp here, if we bothered to compute that difference.
                    // Most of the time ( I hope ) it is about 1 anyway.
                    ieeeBits += overvalue ? -1 : 1; // nextDown or nextUp
                    if (ieeeBits == 0 || ieeeBits == FloatConsts.EXP_BIT_MASK) { // 0.0 or Float.POSITIVE_INFINITY
                        break correctionLoop; // oops. Fell off end of range.
                    }
                    continue; // try again.
                }

            }
            if (isNegative) {
                ieeeBits |= FloatConsts.SIGN_BIT_MASK;
            }
            return Float.intBitsToFloat(ieeeBits);
        }


        /**
         * All the positive powers of 10 that can be
         * represented exactly in double/float.
         */
        private static final double[] SMALL_10_POW = {
            1.0e0,
            1.0e1, 1.0e2, 1.0e3, 1.0e4, 1.0e5,
            1.0e6, 1.0e7, 1.0e8, 1.0e9, 1.0e10,
            1.0e11, 1.0e12, 1.0e13, 1.0e14, 1.0e15,
            1.0e16, 1.0e17, 1.0e18, 1.0e19, 1.0e20,
            1.0e21, 1.0e22
        };

        private static final float[] SINGLE_SMALL_10_POW = {
            1.0e0f,
            1.0e1f, 1.0e2f, 1.0e3f, 1.0e4f, 1.0e5f,
            1.0e6f, 1.0e7f, 1.0e8f, 1.0e9f, 1.0e10f
        };

        private static final double[] BIG_10_POW = {
            1e16, 1e32, 1e64, 1e128, 1e256 };
        private static final double[] TINY_10_POW = {
            1e-16, 1e-32, 1e-64, 1e-128, 1e-256 };

        private static final int MAX_SMALL_TEN = SMALL_10_POW.length-1;
        private static final int SINGLE_MAX_SMALL_TEN = SINGLE_SMALL_10_POW.length-1;

    }

    /**
     * Returns a <code>BinaryToASCIIConverter</code> for a <code>double</code>.
     * The returned object is a <code>ThreadLocal</code> variable of this class.
     *
     * @param d The double precision value to convert.
     * @return The converter.
     */
    public static BinaryToASCIIConverter getBinaryToASCIIConverter(double d) {
        return getBinaryToASCIIConverter(d, true);
    }

    /**
     * Returns a <code>BinaryToASCIIConverter</code> for a <code>double</code>.
     * The returned object is a <code>ThreadLocal</code> variable of this class.
     *
     * @param d The double precision value to convert.
     * @param isCompatibleFormat
     * @return The converter.
     */
    static BinaryToASCIIConverter getBinaryToASCIIConverter(double d, boolean isCompatibleFormat) {
        long dBits = Double.doubleToRawLongBits(d);
        boolean isNegative = (dBits&DoubleConsts.SIGN_BIT_MASK) != 0; // discover sign
        long fractBits = dBits & DoubleConsts.SIGNIF_BIT_MASK;
        int  binExp = (int)( (dBits&DoubleConsts.EXP_BIT_MASK) >> EXP_SHIFT );
        // Discover obvious special cases of NaN and Infinity.
        if ( binExp == (int)(DoubleConsts.EXP_BIT_MASK>>EXP_SHIFT) ) {
            if ( fractBits == 0L ){
                return isNegative ? B2AC_NEGATIVE_INFINITY : B2AC_POSITIVE_INFINITY;
            } else {
                return B2AC_NOT_A_NUMBER;
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
        buf.setSign(isNegative);
        // call the routine that actually does all the hard work.
        buf.dtoa(binExp, fractBits, nSignificantBits, isCompatibleFormat);
        return buf;
    }

    private static BinaryToASCIIConverter getBinaryToASCIIConverter(float f) {
        int fBits = Float.floatToRawIntBits( f );
        boolean isNegative = (fBits&FloatConsts.SIGN_BIT_MASK) != 0;
        int fractBits = fBits&FloatConsts.SIGNIF_BIT_MASK;
        int binExp = (fBits&FloatConsts.EXP_BIT_MASK) >> SINGLE_EXP_SHIFT;
        // Discover obvious special cases of NaN and Infinity.
        if ( binExp == (FloatConsts.EXP_BIT_MASK>>SINGLE_EXP_SHIFT) ) {
            if ( fractBits == 0L ){
                return isNegative ? B2AC_NEGATIVE_INFINITY : B2AC_POSITIVE_INFINITY;
            } else {
                return B2AC_NOT_A_NUMBER;
            }
        }
        // Finish unpacking
        // Normalize denormalized numbers.
        // Insert assumed high-order bit for normalized numbers.
        // Subtract exponent bias.
        int  nSignificantBits;
        if ( binExp == 0 ){
            if ( fractBits == 0 ){
                // not a denorm, just a 0!
                return isNegative ? B2AC_NEGATIVE_ZERO : B2AC_POSITIVE_ZERO;
            }
            int leadingZeros = Integer.numberOfLeadingZeros(fractBits);
            int shift = leadingZeros-(31-SINGLE_EXP_SHIFT);
            fractBits <<= shift;
            binExp = 1 - shift;
            nSignificantBits =  32 - leadingZeros; // recall binExp is  - shift count.
        } else {
            fractBits |= SINGLE_FRACT_HOB;
            nSignificantBits = SINGLE_EXP_SHIFT+1;
        }
        binExp -= FloatConsts.EXP_BIAS;
        BinaryToASCIIBuffer buf = getBinaryToASCIIBuffer();
        buf.setSign(isNegative);
        // call the routine that actually does all the hard work.
        buf.dtoa(binExp, ((long)fractBits)<<(EXP_SHIFT-SINGLE_EXP_SHIFT), nSignificantBits, true);
        return buf;
    }

    static ASCIIToBinaryConverter readDoubleSignlessDigits(int decExp, char[] digits, int length) {

        // Prevent an extreme negative exponent from causing overflow issues in doubleValue().
        // Large positive values are handled within doubleValue();
        if (decExp < MIN_DECIMAL_EXPONENT) {
            return A2BC_POSITIVE_ZERO;
        }
        byte[] buf = new byte[length];
        for (int i = 0; i < length; i++) {
            buf[i] = (byte) digits[i];
        }
        return new ASCIIToBinaryBuffer(false, decExp, buf, length);
    }

    /**
     * The input must match the {@link Double#valueOf(String) rules described here},
     * about leading and trailing whitespaces, and the grammar.
     *
     * @param in the non-null input
     * @param ix one of the {@code BINARY_<S>_IX} constants, where {@code <S>}
     *          is one of 16, 32, 64
     * @return an appropriate binary converter
     * @throws NullPointerException if the input is null
     * @throws NumberFormatException if the input is malformed
     */
    static ASCIIToBinaryConverter readJavaFormatString(String in, int ix) {
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
                return ssign != '-' ? A2BC_POSITIVE_INFINITY : A2BC_NEGATIVE_INFINITY;
            }
            if (ch == 'N') {
                scanSymbolic(in, i, NAN_REP);
                return A2BC_NOT_A_NUMBER;  // ignore sign
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
            return ssign != '-' ? A2BC_POSITIVE_ZERO : A2BC_NEGATIVE_ZERO;
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
            } else if (lz < pt) {  // lz < pt <= tnz
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
         * Integer f = <f_1 ... f_n> consists of the n decimal or hexadecimal
         * digits found in part [lz, tnz) of the input, and f_1 != 0, f_n != 0.
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
             * Let x = c 2^ep, so 2^(ep+bl-1) <= x < 2^(ep+bl)
             * When ep + bl < Q_MIN then x certainly rounds to zero.
             * When ep + bl > QE_MAX then x surely rounds to infinity.
             */
            if (ep < Q_MIN[ix] - bl) {
                return ssign != '-' ? A2BC_POSITIVE_ZERO : A2BC_NEGATIVE_ZERO;
            }
            if (ep > QE_MAX[ix] - bl) {
                return ssign != '-' ? A2BC_POSITIVE_INFINITY : A2BC_NEGATIVE_INFINITY;
            }
            int q = (int) ep;  // narrowing conversion is safe
            int shr;  // (sh)ift to (r)ight iff shr > 0
            if (q >= QE_MIN[ix] - bl) {
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
                case BINARY_32_IX ->
                    new PreparedASCIIToBinaryBuffer(Double.NaN, buildFloat(ssign, q, c));
                case BINARY_64_IX ->
                    new PreparedASCIIToBinaryBuffer(buildDouble(ssign, q, c), Float.NaN);
                default -> throw new AssertionError("unexpected");
            };
        }

        /*
         * For decimal inputs, we copy an appropriate prefix of the input and
         * rely on another method to do the (sometimes intensive) math conversion.
         *
         * Define e = n + ep, which leads to
         *      x = 0.d_1 ... d_n 10^e, 10^(e-1) <= x < 10^e
         * If e <= E_THR_Z then x rounds to zero.
         * Similarly, if e >= E_THR_I then x rounds to infinity.
         * We return immediately in these cases.
         * Otherwise, e fits in an int, aptly named e as well.
         */
        int e = Math.clamp(ep + n, E_THR_Z[ix], E_THR_I[ix]);
        if (e == E_THR_Z[ix]) {  // true e <= E_THR_Z
            return ssign != '-' ? A2BC_POSITIVE_ZERO : A2BC_NEGATIVE_ZERO;
        }
        if (e == E_THR_I[ix]) {  // true e >= E_THR_I
            return ssign != '-' ? A2BC_POSITIVE_INFINITY : A2BC_NEGATIVE_INFINITY;
        }

        /*
         * For further considerations, x also needs to be seen as
         *      x = beta 2^q
         * with real beta and integer q meeting
         *      q >= Q_MIN
         * and
         *      either  2^(P-1) <= beta < 2^P
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
         *      ql <= q <= qh, and qh - ql <= 4
         * Since by now e is relatively small, we can leverage flog2pow10().
         *
         * Consider the half-open interval [ 2^(P-1+q), 2^(P+q) ).
         * It contains all floating-point values of the form
         *      c 2^q, c integer, 2^(P-1) <= c < 2^P (normal values)
         * When q = Q_MIN also consider the interval half-open [0, 2^(P-1+q) ),
         * which contains all floating-point values of the form
         *      c 2^q, c integer, 0 <= c < 2^(P-1) (subnormal values and zero)
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
         * If n > e we pass the digits d_1...d_e 3 (3 is as good as any other
         * non-zero sticky digit) and the exponent e to the conversion routine.
         * If n <= e we pass all the digits d_1...d_n (no sticky digit,
         * as the fractional part is empty) and the exponent e to the converter.
         *
         * Now assume qh <= 0, so q <= 0.
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
         * digits of f, sticky 3, and e.
         * Otherwise, n <= e + 1 - ql.
         * We pass all n digits of f, no sticky digit, and e to the converter.
         *
         * Otherwise, ql <= 0 < qh, so -4 < q <= 4.
         * Again, since q is not known exactly, we proceed as in the previous
         * case, with ql as a safe replacement for q.
         */
        // TODO insert logic for small n: 9 for float, 18 for double
        int ql = Math.max(MathUtils.flog2pow10(e - 1) - (P[ix] - 1), Q_MIN[ix]);
        int np = e + Math.max(2 - ql, 1);
        byte[] digits = new byte[Math.min(n, np)];
        if (n >= np) {
            copyDigits(in, digits, np - 1, lz);
            digits[np - 1] = '3';  // append any non-zero sticky digit
        } else {
            copyDigits(in, digits, n, lz);
        }
        return new ASCIIToBinaryBuffer(ssign == '-', e, digits, digits.length);
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

    private static void copyDigits(String in, byte[] digits, int len, int i) {
        int ch;
        int j = 0;
        while (j < len) {
            if ((ch = in.charAt(i++)) != '.') {
                digits[j++] = (byte) ch;
            }
        }
    }

    /* Arithmetically "appends the dec digit" ch to v >= 0, clamping at 10^10. */
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
     *      Q_MIN <= q <= Q_MAX
     *      either      2^(P-1) <= c < 2^P                  (normal)
     *      or          0 < c < 2^(P-1)  &  q = Q_MIN       (subnormal)
     *      c = b_1...b_P  (b_i in [0, 2))
     *
     * Equivalently, the floating-point value can be (uniquely) expressed as
     *      m 2^ep
     * where integer qe and real f meet
     *      qe = q + P
     *      m = c 2^(-P)
     * Hence,
     *      QE_MIN = Q_MIN + P, QE_MAX = Q_MAX + P,
     *      2^(-1) <= m < 1     (normal)
     *      m < 2^(-1)          (subnormal)
     *      m = 0.b_1...b_P
     */

    /*
     * These constants are used to indicate the IEEE binary floating-point format
     * as an index (ix) to some methods and static arrays in this class.
     */
    private static final int BINARY_16_IX = 0;
    private static final int BINARY_32_IX = 1;
    private static final int BINARY_64_IX = 2;
//    private static final int BINARY_128_IX = 3;
//    private static final int BINARY_256_IX = 4;

    @Stable
    private static final int[] P = {
            11,
            FloatToDecimal.P,
            DoubleToDecimal.P,
            // 113,
            // 237,
    };

    @Stable
    private static final int[] W = {
            5,
            FloatToDecimal.W,
            DoubleToDecimal.W,
//            (1 << 4 + BINARY_128_IX) - P[BINARY_128_IX],
//            (1 << 4 + BINARY_256_IX) - P[BINARY_256_IX],
    };

    /* Minimum exponent in the m 2^e representation. */
    /* Minimum exponent in the c 2^q representation. */
    @Stable
    private static final int[] Q_MIN = {
            -24,  // Float16ToDecimal.Q_MIN,
            FloatToDecimal.Q_MIN,
            DoubleToDecimal.Q_MIN,
//            QE_MIN[BINARY_128_IX] - (P[BINARY_128_IX] - 1),
//            QE_MIN[BINARY_256_IX] - (P[BINARY_256_IX] - 1),
    };

    @Stable
    private static final int[] QE_MIN = {
            Q_MIN[BINARY_16_IX] + P[BINARY_16_IX],
            FloatToDecimal.Q_MIN + FloatToDecimal.P,
            DoubleToDecimal.Q_MIN + DoubleToDecimal.P,
//            Q_MIN[BINARY_128_IX] + P[BINARY_128_IX],
//            Q_MIN[BINARY_256_IX] + P[BINARY_256_IX],
    };

    /* Maximum exponent in the m 2^e representation. */
    @Stable
    private static final int[] QE_MAX = {
            3 - QE_MIN[BINARY_16_IX],
            FloatToDecimal.Q_MAX + FloatToDecimal.P,
            DoubleToDecimal.Q_MAX + DoubleToDecimal.P,
//            3 - QE_MIN[BINARY_128_IX],
//            3 - QE_MIN[BINARY_256_IX],
    };

    @Stable
    private static final int[] E_THR_Z = {
            -8,
            FloatToDecimal.E_THR_Z,
            DoubleToDecimal.E_THR_Z,
            // -4_966,
            // -78_985,
    };

    @Stable
    private static final int[] E_THR_I = {
            6,
            FloatToDecimal.E_THR_I,
            DoubleToDecimal.E_THR_I,
            // 4_934,
            // 78_915,
    };

    /*
     * The most significant P +1 rounding bit +1 sticky bit = P + 2 bits in a
     * hexadecimal string need up to HEX_COUNT = floor(P/4) + 2 hex digits.
     */
    @Stable
    private static final int[] HEX_COUNT = {
            P[BINARY_16_IX] / 4 + 2,
            P[BINARY_32_IX] / 4 + 2,
            P[BINARY_64_IX] / 4 + 2,
//            P[BINARY_128_IX] / 4 + 2,
//            P[BINARY_256_IX] / 4 + 2,
    };

}
