/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

/**
 * @test
 * @summary StringFormat fastpath test
 */

import java.util.Formatter;
import java.util.Locale;

public class StringFormat {
    static char[] utf_chars = new char[] {
            '\u3007', '\u4e00', '\u4e8c', '\u4e09', '\u56db', '\u4e94', '\u516d', '\u4e03', '\u516b', '\u4e5d'
    };

    static String str3(String s, char c, int i) {
        int x1 = (i * 3 - 1) % 10, x2 = (i * 3)  % 10, x3 = (i * 3 + 1)  % 10;
        return s + (char) (c + x1) + (char) (c + x2) + (char) (c + x3);
    }

    static String str3_utf16(String s, int i) {
        int x1 = (i * 3 - 1) % 10, x2 = (i * 3)  % 10, x3 = (i * 3 + 1)  % 10;
        return s + utf_chars[x1] + utf_chars[x2] + utf_chars[x3];
    }

    public static void main(String[] args) {
        int n = 5;
        String[] str_args_0 = new String[n];
        String[] str_args_1 = new String[n];
        String[] str_args_utf16 = new String[n];
        long[] int_args = new long[n];

        String[] prefix = new String[n];
        String[] suffix = new String[n];
        String[] middle = new String[n];

        str_args_0[0] = "";
        str_args_1[0] = "";
        str_args_utf16[0] = "";

        prefix[0] = "";
        suffix[0] = "";
        middle[0] = "";
        int_args[0] = 1;
        for (int i = 1; i < n; i++) {
            int x1 = (i * 3 - 1) % 10, x2 = (i * 3)  % 10, x3 = (i * 3 + 1)  % 10;
            str_args_0[i] = str3(str_args_0[i - 1], 'M', i);
            str_args_1[i] = str3(str_args_1[i - 1], 'm', i);
            int_args[i] = int_args[i - 1] * 1000 + x1 * 100 + x2 * 10 + x3;

            str_args_utf16[i] = str3_utf16(str_args_utf16[i - 1], i);

            prefix[i] = str3(prefix[i - 1], 'a', i);
            suffix[i] = str3(suffix[i - 1], 'A', i);
            middle[i] = str3(middle[i - 1], 'k', i);
        }

        String[] formats_1s = new String[n * n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    String specifier = "%" + (k == 0 ? "" : Integer.toString(k)) + "s";
                    formats_1s[i * n * n + j * n + k] = prefix[i] + specifier + suffix[j];
                }
            }
        }

        for (String format : formats_1s) {
            for (String arg : str_args_0) {
                format1(format, arg);
            }

            for (long arg : int_args) {
                format1(format, arg);
            }
        }

        String[] formats_1d = new String[n * n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    String specifier = "%" + (k == 0 ? "" : Integer.toString(k * 3)) + "s";
                    formats_1d[i * n * n + j * n + k] = prefix[i] + specifier + suffix[j];
                }
            }
        }
        for (String format : formats_1d) {
            for (int i = 0; i < n; i++) {
                format1(format, str_args_0[i]);
                format1(format, str_args_utf16[i]);
                format1(format, int_args[i]);
            }
        }

        String[] formats_1d_utf16 = new String[n * n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    String specifier = utf_chars[0] + "%" + (k == 0 ? "" : Integer.toString(k * 3)) + "s";
                    formats_1d_utf16[i * n * n + j * n + k] = prefix[i] + specifier + suffix[j];
                }
            }
        }
        for (String format : formats_1d_utf16) {
            for (int i = 0; i < n; i++) {
                format1(format, str_args_0[i]);
                format1(format, str_args_utf16[i]);
                format1(format, int_args[i]);
            }
        }

        String[] formats_2_s_s = new String[n * n * n * n * n];
        for (int i0 = 0; i0 < n; i0++) {
            for (int i1 = 0; i1 < n; i1++) {
                for (int i2 = 0; i2 < n; i2++) {
                    for (int i3 = 0; i3 < n; i3++) {
                        for (int i4 = 0; i4 < n; i4++) {
                            String s0 = "%" + (i3 == 0 ? "" : Integer.toString(i3 * 3)) + "s";
                            String s1 = "%" + (i4 == 0 ? "" : Integer.toString(i4 * 3)) + "s";
                            int index = i0 * n * n * n * n
                                    + i1 * n * n * n
                                    + i2 * n * n
                                    + i3 * n
                                    + i4;
                            formats_2_s_s[index] = prefix[i0] + s0 + middle[i1] + s1 + suffix[i2];
                        }
                    }
                }
            }
        }
        for (String format : formats_2_s_s) {
            for (int i = 0; i < str_args_0.length; i++) {
                for (int j = 0; j < str_args_0.length; j++) {
                    format2(format, str_args_0[i], str_args_0[j]);
                    format2(format, int_args[i], int_args[j]);
                }
            }
        }

        String[] formats_2_s_d = new String[n * n * n * n * n];
        for (int i0 = 0; i0 < n; i0++) {
            for (int i1 = 0; i1 < n; i1++) {
                for (int i2 = 0; i2 < n; i2++) {
                    for (int i3 = 0; i3 < n; i3++) {
                        for (int i4 = 0; i4 < n; i4++) {
                            String s0 = "%" + (i3 == 0 ? "" : Integer.toString(i3 * 3)) + "s";
                            String s1 = "%" + (i4 == 0 ? "" : Integer.toString(i4 * 3)) + "d";
                            int index = i0 * n * n * n * n
                                    + i1 * n * n * n
                                    + i2 * n * n
                                    + i3 * n
                                    + i4;
                            formats_2_s_d[index] = prefix[i0] + s0 + middle[i1] + s1 + suffix[i2];
                        }
                    }
                }
            }
        }
        for (String format : formats_2_s_d) {
            for (int i = 0; i < str_args_0.length; i++) {
                for (int j = 0; j < str_args_0.length; j++) {
                    format2(format, str_args_0[i], int_args[j]);
                }
            }
        }

        String[] formats_2_d_s = new String[n * n * n * n * n];
        for (int i0 = 0; i0 < n; i0++) {
            for (int i1 = 0; i1 < n; i1++) {
                for (int i2 = 0; i2 < n; i2++) {
                    for (int i3 = 0; i3 < n; i3++) {
                        for (int i4 = 0; i4 < n; i4++) {
                            String s0 = "%" + (i3 == 0 ? "" : Integer.toString(i3 * 3)) + "d";
                            String s1 = "%" + (i4 == 0 ? "" : Integer.toString(i4 * 3)) + "s";
                            int index = i0 * n * n * n * n
                                    + i1 * n * n * n
                                    + i2 * n * n
                                    + i3 * n
                                    + i4;
                            formats_2_d_s[index] = prefix[i0] + s0 + middle[i1] + s1 + suffix[i2];
                        }
                    }
                }
            }
        }
        for (String format : formats_2_d_s) {
            for (int i = 0; i < str_args_0.length; i++) {
                for (int j = 0; j < str_args_0.length; j++) {
                    format2(format, int_args[i], str_args_0[j]);
                }
            }
        }

        String[] formats_2_d_d = new String[n * n * n * n * n];
        for (int i0 = 0; i0 < n; i0++) {
            for (int i1 = 0; i1 < n; i1++) {
                for (int i2 = 0; i2 < n; i2++) {
                    for (int i3 = 0; i3 < n; i3++) {
                        for (int i4 = 0; i4 < n; i4++) {
                            String s0 = "%" + (i3 == 0 ? "" : Integer.toString(i3 * 3)) + "d";
                            String s1 = "%" + (i4 == 0 ? "" : Integer.toString(i4 * 3)) + "d";
                            int index = i0 * n * n * n * n
                                    + i1 * n * n * n
                                    + i2 * n * n
                                    + i3 * n
                                    + i4;
                            formats_2_d_d[index] = prefix[i0] + s0 + middle[i1] + s1 + suffix[i2];
                        }
                    }
                }
            }
        }

        for (String format : formats_2_d_d) {
            for (int i = 0; i < str_args_0.length; i++) {
                for (int j = 0; j < str_args_0.length; j++) {
                    format2(format, int_args[i], int_args[j]);
                }
            }
        }

        locales(() -> {
            for (int i = 0; i < n; i++) {
                format2("%s%d", str_args_0[i], int_args[i]);
                format2("%s%d", str_args_0[i], int_args[i]);
                format2("%s%x", str_args_0[i], int_args[i]);
                format2("%s%X", str_args_0[i], int_args[i]);

                format2(utf_chars[0] + "%s%d", str_args_0[i], int_args[i]);
                format2(utf_chars[0] + "%s%d", str_args_0[i], int_args[i]);
                format2(utf_chars[0] + "%s%x", str_args_0[i], int_args[i]);
                format2(utf_chars[0] + "%s%X", str_args_0[i], int_args[i]);
                format2("%s %d" + utf_chars[0], str_args_0[i], int_args[i]);
                format2("%s %d" + utf_chars[0], str_args_0[i], int_args[i]);
                format2("%s %x" + utf_chars[0], str_args_0[i], int_args[i]);
                format2("%s %X" + utf_chars[0], str_args_0[i], int_args[i]);

                format2("%s%d", formats_1d_utf16[i], int_args[i]);
                format2("%s%d", formats_1d_utf16[i], int_args[i]);
                format2("%s%x", formats_1d_utf16[i], int_args[i]);
                format2("%s%X", formats_1d_utf16[i], int_args[i]);
            }
        });

        format2("%s%", "", 12);
        format2("%3", "", 12);
        format2("%s%3", "", 12);
        format2("%s%33%", "", 12);

        format1("a%n");
        format1("a%nb");
        format1("%nb");
        format1(utf_chars[0] + "%n");
        format1(utf_chars[0] + "%n" + utf_chars[0]);
        format1("%n" + utf_chars[0]);

        format1(utf_chars[0] + "%s%n", str_args_0[0]);
        format1(utf_chars[0] + "%s%n", formats_1d_utf16[0]);
        format1(utf_chars[0] + "%n%s", str_args_0[0]);
        format1(utf_chars[0] + "%n%s", formats_1d_utf16[0]);
    }

    static void locales(Runnable r) {
        Locale defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale[] locales = Locale.getAvailableLocales();
        for (Locale locale : locales) {
            Locale.setDefault(Locale.Category.FORMAT, locale);
            r.run();
        }
        Locale.setDefault(Locale.Category.FORMAT, defaultLocale);
    }

    private static void format1(String format) {
        String juf = new Formatter().format(format).toString();
        String strf = format.formatted();
        assertEquals(juf, strf);
    }

    private static void format1(String format, String arg) {
        String juf = new Formatter().format(format, arg).toString();
        String strf = format.formatted(arg);
        assertEquals(juf, strf);
    }

    private static void format2(String format, Object arg0, Object arg1) {
        RuntimeException jue = null;
        String juf = null;
        try {
            juf = new Formatter().format(format, arg0, arg1).toString();
        } catch (RuntimeException e) {
            jue = e;
        }

        RuntimeException stre = null;
        String strf = null;
        try {
            strf = format.formatted(arg0, arg1);
        } catch (RuntimeException e) {
            stre = e;
        }

        if (jue != null && stre == null) {
            throw jue;
        }

        if (jue == null && stre != null) {
            throw stre;
        }

        if (jue != null && stre != null) {
            return;
        }

        assertEquals(juf, strf);
    }

    private static void format1(String format, long arg) {
        {
            byte byteArg = (byte) arg;
            String juf = new Formatter().format(format, byteArg).toString();
            String strf = format.formatted(byteArg);
            assertEquals(juf, strf);
        }
        {
            short shortArg = (short) arg;
            String juf = new Formatter().format(format, shortArg).toString();
            String strf = format.formatted(shortArg);
            assertEquals(juf, strf);
        }
        {
            int intArg = (int) arg;
            String juf = new Formatter().format(format, intArg).toString();
            String strf = format.formatted(intArg);
            assertEquals(juf, strf);
        }
        {
            String juf = new Formatter().format(format, arg).toString();
            String strf = format.formatted(arg);
            assertEquals(juf, strf);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new RuntimeException("Expected " + expected + " but got " + actual);
        }
    }
}
