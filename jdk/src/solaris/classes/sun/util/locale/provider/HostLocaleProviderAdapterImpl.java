/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.util.locale.provider;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.spi.DateFormatProvider;
import java.util.Locale;

/**
 * LocaleProviderAdapter implementation for the Unix locale data
 *
 * @author Naoto Sato
 */
public class HostLocaleProviderAdapterImpl {
    static Locale[] supported = {Locale.getDefault(Locale.Category.FORMAT)};

    public static DateFormatProvider getDateFormatProvider() {
        return new DateFormatProvider() {
            String posixPattern;

            @Override
            public Locale[] getAvailableLocales() {
                // not implemented yet
                return new Locale[0];
            }
            @Override
            public DateFormat getDateInstance(int style, Locale locale) {
                posixPattern = getPattern(style, -1, locale.toLanguageTag());
                return new SimpleDateFormat(convertPosixToJava(posixPattern), locale);
            }
            @Override
            public DateFormat getTimeInstance(int style, Locale locale) {
                posixPattern = getPattern(-1, style, locale.toLanguageTag());
                return new SimpleDateFormat(convertPosixToJava(posixPattern), locale);
            }
            @Override
            public DateFormat getDateTimeInstance(int dateStyle,
                    int timeStyle, Locale locale) {
                posixPattern = getPattern(dateStyle, timeStyle, locale.toLanguageTag());
                return new SimpleDateFormat(convertPosixToJava(posixPattern), locale);
            }
        };
    }

    private static String convertPosixToJava(String posixPattern) {
        StringBuilder sb = new StringBuilder();
        boolean conversion = false;

        for (int index = 0; index < posixPattern.length(); index++) {
            char c = posixPattern.charAt(index);
            if (conversion) {
                switch (c) {
                case 'a':
                    sb.append("EEE");
                    break;
                case 'b':
                    sb.append("MMM");
                    break;
                case 'e':
                    sb.append("dd");
                    break;
                case 'H':
                    sb.append("kk");
                    break;
                case 'M':
                    sb.append("mm");
                    break;
                case 'S':
                    sb.append("ss");
                    break;
                case 'Y':
                    sb.append("yyyy");
                    break;
                case 'm':
                    sb.append("MM");
                    break;
                case 'd':
                    sb.append("dd");
                    break;
                case 'r':
                    sb.append("hh:mm:ss aa");
                    break;
                case 'Z':
                    sb.append("zzz");
                    break;
                }
                conversion = false;
            } else {
                if (c == '%') {
                    conversion = true;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static native String getPattern(int dateStyle, int timeStyle, String langtag);
}
