/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
/*
 *
 */

package com.foo;

import java.text.*;
import java.text.spi.*;
import java.util.*;

import com.foobar.Utils;

public class NumberFormatProviderImpl extends NumberFormatProvider {

    static Locale[] avail = {
        Locale.JAPAN,
        new Locale("ja", "JP", "osaka"),
        new Locale("ja", "JP", "kyoto"),
        new Locale("zz")};

    static String[] dialect = {
        "\u3067\u3059\u3002",
        "\u3084\u3002",
        "\u3069\u3059\u3002",
        "-zz"
    };

    static String[] patterns = {
        "#,##0.###{0};-#,##0.###{1}", // decimal pattern
        "\u00A4#,##0{0};-\u00A4#,##0{1}", // currency pattern
        "#,##0%{0}" // percent pattern
    };
    // Constants used by factory methods to specify a style of format.
    static final int NUMBERSTYLE = 0;
    static final int CURRENCYSTYLE = 1;
    static final int PERCENTSTYLE = 2;

    public Locale[] getAvailableLocales() {
        return avail;
    }

    public NumberFormat getCurrencyInstance(Locale locale) {
        for (int i = 0; i < avail.length; i ++) {
            if (Utils.supportsLocale(avail[i], locale)) {
                String pattern =
                    MessageFormat.format(patterns[CURRENCYSTYLE],
                                         dialect[i],
                                         dialect[i]);
                DecimalFormat df = new DecimalFormat(pattern,
                    DecimalFormatSymbols.getInstance(locale));
                adjustForCurrencyDefaultFractionDigits(df);
                return df;
            }
        }
        throw new IllegalArgumentException("locale is not supported: "+locale);
    }

    public NumberFormat getIntegerInstance(Locale locale) {
        for (int i = 0; i < avail.length; i ++) {
            if (Utils.supportsLocale(avail[i], locale)) {
                String pattern =
                    MessageFormat.format(patterns[NUMBERSTYLE],
                                         dialect[i],
                                         dialect[i]);
                DecimalFormat df = new DecimalFormat(pattern,
                    DecimalFormatSymbols.getInstance(locale));
                df.setMaximumFractionDigits(0);
                df.setDecimalSeparatorAlwaysShown(false);
                df.setParseIntegerOnly(true);
                return df;
            }
        }
        throw new IllegalArgumentException("locale is not supported: "+locale);
    }

    public NumberFormat getNumberInstance(Locale locale) {
        for (int i = 0; i < avail.length; i ++) {
            if (Utils.supportsLocale(avail[i], locale)) {
                String pattern =
                    MessageFormat.format(patterns[NUMBERSTYLE],
                                         dialect[i],
                                         dialect[i]);
                return new DecimalFormat(pattern,
                    DecimalFormatSymbols.getInstance(locale));
            }
        }
        throw new IllegalArgumentException("locale is not supported: "+locale);
    }

    public NumberFormat getPercentInstance(Locale locale) {
        for (int i = 0; i < avail.length; i ++) {
            if (Utils.supportsLocale(avail[i], locale)) {
                String pattern =
                    MessageFormat.format(patterns[PERCENTSTYLE],
                                         dialect[i]);
                return new DecimalFormat(pattern,
                    DecimalFormatSymbols.getInstance(locale));
            }
        }
        throw new IllegalArgumentException("locale is not supported: "+locale);
    }

    /**
     * Adjusts the minimum and maximum fraction digits to values that
     * are reasonable for the currency's default fraction digits.
     */
    void adjustForCurrencyDefaultFractionDigits(DecimalFormat df) {
        DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
        Currency currency = dfs.getCurrency();
        if (currency == null) {
            try {
                currency = Currency.getInstance(dfs.getInternationalCurrencySymbol());
            } catch (IllegalArgumentException e) {
            }
        }
        if (currency != null) {
            int digits = currency.getDefaultFractionDigits();
            if (digits != -1) {
                int oldMinDigits = df.getMinimumFractionDigits();
                // Common patterns are "#.##", "#.00", "#".
                // Try to adjust all of them in a reasonable way.
                if (oldMinDigits == df.getMaximumFractionDigits()) {
                    df.setMinimumFractionDigits(digits);
                    df.setMaximumFractionDigits(digits);
                } else {
                    df.setMinimumFractionDigits(Math.min(digits, oldMinDigits));
                    df.setMaximumFractionDigits(digits);
                }
            }
        }
    }
}
