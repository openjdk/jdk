/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282819
 * @summary Unit tests for Locale.of() method. Those tests check the equality
 *      of obtained objects with ones that are gotten from other means with both
 *      well-formed and ill-formed arguments. Also checks the possible exceptions
 *      for error cases.
 * @run testng TestOf
 */
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import java.util.Locale;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

@Test
public class TestOf {

    @SuppressWarnings("deprecation")
    @DataProvider
    public Object[][] data_validArgs() {
        return new Object[][]{
                // well-formed
                {Locale.ROOT, new String[0]},
                {Locale.ROOT, ""},
                {Locale.ENGLISH, "en"},
                {Locale.US, "en", "US"},
                {Locale.forLanguageTag("en-Latn-US"), "en", "US", "", "Latn"},

                // ill-formed
                {new Locale("a", "A", "a"), "a", "A", "a"},
                {new Locale("ja", "JP", "JP"), "ja", "JP", "JP"},
                {new Locale("th", "TH", "TH"), "th", "TH", "TH"},
        };
    }

    @DataProvider
    public Object[][] data_nullArgs() {
        return new Object[][]{
                {null},
                {"", null},
                {"", "", null},
                {"", "", "", null},
        };
    }

    @Test (dataProvider = "data_validArgs")
    public void test_validArgs(Locale expected, String... args) {
        assertEquals(Locale.of(args), expected);
    }

    @Test (dataProvider = "data_nullArgs")
    public void test_nullArgs(String... args) {
        assertThrows(NullPointerException.class, () -> Locale.of(args));
    }

    @Test
    public void test_IAE() {
        assertThrows(IllegalArgumentException.class, () -> Locale.of("en", "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> Locale.of(new String[5]));
    }
}
