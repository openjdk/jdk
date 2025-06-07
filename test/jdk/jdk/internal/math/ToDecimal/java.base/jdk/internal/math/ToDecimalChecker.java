/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

import static jdk.internal.math.MathUtilsChecker.*;

/*
 * A checker for the Javadoc specification.
 * It just relies on straightforward use of (expensive) BigDecimal arithmetic.
 * Not optimized for performance.
 */
abstract class ToDecimalChecker extends BasicChecker {

    /* The string to check */
    private final String s;

    /* The decimal parsed from s is dv = (sgn c) 10^q*/
    private int sgn;
    private int q;
    private long c;

    /* The number of digits in c: 10^(l-1) <= c < 10^l */
    private int l;

    ToDecimalChecker(String s) {
        this.s = s;
    }

    private boolean conversionError(String reason) {
        return addError("toString(" + hexString() + ")" +
                " returns incorrect \"" + s + "\" (" + reason + ")");
    }

    /*
     * Returns whether s syntactically meets the expected output of
     * toString(). It is restricted to finite nonzero outputs.
     */
    private boolean failsOnParse() {
        if (s.length() > maxStringLength()) {
            return conversionError("too long");
        }
        try (StringReader r = new StringReader(s)) {
            /* 1 character look-ahead */
            int ch = r.read();

            if (ch != '-' && !isDigit(ch)) {
                return conversionError("does not start with '-' or digit");
            }

            int m = 0;
            if (ch == '-') {
                ++m;
                ch = r.read();
            }
            sgn = m > 0 ? -1 : 1;

            int i = m;
            while (ch == '0') {
                ++i;
                ch = r.read();
            }
            if (i - m > 1) {
                return conversionError("more than 1 leading '0'");
            }

            int p = i;
            while (isDigit(ch)) {
                c = 10 * c + (ch - '0');
                ++p;
                ch = r.read();
            }
            if (p == m) {
                return conversionError("no integer part");
            }
            if (i > m && p > i) {
                return conversionError("non-zero integer part with leading '0'");
            }

            int fz = p;
            if (ch == '.') {
                ++fz;
                ch = r.read();
            }
            if (fz == p) {
                return conversionError("no decimal point");
            }

            int f = fz;
            while (ch == '0') {
                c = 10 * c;
                ++f;
                ch = r.read();
            }

            int x = f;
            while (isDigit(ch)) {
                c = 10 * c + (ch - '0');
                ++x;
                ch = r.read();
            }
            if (x == fz) {
                return conversionError("no fraction");
            }
            l = p > i ? x - i - 1 : x - f;
            if (l > h()) {
                return conversionError("significand with more than " + h() + " digits");
            }
            if (x - fz > 1 && c % 10 == 0) {
                return conversionError("fraction has more than 1 digit and ends with '0'");
            }

            if (ch == 'e') {
                return conversionError("exponent indicator is 'e'");
            }
            if (ch != 'E') {
                /* Plain notation, no exponent */
                if (p - m > 7) {
                    return conversionError("integer part with more than 7 digits");
                }
                if (i > m && f - fz > 2) {
                    return conversionError("pure fraction with more than 2 leading '0'");
                }
            } else {
                if (p - i != 1) {
                    return conversionError("integer part doesn't have exactly 1 non-zero digit");
                }

                ch = r.read();
                if (ch != '-' && !isDigit(ch)) {
                    return conversionError("exponent doesn't start with '-' or digit");
                }

                int e = x + 1;
                if (ch == '-') {
                    ++e;
                    ch = r.read();
                }

                if (ch == '0') {
                    return conversionError("exponent with leading '0'");
                }

                int z = e;
                while (isDigit(ch)) {
                    q = 10 * q + (ch - '0');
                    ++z;
                    ch = r.read();
                }
                if (z == e) {
                    return conversionError("no exponent");
                }
                if (z - e > 3) {
                    return conversionError("exponent is out-of-range");
                }

                if (e > x + 1) {
                    q = -q;
                }
                if (-3 <= q && q < 7) {
                    return conversionError("exponent lies in [-3, 7)");
                }
            }
            if (ch >= 0) {
                return conversionError("extraneous characters after decimal");
            }
            q += fz - x;
        } catch (IOException ex) {
            return conversionError("unexpected exception (" +  ex.getMessage() + ")!!!");
        }
        return false;
    }

    private static boolean isDigit(int ch) {
        return '0' <= ch && ch <= '9';
    }

    private boolean addOnFail(String expected) {
        return addOnFail(s.equals(expected), "expected \"" + expected + "\"");
    }

    boolean check() {
        if (s.isEmpty()) {
            return conversionError("empty");
        }
        if (isNaN()) {
            return addOnFail("NaN");
        }
        if (isNegativeInfinity()) {
            return addOnFail("-Infinity");
        }
        if (isPositiveInfinity()) {
            return addOnFail("Infinity");
        }
        if (isMinusZero()) {
            return addOnFail("-0.0");
        }
        if (isPlusZero()) {
            return addOnFail("0.0");
        }
        if (failsOnParse()) {
            return true;
        }

        /* The exponent is bounded */
        if (eMin() > q + l || q + l > eMax()) {
            return conversionError("exponent is out-of-range");
        }

        /* s must recover v */
        try {
            if (!recovers(s)) {
                return conversionError("does not convert to the floating-point value");
            }
        } catch (NumberFormatException ex) {
            return conversionError("unexpected exception (" +  ex.getMessage() + ")!!!");
        }

        if (l < 2) {
            c *= 10;
            q -= 1;
            l += 1;
        }

        /* Get rid of trailing zeroes, still ensuring at least 2 digits */
        while (l > 2 && c % 10 == 0) {
            c /= 10;
            q += 1;
            l -= 1;
        }

        /* dv = (sgn * c) 10^q */
        if (l > 2) {
            /* Try with a number shorter than dv of lesser magnitude... */
            BigDecimal dvd = BigDecimal.valueOf(sgn * (c / 10), -(q + 1));
            if (recovers(dvd)) {
                return conversionError("\"" + dvd + "\" is shorter");
            }
            /* ... and with a number shorter than dv of greater magnitude */
            BigDecimal dvu = BigDecimal.valueOf(sgn * (c / 10 + 1), -(q + 1));
            if (recovers(dvu)) {
                return conversionError("\"" + dvu + "\" is shorter");
            }
        }

        /*
         * Check with the predecessor dvp (lesser magnitude)
         * and successor dvs (greater magnitude) of dv.
         * If |dv| < |v| dvp is not checked.
         * If |dv| > |v| dvs is not checked.
         */
        BigDecimal v = toBigDecimal();
        BigDecimal dv = BigDecimal.valueOf(sgn * c, -q);
        BigDecimal deltav = v.subtract(dv);
        if (sgn * deltav.signum() < 0) {
            /* |dv| > |v|, check dvp */
            BigDecimal dvp =
                    c == 10L
                            ? BigDecimal.valueOf(sgn * 99L, -(q - 1))
                            : BigDecimal.valueOf(sgn * (c - 1), -q);
            if (recovers(dvp)) {
                BigDecimal deltavp = dvp.subtract(v);
                if (sgn * deltavp.signum() >= 0) {
                    return conversionError("\"" + dvp + "\" is closer");
                }
                int cmp = sgn * deltav.compareTo(deltavp);
                if (cmp < 0) {
                    return conversionError("\"" + dvp + "\" is closer");
                }
                if (cmp == 0 && (c & 0x1) != 0) {
                    return conversionError("\"" + dvp + "\" is as close but has even significand");
                }
            }
        } else if (sgn * deltav.signum() > 0) {
            /* |dv| < |v|, check dvs */
            BigDecimal dvs = BigDecimal.valueOf(sgn * (c + 1), -q);
            if (recovers(dvs)) {
                BigDecimal deltavs = dvs.subtract(v);
                if (sgn * deltavs.signum() <= 0) {
                    return conversionError("\"" + dvs + "\" is closer");
                }
                int cmp = sgn * deltav.compareTo(deltavs);
                if (cmp > 0) {
                    return conversionError("\"" + dvs + "\" is closer");
                }
                if (cmp == 0 && (c & 0x1) != 0) {
                    return conversionError("\"" + dvs + "\" is as close but has even significand");
                }
            }
        }
        return false;
    }

    static int size(int p) {
        return 1 << -Integer.numberOfLeadingZeros(p);
    }

    static int w(int p) {
        return (size(p) - 1) - (p - 1);
    }

    static int q_min(int p) {
        return (-1 << (w(p) - 1)) - p + 3;
    }

    static int q_max(int p) {
        return (1 << (w(p) - 1)) - p;
    }

    static long c_min(int p) {
        return 1L << (p - 1);
    }

    static long c_max(int p) {
        return (1L << p) - 1;
    }

    /* max{e : 10^(e-1) <= v */
    static int e(BigDecimal v) {
        return flog10(v) + 1;
    }

    static int e_min(int p) {
        return e(min_value(p));
    }

    static int e_max(int p) {
        return e(max_value(p));
    }

    static int e_thr_z(int p) {
        BigDecimal THR_Z = pow2(q_min(p) - 1);
        return flog10(THR_Z);
    }

    static int e_thr_i(int p) {
        BigDecimal THR_I = BigDecimal.valueOf(2 * c_max(p) + 1)
                .multiply(pow2(q_max(p) - 1));
        return clog10(THR_I) + 1;
    }

    static int k_min(int p) {
        return flog10pow2(q_min(p));
    }

    static int k_max(int p) {
        return flog10pow2(q_max(p));
    }

    /* C_TINY = ceil(2^(-Q_MIN) 10^(K_MIN+1)) */
    static int c_tiny(int p) {
        return ceil(pow2(-q_min(p))
                .multiply(pow10(k_min(p) + 1)))
                .intValueExact();
    }

    static int h(int p) {
        return flog10pow2(p) + 2;
    }

    static BigDecimal min_value(int p) {
        return pow2(q_min(p));
    }

    static BigDecimal min_normal(int p) {
        return BigDecimal.valueOf(c_min(p))
                .multiply(pow2(q_min(p)));
    }

    static BigDecimal max_value(int p) {
        return BigDecimal.valueOf(c_max(p))
                .multiply(pow2(q_max(p)));
    }

    abstract int eMin();

    abstract int eMax();

    abstract int h();

    abstract int maxStringLength();

    abstract BigDecimal toBigDecimal();

    abstract boolean recovers(BigDecimal bd);

    abstract boolean recovers(String s);

    abstract String hexString();

    abstract boolean isNegativeInfinity();

    abstract boolean isPositiveInfinity();

    abstract boolean isMinusZero();

    abstract boolean isPlusZero();

    abstract boolean isNaN();

}
