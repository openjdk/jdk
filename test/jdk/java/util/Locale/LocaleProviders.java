/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.text.*;
import java.text.spi.*;
import java.util.*;
import java.util.spi.*;
import sun.util.locale.provider.LocaleProviderAdapter;

public class LocaleProviders {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    public static void main(String[] args) {
        String methodName = args[0];

        switch (methodName) {
            case "getPlatformLocale":
                if (args[1].equals("format")) {
                    getPlatformLocale(Locale.Category.FORMAT);
                } else {
                    getPlatformLocale(Locale.Category.DISPLAY);
                }
                break;

            case "adapterTest":
                adapterTest(args[1], args[2], (args.length >= 4 ? args[3] : ""));
                break;

            case "bug7198834Test":
                bug7198834Test();
                break;

            case "tzNameTest":
                tzNameTest(args[1]);
                break;

            case "bug8001440Test":
                bug8001440Test();
                break;

            case "bug8010666Test":
                bug8010666Test();
                break;

            case "bug8013086Test":
                bug8013086Test(args[1], args[2]);
                break;

            case "bug8013903Test":
                bug8013903Test();
                break;

            case "bug8027289Test":
                bug8027289Test(args[1]);
                break;

            case "bug8220227Test":
                bug8220227Test();
                break;

            case "bug8228465Test":
                bug8228465Test();
                break;

            default:
                throw new RuntimeException("Test method '"+methodName+"' not found.");
        }
    }

    static void getPlatformLocale(Locale.Category cat) {
        Locale defloc = Locale.getDefault(cat);
        System.out.printf("%s,%s\n", defloc.getLanguage(), defloc.getCountry());
    }

    static void adapterTest(String expected, String lang, String ctry) {
        Locale testLocale = new Locale(lang, ctry);
        LocaleProviderAdapter ldaExpected =
            LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.valueOf(expected));
        if (!ldaExpected.getDateFormatProvider().isSupportedLocale(testLocale)) {
            System.out.println("test locale: "+testLocale+" is not supported by the expected provider: "+ldaExpected+". Ignoring the test.");
            return;
        }
        String preference = System.getProperty("java.locale.providers", "");
        LocaleProviderAdapter lda = LocaleProviderAdapter.getAdapter(DateFormatProvider.class, testLocale);
        LocaleProviderAdapter.Type type = lda.getAdapterType();
        System.out.printf("testLocale: %s, got: %s, expected: %s\n", testLocale, type, expected);
        if (!type.toString().equals(expected)) {
            throw new RuntimeException("Returned locale data adapter is not correct.");
        }
    }

    static void bug7198834Test() {
        LocaleProviderAdapter lda = LocaleProviderAdapter.getAdapter(DateFormatProvider.class, Locale.US);
        LocaleProviderAdapter.Type type = lda.getAdapterType();
        if (type == LocaleProviderAdapter.Type.HOST && IS_WINDOWS) {
            DateFormat df = DateFormat.getDateInstance(DateFormat.FULL, Locale.US);
            String date = df.format(new Date());
            if (date.charAt(date.length()-1) == ' ') {
                throw new RuntimeException("Windows Host Locale Provider returns a trailing space.");
            }
        } else {
            System.out.println("Windows HOST locale adapter not found. Ignoring this test.");
        }
    }

    static void tzNameTest(String id) {
        TimeZone tz = TimeZone.getTimeZone(id);
        String tzName = tz.getDisplayName(false, TimeZone.SHORT, Locale.US);
        if (tzName.startsWith("GMT")) {
            throw new RuntimeException("JRE's localized time zone name for "+id+" could not be retrieved. Returned name was: "+tzName);
        }
    }

    static void bug8001440Test() {
        Locale locale = Locale.forLanguageTag("th-TH-u-nu-hoge");
        NumberFormat nf = NumberFormat.getInstance(locale);
        String nu = nf.format(1234560);
    }

    // This test assumes Windows localized language/country display names.
    static void bug8010666Test() {
        if (IS_WINDOWS) {
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            try {
                double ver = nf.parse(System.getProperty("os.version"))
                               .doubleValue();
                System.out.printf("Windows version: %.1f\n", ver);
                if (ver >= 6.0) {
                    LocaleProviderAdapter lda =
                        LocaleProviderAdapter.getAdapter(
                            LocaleNameProvider.class, Locale.ENGLISH);
                    LocaleProviderAdapter.Type type = lda.getAdapterType();
                    if (type == LocaleProviderAdapter.Type.HOST) {
                        LocaleNameProvider lnp = lda.getLocaleNameProvider();
                        Locale mkmk = Locale.forLanguageTag("mk-MK");
                        String result = mkmk.getDisplayLanguage(Locale.ENGLISH);
                        String hostResult =
                            lnp.getDisplayLanguage(mkmk.getLanguage(),
                                                   Locale.ENGLISH);
                        System.out.printf("  Display language name for" +
                            " (mk_MK): result(HOST): \"%s\", returned: \"%s\"\n",
                            hostResult, result);
                        if (result == null ||
                            hostResult != null &&
                            !result.equals(hostResult)) {
                            throw new RuntimeException("Display language name" +
                                " mismatch for \"mk\". Returned name was" +
                                " \"" + result + "\", result(HOST): \"" +
                                hostResult + "\"");
                        }
                        result = Locale.US.getDisplayLanguage(Locale.ENGLISH);
                        hostResult =
                            lnp.getDisplayLanguage(Locale.US.getLanguage(),
                                                   Locale.ENGLISH);
                        System.out.printf("  Display language name for" +
                            " (en_US): result(HOST): \"%s\", returned: \"%s\"\n",
                            hostResult, result);
                        if (result == null ||
                            hostResult != null &&
                            !result.equals(hostResult)) {
                            throw new RuntimeException("Display language name" +
                                " mismatch for \"en\". Returned name was" +
                                " \"" + result + "\", result(HOST): \"" +
                                hostResult + "\"");
                        }
                        if (ver >= 6.1) {
                            result = Locale.US.getDisplayCountry(Locale.ENGLISH);
                            hostResult = lnp.getDisplayCountry(
                                Locale.US.getCountry(), Locale.ENGLISH);
                            System.out.printf("  Display country name for" +
                                " (en_US): result(HOST): \"%s\", returned: \"%s\"\n",
                                hostResult, result);
                            if (result == null ||
                                hostResult != null &&
                                !result.equals(hostResult)) {
                                throw new RuntimeException("Display country name" +
                                    " mismatch for \"US\". Returned name was" +
                                    " \"" + result + "\", result(HOST): \"" +
                                    hostResult + "\"");
                            }
                        }
                    } else {
                        throw new RuntimeException("Windows Host" +
                            " LocaleProviderAdapter was not selected for" +
                            " English locale.");
                    }
                }
            } catch (ParseException pe) {
                throw new RuntimeException("Parsing Windows version failed: "+pe.toString());
            }
        }
    }

    static void bug8013086Test(String lang, String ctry) {
        try {
            // Throws a NullPointerException if the test fails.
            System.out.println(new SimpleDateFormat("z", new Locale(lang, ctry)).parse("UTC"));
        } catch (ParseException pe) {
            // ParseException is fine in this test, as it's not "UTC"
}
    }

    static void bug8013903Test() {
        if (IS_WINDOWS) {
            Date sampleDate = new Date(0x10000000000L);
            String hostResult = "\u5e73\u6210 16.11.03 (Wed) AM 11:53:47";
            String jreResult = "\u5e73\u6210 16.11.03 (\u6c34) \u5348\u524d 11:53:47";
            Locale l = new Locale("ja", "JP", "JP");
            SimpleDateFormat sdf = new SimpleDateFormat("GGGG yyyy.MMM.dd '('E')' a hh:mm:ss", l);
            sdf.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            String result = sdf.format(sampleDate);
            System.out.println(result);
            if (LocaleProviderAdapter.getAdapterPreference()
                .contains(LocaleProviderAdapter.Type.JRE)) {
                if (!jreResult.equals(result)) {
                    throw new RuntimeException("Format failed. result: \"" +
                        result + "\", expected: \"" + jreResult);
                }
            } else {
                // Windows display names. Subject to change if Windows changes its format.
                if (!hostResult.equals(result)) {
                    throw new RuntimeException("Format failed. result: \"" +
                        result + "\", expected: \"" + hostResult);
                }
            }
        }
    }

    static void bug8027289Test(String expectedCodePoint) {
        if (IS_WINDOWS) {
            char[] expectedSymbol = Character.toChars(Integer.valueOf(expectedCodePoint, 16));
            NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.CHINA);
            char formatted = nf.format(7000).charAt(0);
            System.out.println("returned: " + formatted + ", expected: " + expectedSymbol[0]);
            if (formatted != expectedSymbol[0]) {
                throw new RuntimeException(
                        "Unexpected Chinese currency symbol. returned: "
                                + formatted + ", expected: " + expectedSymbol[0]);
            }
        }
    }

    static void bug8220227Test() {
        if (IS_WINDOWS) {
            Locale l = new Locale("xx","XX");
            String country = l.getDisplayCountry();
            if (country.endsWith("(XX)")) {
                throw new RuntimeException(
                        "Unexpected Region name: " + country);
            }
        }
    }

    static void bug8228465Test() {
        LocaleProviderAdapter lda = LocaleProviderAdapter.getAdapter(CalendarNameProvider.class, Locale.US);
        LocaleProviderAdapter.Type type = lda.getAdapterType();
        if (type == LocaleProviderAdapter.Type.HOST && IS_WINDOWS) {
            var names =  new GregorianCalendar()
                .getDisplayNames(Calendar.ERA, Calendar.SHORT_FORMAT, Locale.US);
            if (!names.keySet().contains("AD") ||
                names.get("AD").intValue() != 1) {
                    throw new RuntimeException(
                            "Short Era name for 'AD' is missing or incorrect");
            } else {
                System.out.println("bug8228465Test succeeded.");
            }
        }
    }
}
