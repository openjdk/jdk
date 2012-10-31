/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
}
