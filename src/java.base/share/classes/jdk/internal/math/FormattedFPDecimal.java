/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This class provides support for the 'e', 'f' and 'g' conversions on double
 * values with sign bit 0.
 * It is worth noting that float values are converted to double values _before_
 * control reaches code in this class.
 *
 * It delegates the conversion to decimal to class DoubleToDecimal to get
 * the decimal d selected by Double.toString(double) as a pair of integers
 * f and e meeting d = f 10^e.
 * It then rounds d to the appropriate number of digits, as per specification,
 * and extracts the digits of both the significand and, where required, the
 * exponent of the rounded value.
 *
 * Further processing like padding, sign, grouping, localization, etc., is the
 * responsibility of the caller.
 */
public final class FormattedFPDecimal {

    public static final char SCIENTIFIC = 'e';
    public static final char PLAIN      = 'f';
    public static final char GENERAL    = 'g';

    private long f;
    private int e;  // normalized to 0 when f = 0
    private int n;
    private char[] digits;  // ... and often the decimal separator as well
    private char[] exp;  // [+-][e]ee, that is, sign and minimum 2 digits

    private FormattedFPDecimal() {
    }

    public static FormattedFPDecimal valueOf(double v, int prec, char form) {
        FormattedFPDecimal fd = split(v);
        return switch (form) {
            case SCIENTIFIC -> fd.scientific(prec);
            case PLAIN      -> fd.plain(prec);
            case GENERAL    -> fd.general(prec);
            default         -> throw new IllegalArgumentException(
                    String.format("unsupported form '%c'", form)
            );
        };
    }

    private static FormattedFPDecimal split(double v) {
        FormattedFPDecimal fd = new FormattedFPDecimal();
        DoubleToDecimal.split(v, fd);
        return fd;
    }

    /**
     * Returns a FormattedFPDecimal with the appropriate precision for
     * {@link Double#toString(double)}.
     *
     * @see java.math.BigDecimal#valueOf(double)
     */
    public static FormattedFPDecimal valueForDoubleToString(double v) {
        final FormattedFPDecimal fd = split(v);
        final int expR = fd.getExponentRounded();

        // Adjust precision, following rules for Double.toString. There is
        // always at least one digit and some cases require an extra one to
        // force a digit after the decimal. No additional rounding is performed;
        // no significant trailing digits are removed.

        final int targetPrec =
                // No extra trailing digit needed
                (-3 <= expR && expR < 0) ? 1

                // Keep digits to left of decimal, plus leave a trailing zero
                : (0 <= expR && expR < 7) ? expR + 2 :

                // Otherwise, require at least 2 digits, to include trailing
                // digit when there is a single digit
                2;


        long s = fd.f;
        int prec = fd.n;

        if (prec < targetPrec) {
            // Add zeros needed to reach target precision
            final int addZeros = targetPrec - prec;
            s *= MathUtils.pow10(addZeros); // addZeros will be at most 8
            prec = targetPrec;
        } else {
            // Remove trailing zeros to try to reach target precision
            while (prec > targetPrec && s % 10 == 0) {
                s = s / 10;
                prec--;
            }
        }

        // Calculate new e based on updated precision
        final int eNew = expR - prec + 1;  // expR is defined as prec + e - 1
        fd.set(s, eNew, prec);

        return fd;
    }

    public long getSignificand() {
        return f;
    }

    public int getPrecision() {
        return n;
    }

    public int getExp() {
        return e;
    }

    public void set(long f, int e, int n) {
        /* Initially, n = 0 if f = 0, and 10^{n-1} <= f < 10^n if f != 0 */
        this.f = f;
        this.e = e;
        this.n = n;
    }

    public char[] getExponent() {
        return exp;
    }

    public char[] getMantissa() {
        return digits;
    }

    public int getExponentRounded() {
        return n + e - 1;
    }

    private FormattedFPDecimal plain(int prec) {
        /*
         * Rounding d = f 10^e to prec digits in plain mode means the same
         * as rounding it to the p = n + e + prec most significand digits of d,
         * with the understanding that p < 0 cuts off all its digits.
         */
        round(n + e + (long) prec);  // n + e is well inside the int range
        return plainChars();
    }

    private FormattedFPDecimal plainChars() {
        if (e >= 0) {
            plainCharsPureInteger();
        } else if (n + e > 0) {
            plainCharsMixed();
        } else {
            plainCharsPureFraction();
        }
        return this;
    }

    private void plainCharsPureInteger() {
        digits = new char[n + e];
        fillWithZeros(n, n + e);
        fillWithDigits(f, 0, n);
    }

    private void plainCharsMixed() {
        digits = new char[n + 1];
        long x = fillWithDigits(f, n + 1 + e, n + 1);
        digits[n + e] = '.';
        fillWithDigits(x, 0, n + e);
    }

    private void plainCharsPureFraction() {
        digits = new char[2 - e];
        long x = f;
        fillWithDigits(x, 2 - e - n, 2 - e);
        fillWithZeros(0, 2 - e - n);
        digits[1] = '.';
    }

    private FormattedFPDecimal scientific(int prec) {
        /*
         * Rounding d = f 10^e to prec digits in scientific mode means the same
         * as rounding it to the p = prec + 1 most significand digits of d.
         */
        round(prec + 1L);
        return scientificChars(prec);
    }

    private FormattedFPDecimal scientificChars(int prec) {
        if (prec != 0) {
            scientificCharsWithFraction();
        } else {
            scientificCharsNoFraction();
        }
        expChars();
        return this;
    }

    private void scientificCharsWithFraction() {
        digits = new char[1 + n];  // room for leading digit and for '.'
        long x = fillWithDigits(f, 2, 1 + n);
        digits[1] = '.';
        digits[0] = toDigit(x);
    }

    private void scientificCharsNoFraction() {
        digits = new char[1];
        digits[0] = toDigit(f);
    }

    private FormattedFPDecimal general(int prec) {
        /*
         * Rounding d = f 10^e to prec digits in general mode means the same
         * as rounding it to the p = prec most significand digits of d, and then
         * deciding whether to format it in plain or scientific mode, depending
         * on the rounded value.
         */
        round(prec);
        int er = getExponentRounded();
        if (-4 <= er && er < prec) {
            plainChars();
        } else {
            scientificChars(prec - 1);
        }
        return this;
    }

    private void expChars() {
        int er = getExponentRounded();
        int aer = Math.abs(er);
        exp = new char[aer >= 100 ? 4 : 3];
        int q;
        if (aer >= 100) {
            q = aer / 10;
            exp[3] = toDigit(aer - 10 * q);
            aer = q;
        }
        q = aer / 10;
        exp[2] = toDigit(aer - 10 * q);
        exp[1] = toDigit(q);
        exp[0] = er >= 0 ? '+' : '-';
    }

    private void round(long pp) {
        /*
         * Let d = f 10^e, and let p shorten pp.
         * This method rounds d to the p most significant digits.
         * It does so by possibly modifying f, e and n.
         * When f becomes 0, e and n are normalized to 0 and 1, resp.
         *
         * For any real x let
         *      r(x) = floor(x + 1/2)
         * which is rounding to the closest integer, with ties rounded toward
         * positive infinity.
         *
         * When f = 0 there's not much to say, except that this holds iff n = 0.
         *
         * Otherwise, since
         *      10^{n-1} <= f < 10^n
         * it follows that
         *      10^{e+n-1} <= d < 10^{e+n}
         * To round d to the most significant p digits, first scale d to the
         * range [10^{p-1}, 10^p), cutoff the fractional digits by applying r,
         * and finally scale back.
         * To this end, first define
         *      ds = d 10^{p-e-n}
         * which ensures
         *      10^{p-1} <= ds < 10^p
         *
         * Now, if p < 0 (that is, if p <= -1) then
         *      ds < 10^p <= 10^{-1} < 1/2
         * so that
         *      r(ds) = 0
         * Thus, rounding d to p < 0 digits leads to 0.
         */
        if (n == 0 || pp < 0) {
            f = 0;
            e = 0;
            n = 1;
            return;
        }

        /*
         * Further, if p >= n then
         *      ds = f 10^e 10^{p-e-n} = f 10^{p-n}
         * which shows that ds is an integer, so r(ds) = ds. That is,
         * rounding to p >= n digits leads to a result equal to d.
         */
        if (pp >= n) {  // no rounding needed
            return;
        }

        /*
         * Finally, 0 <= p < n. When p = 0 it follows that
         *      10^{-1} <= ds < 1
         *      0 <= f' = r(ds) <= 1
         * that is, f' is either 0 or 1.
         *
         * Otherwise
         *      10^{p-1} <= ds < 10^p
         *      1 <= 10^{p-1} <= f' = r(ds) <= 10^p
         * Note that f' = 10^p is a possible outcome.
         *
         * Scale back, where e' = e + n - p
         *      d' = f' 10^{e+n-p} = f' 10^e', with 10^{e+n-1} <= d' <= 10^{e+n}
         *
         * Since n > p, f' can be computed in integer arithmetic as follows,
         * where / denotes division in the real numbers:
         *      f' = r(ds) = r(f 10^{p-n}) = r(f / 10^{n-p})
         *          = floor(f / 10^{n-p} + 1/2)
         *          = floor((f + 10^{n-p}/2) / 10^{n-p})
         */
        int p = (int) pp;  // 0 <= pp < n, safe cast
        e += n - p;  // new e is well inside the int range
        long pow10 = MathUtils.pow10(n - p);
        f = (f + (pow10 >> 1)) / pow10;
        if (p == 0) {
            n = 1;
            if (f == 0) {
                e = 0;
            }
            return;
        }

        n = p;
        if (f == MathUtils.pow10(p)) {
            /*
             * f is n + 1 digits long.
             * Absorb one trailing zero into e and reduce f accordingly.
             */
            f /= 10;
            e += 1;
        }
    }

    /*
     * Fills the digits section with indices in [from, to) with the lower
     * to - from digits of x (as chars), while stripping them away from x.
     * Returns the stripped x.
     */
    private long fillWithDigits(long x, int from, int to) {
        while (to > from) {
            long q = x / 10;
            digits[--to] = toDigit(x - q * 10);
            x = q;
        }
        return x;
    }

    /*
     * Fills the digits section with indices in [from, to) with '0'.
     */
    private void fillWithZeros(int from, int to) {
        while (to > from) {
            digits[--to] = '0';
        }
    }

    private static char toDigit(long d) {
        return toDigit((int) d);
    }

    private static char toDigit(int d) {
        return (char) (d + '0');
    }

}
