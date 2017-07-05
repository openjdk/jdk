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

import java.text.*;
import java.util.*;
import sun.util.*;
import sun.util.resources.*;

public class CurrencyNameProviderTest extends ProviderTest {

    public static void main(String[] s) {
        new CurrencyNameProviderTest();
    }

    CurrencyNameProviderTest() {
        test1();
        test2();
    }

    void test1() {
        com.bar.CurrencyNameProviderImpl cnp = new com.bar.CurrencyNameProviderImpl();
        Locale[] availloc = Locale.getAvailableLocales();
        Locale[] testloc = availloc.clone();
        List<Locale> providerloc = Arrays.asList(cnp.getAvailableLocales());

        for (Locale target: availloc) {
            // pure JRE implementation
            OpenListResourceBundle rb = (OpenListResourceBundle)LocaleData.getCurrencyNames(target);
            boolean jreHasBundle = rb.getLocale().equals(target);

            for (Locale test: testloc) {
                // get a Currency instance
                Currency c = null;
                try {
                    c = Currency.getInstance(test);
                } catch (IllegalArgumentException iae) {}

                if (c == null) {
                    continue;
                }

                // the localized symbol for the target locale
                String currencyresult = c.getSymbol(target);

                // the localized name for the target locale
                String nameresult = c.getDisplayName(target);

                // provider's name (if any)
                String providerscurrency = null;
                String providersname = null;
                if (providerloc.contains(target)) {
                    providerscurrency = cnp.getSymbol(c.getCurrencyCode(), target);
                    providersname = cnp.getDisplayName(c.getCurrencyCode(), target);
                }

                // JRE's name (if any)
                String jrescurrency = null;
                String jresname = null;
                String key = c.getCurrencyCode();
                String nameKey = key.toLowerCase(Locale.ROOT);
                if (jreHasBundle) {
                    try {
                        jrescurrency = rb.getString(key);
                    } catch (MissingResourceException mre) {
                        // JRE does not have any resource, "jrescurrency" should remain null
                    }
                    try {
                        jresname = rb.getString(nameKey);
                    } catch (MissingResourceException mre) {
                        // JRE does not have any resource, "jresname" should remain null
                    }
                }

                checkValidity(target, jrescurrency, providerscurrency, currencyresult, jrescurrency!=null);
                checkValidity(target, jresname, providersname, nameresult,
                              jreHasBundle && rb.handleGetKeys().contains(nameKey));
            }
        }
    }


    final String pattern = "###,###\u00A4";
    final String YEN_IN_OSAKA = "100,000\u5186\u3084\u3002";
    final String YEN_IN_KYOTO = "100,000\u5186\u3069\u3059\u3002";
    final Locale OSAKA = new Locale("ja", "JP", "osaka");
    final Locale KYOTO = new Locale("ja", "JP", "kyoto");
    Integer i = new Integer(100000);
    String formatted;
    DecimalFormat df;

    void test2() {
        try {
            df = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(OSAKA));
            System.out.println(formatted = df.format(i));
            if(!formatted.equals(YEN_IN_OSAKA)) {
                throw new RuntimeException("formatted zone names mismatch. " +
                    "Should match with " + YEN_IN_OSAKA);
            }

            df.parse(YEN_IN_OSAKA);

            Locale.setDefault(KYOTO);
            df = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance());
            System.out.println(formatted = df.format(i));
            if(!formatted.equals(YEN_IN_KYOTO)) {
                throw new RuntimeException("formatted zone names mismatch. " +
                    "Should match with " + YEN_IN_KYOTO);
            }

            df.parse(YEN_IN_KYOTO);
        } catch (ParseException pe) {
            throw new RuntimeException("parse error occured" + pe);
        }
    }
}
