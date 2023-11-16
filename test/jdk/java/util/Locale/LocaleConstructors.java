/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4316602
 * @author joconner
 * @summary Verify all Locale constructors and of() methods
 * @run junit LocaleConstructors
 */

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This class tests to ensure that the language, language/country, and
 * language/country/variant Locale constructors + of() method are all allowed.
 */
public class LocaleConstructors {

    static final String LANG = "en";
    static final String COUNTRY = "US";
    static final String VAR = "socal";

    // Test Locale constructor and .of() allow (language) argument(s)
    @Test
    public void langTest() {
        Locale aLocale = Locale.of(LANG);
        Locale otherLocale = new Locale(LANG);
        assertEquals(aLocale.toString(), LANG);
        assertEquals(otherLocale.toString(), LANG);
    }

    // Test Locale constructor and .of() allow (language, constructor) argument(s)
    @Test
    public void langCountryTest() {
        Locale aLocale = Locale.of(LANG, COUNTRY);
        Locale otherLocale = new Locale(LANG, COUNTRY);
        assertEquals(aLocale.toString(), String.format("%s_%s",
                LANG, COUNTRY));
        assertEquals(otherLocale.toString(), String.format("%s_%s",
                LANG, COUNTRY));
    }

    // Test Locale constructor and .of() allow
    // (language, constructor, variant) argument(s)
    @Test
    public void langCountryVariantTest() {
        Locale aLocale = Locale.of(LANG, COUNTRY, VAR);
        Locale otherLocale = new Locale(LANG, COUNTRY, VAR);
        assertEquals(aLocale.toString(), String.format("%s_%s_%s",
                LANG, COUNTRY, VAR));
        assertEquals(otherLocale.toString(), String.format("%s_%s_%s",
                LANG, COUNTRY, VAR));
    }
}
