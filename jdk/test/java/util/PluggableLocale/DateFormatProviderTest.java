/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

public class DateFormatProviderTest extends ProviderTest {

    com.foo.DateFormatProviderImpl dfp = new com.foo.DateFormatProviderImpl();
    List<Locale> availloc = Arrays.asList(DateFormat.getAvailableLocales());
    List<Locale> providerloc = Arrays.asList(dfp.getAvailableLocales());
    List<Locale> jreloc = Arrays.asList(LocaleData.getAvailableLocales());

    public static void main(String[] s) {
        new DateFormatProviderTest();
    }

    DateFormatProviderTest() {
        availableLocalesTest();
        objectValidityTest();
        extendedVariantTest();
        messageFormatTest();
    }

    void availableLocalesTest() {
        Set<Locale> localesFromAPI = new HashSet<Locale>(availloc);
        Set<Locale> localesExpected = new HashSet<Locale>(jreloc);
        localesExpected.addAll(providerloc);
        if (localesFromAPI.equals(localesExpected)) {
            System.out.println("availableLocalesTest passed.");
        } else {
            throw new RuntimeException("availableLocalesTest failed");
        }
    }

    void objectValidityTest() {

        for (Locale target: availloc) {
            // Get the key for the date/time patterns which is
            // specific to each calendar system.
            Calendar cal = Calendar.getInstance(target);
            String key = "DateTimePatterns";
            if (!cal.getClass().getName().equals("java.util.GregorianCalendar")) {
                // e.g., "java.util.JapaneseImperialCalendar.DateTimePatterns"
                key = cal.getClass().getName() + "." + key;
            }
            // pure JRE implementation
            ResourceBundle rb = LocaleData.getDateFormatData(target);
            boolean jreSupportsLocale = jreloc.contains(target);

            // JRE string arrays
            String[] jreDateTimePatterns = null;
            if (jreSupportsLocale) {
                try {
                    jreDateTimePatterns = (String[])rb.getObject(key);
                } catch (MissingResourceException mre) {}
            }

            for (int style = DateFormat.FULL; style <= DateFormat.SHORT; style ++) {
                // result object
                DateFormat result = DateFormat.getDateTimeInstance(style, style, target);

                // provider's object (if any)
                DateFormat providersResult = null;
                if (providerloc.contains(target)) {
                    providersResult = dfp.getDateTimeInstance(style, style, target);
                }

                // JRE's object (if any)
                DateFormat jresResult = null;
                if (jreSupportsLocale) {
                    Object[] dateTimeArgs = {jreDateTimePatterns[style],
                                             jreDateTimePatterns[style + 4]};
                    String pattern = MessageFormat.format(jreDateTimePatterns[8], dateTimeArgs);
                    jresResult = new SimpleDateFormat(pattern, target);
                }

                checkValidity(target, jresResult, providersResult, result, jreSupportsLocale);
            }
        }
    }

    // Check that fallback correctly occurs with locales with variant including '_'s
    // This test assumes that the provider supports the ja_JP_osaka locale, and JRE does not.
    void extendedVariantTest() {
        Locale[] testlocs = {new Locale("ja", "JP", "osaka_extended"),
                             new Locale("ja", "JP", "osaka_extended_further"),
                             new Locale("ja", "JP", "osaka_")};
        for (Locale test: testlocs) {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, test);
            DateFormat provider = dfp.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, test);
            if (!df.equals(provider)) {
                throw new RuntimeException("variant fallback failed. test locale: "+test);
            }
        }
    }


    private static final String[] TYPES = {
        "date",
        "time"
    };
    private static final String[] MODIFIERS = {
        "",
        "short",
        "medium", // Same as DEFAULT
        "long",
        "full"
    };

    void messageFormatTest() {
        for (Locale target : providerloc) {
            for (String type : TYPES) {
                for (String modifier : MODIFIERS) {
                    String pattern, expected;
                    if (modifier.equals("")) {
                        pattern = String.format("%s={0,%s}", type, type);
                    } else {
                        pattern = String.format("%s={0,%s,%s}", type, type, modifier);
                    }
                    if (modifier.equals("medium")) {
                        // medium is default.
                        expected = String.format("%s={0,%s}", type, type);
                    } else {
                        expected = pattern;
                    }
                    MessageFormat mf = new MessageFormat(pattern, target);
                    Format[] fmts = mf.getFormats();
                    if (fmts[0] instanceof SimpleDateFormat) {
                        continue;
                    }
                    String toPattern = mf.toPattern();
                    if (!toPattern.equals(expected)) {
                        throw new RuntimeException("messageFormatTest: got '" + toPattern
                                                   + "', expected '" + expected + "'");
                    }
                }
            }
        }
    }
}
