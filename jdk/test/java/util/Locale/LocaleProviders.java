/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
        if (type == LocaleProviderAdapter.Type.HOST && System.getProperty("os.name").startsWith("Windows")) {
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
        if (System.getProperty("os.name").startsWith("Windows")) {
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            try {
                double ver = nf.parse(System.getProperty("os.version")).doubleValue();
                System.out.printf("Windows version: %.1f\n", ver);
                if (ver >= 6.0) {
                    LocaleProviderAdapter lda = LocaleProviderAdapter.getAdapter(LocaleNameProvider.class, Locale.ENGLISH);
                    LocaleProviderAdapter.Type type = lda.getAdapterType();
                    if (type == LocaleProviderAdapter.Type.HOST) {
                        Locale mkmk = Locale.forLanguageTag("mk-MK");
                        String result = mkmk.getDisplayLanguage(Locale.ENGLISH);
                        if (!"Macedonian (FYROM)".equals(result)) {
                            throw new RuntimeException("Windows locale name provider did not return expected localized language name for \"mk\". Returned name was \"" + result + "\"");
                        }
                        result = Locale.US.getDisplayLanguage(Locale.ENGLISH);
                        if (!"English".equals(result)) {
                            throw new RuntimeException("Windows locale name provider did not return expected localized language name for \"en\". Returned name was \"" + result + "\"");
                        }
                        result = Locale.US.getDisplayCountry(Locale.ENGLISH);
                        if (ver >= 6.1 && !"United States".equals(result)) {
                            throw new RuntimeException("Windows locale name provider did not return expected localized country name for \"US\". Returned name was \"" + result + "\"");
                        }
                    } else {
                        throw new RuntimeException("Windows Host LocaleProviderAdapter was not selected for English locale.");
                    }
                }
            } catch (ParseException pe) {
                throw new RuntimeException("Parsing Windows version failed: "+pe.toString());
            }
        }
    }
}
