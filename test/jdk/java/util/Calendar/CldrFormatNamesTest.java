/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8004489 8006509 8008577 8145136 8202537 8306116
 * @summary Unit test for CLDR FormatData resources
 * @modules java.base/sun.util.locale.provider
 *          jdk.localedata
 * @compile -XDignore.symbol.file CldrFormatNamesTest.java
 * @run main/othervm -Djava.locale.providers=CLDR CldrFormatNamesTest
 */

import java.util.*;
import static java.util.Calendar.*;
import sun.util.locale.provider.*;

public class CldrFormatNamesTest {
    private static final Locale ARABIC = Locale.of("ar");
    private static final Locale ZH_HANT = Locale.forLanguageTag("zh-Hant");

    /*
     * The first element is a Locale followed by key-value pairs
     * in a FormatData resource bundle. The value type is either
     * String or String[].
     */
    static final Object[][] CLDR_DATA = {
        {
            Locale.JAPAN,
            "field.zone", "タイムゾーン",
            "java.time.japanese.DatePatterns", new String[] {
                "Gy年M月d日EEEE",
                "Gy年M月d日",
                "Gy年M月d日",
                "GGGGGy/M/d",
            },
            "java.time.roc.DatePatterns", new String[] {
                "Gy年M月d日EEEE",
                "Gy年M月d日",
                "Gy/MM/dd",
                "Gy/MM/dd",
            },
            "calendarname.buddhist", "仏暦",
        },
        {
            Locale.PRC,
            "field.zone", "时区",
            "java.time.islamic.DatePatterns", new String[] {
                "Gy年M月d日EEEE",
                "Gy年M月d日",
                "Gy年M月d日",
                "Gy/M/d",
            },
            "calendarname.islamic", "伊斯兰历",
        },
        {
            Locale.GERMANY,
            "field.dayperiod", "Tageshälfte",
            "java.time.islamic.DatePatterns", new String[] {
                "EEEE, d. MMMM y G",
                "d. MMMM y G",
                "dd.MM.y G",
                "dd.MM.yy GGGGG",
            },
            "calendarname.islamic", "Hidschri-Kalender",
        },
        {
            Locale.FRANCE,
            "field.dayperiod", "cadran",
            "java.time.islamic.DatePatterns", new String[] {
                "EEEE d MMMM y G",
                "d MMMM y G",
                "d MMM y G",
                "dd/MM/y GGGGG",
            },
            "calendarname.islamic", "calendrier hégirien",
        },
    };

    // Islamic calendar symbol names in ar
    private static final String[] ISLAMIC_MONTH_NAMES = {
        "محرم",
        "صفر",
        "ربيع الأول",
        "ربيع الآخر",
        "جمادى الأولى",
        "جمادى الآخرة",
        "رجب",
        "شعبان",
        "رمضان",
        "شوال",
        "ذو القعدة",
        "ذو الحجة",
    };
    private static final String[] ISLAMIC_ERA_NAMES = {
        "",
        "هـ",
    };

    // Minguo calendar symbol names in zh_Hant
    private static final String[] ROC_ERA_NAMES = {
        "民國前",
        "民國",
    };

    private static int errors = 0;

    // This test is CLDR data dependent.
    public static void main(String[] args) {
        for (Object[] data : CLDR_DATA) {
            Locale locale = (Locale) data[0];
            ResourceBundle rb = LocaleProviderAdapter.getResourceBundleBased()
                                    .getLocaleResources(locale).getJavaTimeFormatData();
            for (int i = 1; i < data.length; ) {
                String key = (String) data[i++];
                Object expected = data[i++];
                if (rb.containsKey(key)) {
                    Object value = rb.getObject(key);
                    if (expected instanceof String) {
                        if (!expected.equals(value)) {
                            errors++;
                            System.err.printf("error: key='%s', got '%s' expected '%s', rb: %s%n",
                                              key, value, expected, rb);
                        }
                    } else if (expected instanceof String[]) {
                        try {
                            if (!Arrays.equals((Object[]) value, (Object[]) expected)) {
                                errors++;
                                System.err.printf("error: key='%s', got '%s' expected '%s', rb: %s%n",
                                                  key, Arrays.asList((Object[])value),
                                                  Arrays.asList((Object[])expected), rb);
                            }
                        } catch (Exception e) {
                            errors++;
                            e.printStackTrace();
                        }
                    }
                } else {
                    errors++;
                    System.err.println("No resource for " + key+", rb: "+rb);
                }
            }
        }

        // test Islamic calendar names in Arabic
        testSymbolNames(ARABIC, "islamic", ISLAMIC_MONTH_NAMES, MONTH, LONG, "month");
        testSymbolNames(ARABIC, "islamic", ISLAMIC_ERA_NAMES, ERA, SHORT, "era");

        // test ROC (Minguo) calendar names in zh-Hant
        testSymbolNames(ZH_HANT, "roc", ROC_ERA_NAMES, ERA, SHORT, "era");

        if (errors > 0) {
            throw new RuntimeException("test failed");
        }
    }

    private static void testSymbolNames(Locale locale, String calType, String[] expected,
                                        int field, int style, String fieldName) {
        for (int i = 0; i < expected.length; i++) {
            String expt = expected[i];
            String name = CalendarDataUtility.retrieveJavaTimeFieldValueName(calType, field, i, style, locale);
            if (!expt.equals(name)) {
                errors++;
                System.err.printf("error: wrong %s %s name in %s: value=%d, got='%s', expected='%s'%n",
                                  calType, fieldName, locale, i, name, expt);
            }
        }
    }
}
