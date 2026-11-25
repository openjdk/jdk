/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package javatime;

import java.time.format.DateTimeFormatterPatternProvider;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

public class DateTimeFormatterPatternProviderImpl extends DateTimeFormatterPatternProvider {
    private static final List<Locale> available =
        List.of(Locale.UK, Locale.JAPAN, Locale.TAIWAN, Locale.of("ar", "EG"));

    @Override
    public Locale[] getAvailableLocales() {
        return available.toArray(new Locale[0]);
    }

    @Override
    public boolean isSupportedLocale(Locale locale) {
        return available.contains(locale);
    }

    @Override
    public String getDateTimeFormatterPattern(FormatStyle dateStyle, FormatStyle timeStyle, String calType, Locale locale) {
        assert available.contains(locale);
        return "'date style: " + dateStyle + ", timeStyle: " + timeStyle + ", calType: " + calType + ", loc: " + locale + "'";
    }

    @Override
    public String getDateTimeFormatterPattern(String requestedTemplate, String calType, Locale locale) {
        assert available.contains(locale);
        return "'requestedTemplate: " + requestedTemplate + ", calType: " + calType + ", loc: " + locale + "'";
    }
}
