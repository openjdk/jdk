/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4449637 8008577 8174269
 * @summary Basic acceptance test for international J2RE. Verifies that the
 * most important locale data and character converters exist and are
 * minimally functional.
 * @modules jdk.localedata
 *          jdk.charsets
 * @run main InternationalBAT
 */

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class InternationalBAT {

    public static void main(String[] args) {
        boolean pass = true;

        TimeZone tz = TimeZone.getDefault();
        try {
            pass &= testRequiredLocales();
            pass &= testRequiredEncodings();
        } finally {
            TimeZone.setDefault(tz);
        }

        if (!pass) {
            System.out.println("\nSome tests failed.\n"
                    + "If you installed the US-only J2RE for Windows, "
                    + "failures are expected and OK.\n"
                    + "If you installed the international J2RE, or any J2SDK, "
                    + "or if this occurs on any platform other than Windows, "
                    + "please file a bug report.\n"
                    + "Unfortunately, this test cannot determine whether you "
                    + "installed a US-only J2RE, an international J2RE, or "
                    + "a J2SDK.\n");
            throw new RuntimeException();
        }
    }

    // We require the "fully supported locales" for java.util and java.text:
    // http://webwork.eng/j2se/1.4/docs/guide/intl/locale.doc.html#util-text

    private static Locale[] requiredLocales = {
        Locale.of("ar", "SA"),
        Locale.CHINA,
        Locale.TAIWAN,
        Locale.of("nl", "NL"),
        Locale.of("en", "AU"),
        Locale.of("en", "CA"),
        Locale.UK,
        Locale.US,
        Locale.of("fr", "CA"),
        Locale.FRANCE,
        Locale.GERMANY,
        Locale.of("iw", "IL"),
        Locale.of("hi", "IN"),
        Locale.ITALY,
        Locale.JAPAN,
        Locale.KOREA,
        Locale.of("pt", "BR"),
        Locale.of("es", "ES"),
        Locale.of("sv", "SE"),
        Locale.of("th", "TH"),
    };

    // Date strings for May 10, 2001, for the required locales
    private static String[] requiredLocaleDates = {
        "\u0627\u0644\u062e\u0645\u064a\u0633\u060c \u0661\u0660 \u0645\u0627\u064a\u0648 \u0662\u0660\u0660\u0661",
        "2001\u5e745\u670810\u65e5\u661f\u671f\u56db",
        "2001\u5E745\u670810\u65E5 \u661F\u671F\u56DB",
        "donderdag 10 mei 2001",
        "Thursday 10 May 2001",
        "Thursday, May 10, 2001",
        "Thursday 10 May 2001",
        "Thursday, May 10, 2001",
        "jeudi 10 mai 2001",
        "jeudi 10 mai 2001",
        "Donnerstag, 10. Mai 2001",
        "\u05d9\u05d5\u05dd \u05d7\u05de\u05d9\u05e9\u05d9, 10 \u05d1\u05de\u05d0\u05d9 2001",
        "\u0917\u0941\u0930\u0941\u0935\u093e\u0930, 10 \u092e\u0908 2001",
        "gioved\u00EC 10 maggio 2001",
        "2001\u5e745\u670810\u65e5\u6728\u66dc\u65e5", // ja_JP
        "2001\uB144 5\uC6D4 10\uC77C \uBAA9\uC694\uC77C",
        "quinta-feira, 10 de maio de 2001",
        "jueves, 10 de mayo de 2001",
        "torsdag 10 maj 2001",
        "\u0e27\u0e31\u0e19\u0e1e\u0e24\u0e2b\u0e31\u0e2a\u0e1a\u0e14\u0e35\u0e17\u0e35\u0e48 10 \u0e1e\u0e24\u0e29\u0e20\u0e32\u0e04\u0e21 \u0e1e\u0e38\u0e17\u0e18\u0e28\u0e31\u0e01\u0e23\u0e32\u0e0a 2544",
    };

    private static boolean testRequiredLocales() {
        boolean pass = true;

        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        Calendar calendar = Calendar.getInstance(Locale.US);
        calendar.clear();
        calendar.set(2001, 4, 10, 12, 0, 0);
        Date date = calendar.getTime();

        Locale[] available = Locale.getAvailableLocales();
        for (int i = 0; i < requiredLocales.length; i++) {
            Locale locale = requiredLocales[i];
            boolean found = false;
            for (int j = 0; j < available.length; j++) {
                if (available[j].equals(locale)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("Locale not available: " + locale);
                pass = false;
            } else {
                DateFormat format =
                        DateFormat.getDateInstance(DateFormat.FULL, locale);
                String dateString = format.format(date);
                if (!dateString.equals(requiredLocaleDates[i])) {
                    System.out.println("Incorrect date string for locale "
                            + locale + ". Expected: " + requiredLocaleDates[i]
                            + ", got: " + dateString);
                    pass = false;
                }
            }
        }
        return pass;
    }

    // We require the encodings of the fully supported writing systems:
    // http://webwork.eng/j2se/1.4/docs/guide/intl/locale.doc.html#jfc

    private static String[] requiredEncodings = {
        "Cp1256",
        "MS936",
        "MS950",
        "Cp1255",
        "MS932",
        "MS949",
        "Cp1252",
        "MS874",
        "ISO8859_6",
        "EUC_CN",
        "UTF8",
        "GBK",
        "EUC_TW",
        "ISO8859_8",
        "EUC_JP",
        "PCK",
        "EUC_KR",
        "ISO8859_1",
        "ISO8859_15",
        "TIS620",
    };

    // one sample locale each for the required encodings

    private static Locale[] sampleLocales = {
        Locale.of("ar", "SA"),
        Locale.of("zh", "CN"),
        Locale.of("zh", "TW"),
        Locale.of("iw", "IL"),
        Locale.of("ja", "JP"),
        Locale.of("ko", "KR"),
        Locale.of("it", "IT"),
        Locale.of("th", "TH"),
        Locale.of("ar", "SA"),
        Locale.of("zh", "CN"),
        Locale.of("zh", "CN"),
        Locale.of("zh", "CN"),
        Locale.of("zh", "TW"),
        Locale.of("iw", "IL"),
        Locale.of("ja", "JP"),
        Locale.of("ja", "JP"),
        Locale.of("ko", "KR"),
        Locale.of("it", "IT"),
        Locale.of("it", "IT"),
        Locale.of("th", "TH"),
    };

    // expected conversion results for the date strings of the sample locales

    private static byte[][] expectedBytes = {
        { (byte) 0xC7, (byte) 0xE1, (byte) 0xCE, (byte) 0xE3, (byte) 0xED, (byte) 0xD3, (byte) 0xA1, 0x20, 0x3F, 0x3F, 0x20, (byte) 0xE3, (byte) 0xC7, (byte) 0xED, (byte) 0xE6, 0x20, 0x3F, 0x3F, 0x3F, 0x3F, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xC4, (byte) 0xEA, 0x35, (byte) 0xD4, (byte) 0xC2, 0x31, 0x30, (byte) 0xC8, (byte) 0xD5, (byte) 0xD0, (byte) 0xC7, (byte) 0xC6, (byte) 0xDA, (byte) 0xCB, (byte) 0xC4, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xA6, 0x7E, 0x35, (byte) 0xA4, (byte) 0xEB, 0x31, 0x30, (byte) 0xA4, (byte) 0xE9, 0x20, (byte) 0xAC, (byte)0x50, (byte) 0xB4, (byte) 0xC1, (byte) 0xA5, (byte) 0x7C},
        { (byte) 0xE9, (byte) 0xE5, (byte) 0xED, 0x20, (byte) 0xE7, (byte) 0xEE, (byte) 0xE9, (byte) 0xF9, (byte) 0xE9, 0x2C, 0x20, 0x31, 0x30, 0x20, (byte) 0xE1, (byte) 0xEE, (byte) 0xE0, (byte) 0xE9, 0x20, 0x32, 0x30, 0x30, 0x31, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0x94, 0x4E, 0x35, (byte) 0x8C, (byte) 0x8E, 0x31, 0x30, (byte) 0x93, (byte) 0xFA, (byte) 0x96, (byte) 0xD8, (byte) 0x97, 0x6A, (byte) 0x93, (byte) 0xFA, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xB3, (byte) 0xE2, 0x20, 0x35, (byte) 0xBF, (byte) 0xF9, 0x20, 0x31, 0x30, (byte) 0xC0, (byte) 0xCF, 0x20, (byte) 0xB8, (byte) 0xF1, (byte) 0xBF, (byte) 0xE4, (byte) 0xC0, (byte) 0xCF, },
        { 0x67, 0x69, 0x6F, 0x76, 0x65, 0x64, (byte) 0xEC, 0x20, 0x31, 0x30, 0x20, 0x6D, 0x61, 0x67, 0x67, 0x69, 0x6F, 0x20, 0x32, 0x30, 0x30, 0x31, },
        { (byte) 0xC7, (byte) 0xD1, (byte) 0xB9, (byte) 0xBE, (byte) 0xC4, (byte) 0xCB, (byte) 0xD1, (byte) 0xCA, (byte) 0xBA, (byte) 0xB4, (byte) 0xD5, (byte) 0xB7, (byte) 0xD5, (byte) 0xE8, 0x20, 0x31, 0x30, 0x20, (byte) 0xBE, (byte) 0xC4, (byte) 0xC9, (byte) 0xC0, (byte) 0xD2, (byte) 0xA4, (byte) 0xC1, 0x20, (byte) 0xBE, (byte) 0xD8, (byte) 0xB7, (byte) 0xB8, (byte) 0xC8, (byte) 0xD1, (byte) 0xA1, (byte) 0xC3, (byte) 0xD2, (byte) 0xAA, 0x20, 0x32, 0x35, 0x34, 0x34, },
        { (byte) 0xC7, (byte) 0xE4, (byte) 0xCE, (byte) 0xE5, (byte) 0xEA, (byte) 0xD3, (byte) 0xAC, 0x20, 0x3F, 0x3F, 0x20, (byte) 0xE5, (byte) 0xC7, (byte) 0xEA, (byte) 0xE8, 0x20, 0x3F, 0x3F, 0x3F, 0x3F, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xC4, (byte) 0xEA, 0x35, (byte) 0xD4, (byte) 0xC2, 0x31, 0x30, (byte) 0xC8, (byte) 0xD5, (byte) 0xD0, (byte) 0xC7, (byte) 0xC6, (byte) 0xDA, (byte) 0xCB, (byte) 0xC4, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xE5, (byte) 0xB9, (byte) 0xB4, 0x35, (byte) 0xE6, (byte) 0x9C, (byte) 0x88, 0x31, 0x30, (byte) 0xE6, (byte) 0x97, (byte) 0xA5, (byte) 0xE6, (byte) 0x98, (byte) 0x9F, (byte) 0xE6, (byte) 0x9C, (byte) 0x9F, (byte) 0xE5, (byte) 0x9B, (byte) 0x9B, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xC4, (byte) 0xEA, 0x35, (byte) 0xD4, (byte) 0xC2, 0x31, 0x30, (byte) 0xC8, (byte) 0xD5, (byte) 0xD0, (byte) 0xC7, (byte) 0xC6, (byte) 0xDA, (byte) 0xCB, (byte) 0xC4, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xC8, (byte) 0xA1, 0x35, (byte) 0xC5, (byte) 0xCC, 0x31, 0x30, (byte) 0xC5, (byte) 0xCA, 0x20, (byte) 0xD1, (byte) 0xD3, (byte) 0xDF, (byte) 0xE6, (byte) 0xC6, (byte) 0xBE},
        { (byte) 0xE9, (byte) 0xE5, (byte) 0xED, 0x20, (byte) 0xE7, (byte) 0xEE, (byte) 0xE9, (byte) 0xF9, (byte) 0xE9, 0x2C, 0x20, 0x31, 0x30, 0x20, (byte) 0xE1, (byte) 0xEE, (byte) 0xE0, (byte) 0xE9, 0x20, 0x32, 0x30, 0x30, 0x31, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xC7, (byte) 0xAF, 0x35, (byte) 0xB7, (byte) 0xEE, 0x31, 0x30, (byte) 0xC6, (byte) 0xFC, (byte) 0xCC, (byte) 0xDA, (byte) 0xCD, (byte) 0xCB, (byte) 0xC6, (byte) 0xFC, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0x94, 0x4E, 0x35, (byte) 0x8C, (byte) 0x8E, 0x31, 0x30, (byte) 0x93, (byte) 0xFA, (byte) 0x96, (byte) 0xD8, (byte) 0x97, 0x6A, (byte) 0x93, (byte) 0xFA, },
        { 0x32, 0x30, 0x30, 0x31, (byte) 0xB3, (byte) 0xE2, 0x20, 0x35, (byte) 0xBF, (byte) 0xF9, 0x20, 0x31, 0x30, (byte) 0xC0, (byte) 0xCF, 0x20, (byte) 0xB8, (byte) 0xF1, (byte) 0xBF, (byte) 0xE4, (byte) 0xC0, (byte) 0xCF, },
        { 0x67, 0x69, 0x6F, 0x76, 0x65, 0x64, (byte) 0xEC, 0x20, 0x31, 0x30, 0x20, 0x6D, 0x61, 0x67, 0x67, 0x69, 0x6F, 0x20, 0x32, 0x30, 0x30, 0x31, },
        { 0x67, 0x69, 0x6F, 0x76, 0x65, 0x64, (byte) 0xEC, 0x20, 0x31, 0x30, 0x20, 0x6D, 0x61, 0x67, 0x67, 0x69, 0x6F, 0x20, 0x32, 0x30, 0x30, 0x31, },
        { (byte) 0xC7, (byte) 0xD1, (byte) 0xB9, (byte) 0xBE, (byte) 0xC4, (byte) 0xCB, (byte) 0xD1, (byte) 0xCA, (byte) 0xBA, (byte) 0xB4, (byte) 0xD5, (byte) 0xB7, (byte) 0xD5, (byte) 0xE8, 0x20, 0x31, 0x30, 0x20, (byte) 0xBE, (byte) 0xC4, (byte) 0xC9, (byte) 0xC0, (byte) 0xD2, (byte) 0xA4, (byte) 0xC1, 0x20, (byte) 0xBE, (byte) 0xD8, (byte) 0xB7, (byte) 0xB8, (byte) 0xC8, (byte) 0xD1, (byte) 0xA1, (byte) 0xC3, (byte) 0xD2, (byte) 0xAA, 0x20, 0x32, 0x35, 0x34, 0x34, },
    };


    private static boolean testRequiredEncodings() {
        boolean pass = true;

        for (int i = 0; i < requiredEncodings.length; i++) {
            String encoding = requiredEncodings[i];
            Locale sampleLocale = sampleLocales[i];
            try {
                int index = 0;
                while (!sampleLocale.equals(requiredLocales[index])) {
                    index++;
                }
                byte[] out = requiredLocaleDates[index].getBytes(encoding);
                byte[] expected = expectedBytes[i];
                if (out.length != expected.length) {
                    reportConversionError(encoding, expected, out);
                    pass = false;
                } else {
                    for (int j = 0; j < out.length; j++) {
                        if (out[j] != expected[j]) {
                            reportConversionError(encoding, expected, out);
                            pass = false;
                            break;
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                System.out.println("Encoding not available: " + encoding);
                pass = false;
            }
        }
        return pass;
    }

    private static void reportConversionError(String encoding,
            byte[] expected, byte[] actual) {

        System.out.println("Incorrect conversion for encoding: " + encoding);
        System.out.println("Expected output:");
        dumpBytes(expected);
        System.out.println("Actual output:");
        dumpBytes(actual);
    }

    private static void dumpBytes(byte[] bytes) {
        System.out.print("        { ");
        for (int i = 0; i < bytes.length; i++) {
             byte b = bytes[i];
             if (b < 0) {
                 System.out.print("(byte) ");
             }
             System.out.print("0x" + toHex((b & 0x00F0) >> 4)
                     + toHex((b & 0x000F)) + ", ");
        }
        System.out.println("},");
    }

    private static char toHex(int i) {
        if (i <= 9) {
            return (char) ('0' + i);
        } else {
            return (char) ('A' + i - 10);
        }
    }
}
