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
package test.java.time.format;

import static org.testng.Assert.assertEquals;

import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test DateTimeFormatter.ofLocalizedPattern() related methods.
 * @bug 8176706
 */
@Test
public class TestLocalizedPattern {

    private static final ZonedDateTime ZDT =
            ZonedDateTime.of(2022, 1, 26, 15, 32, 39, 0, ZoneId.of("America/Los_Angeles"));

    private final static List<Locale> SAMPLE_LOCALES = List.of(
            Locale.US,
            Locale.forLanguageTag("ja-JP-u-ca-japanese")
    );

    @DataProvider(name = "validSkeletons")
    Object[][] data_validSkeletons() {
        return SAMPLE_LOCALES.stream()
                .flatMap(l -> {
                    var rb = ResourceBundle.getBundle("test.java.time.format.Skeletons", l);
                    return rb.keySet().stream().map(key -> new Object[]{key, rb.getString(key), l});
                })
                .toList()
                .toArray(new Object[0][0]);
    }

    @DataProvider(name = "invalidSkeletons")
    Object[][] data_invalidSkeletons() {
        return new Object[][] {
            {"afo"}, {"hB"}, {"uMMM"}, {"MMMMMM"}, {"BhmsyMMM"},
        };
    }

    @DataProvider(name = "unavailableSkeletons")
    Object[][] data_unavailableSkeletons() {
        return new Object[][] {
            {"yyyyyy"}, {"BBh"}, {"yMMMMEdBBh"},
        };
    }

    @Test(dataProvider = "validSkeletons")
    public void test_ofLocalizedPattern(String skeleton, String expected, Locale l) {
        var dtf = DateTimeFormatter.ofLocalizedPattern(skeleton).localizedBy(l);
        assertEquals(dtf.format(ZDT), expected);
    }

    @Test(dataProvider = "invalidSkeletons", expectedExceptions = IllegalArgumentException.class)
    public void test_ofLocalizedPattern_invalid(String skeleton) {
        DateTimeFormatter.ofLocalizedPattern(skeleton);
    }

    @Test(dataProvider = "invalidSkeletons", expectedExceptions = IllegalArgumentException.class)
    public void test_appendLocalized_invalid(String skeleton) {
        new DateTimeFormatterBuilder().appendLocalized(skeleton);
    }

    @Test(dataProvider = "unavailableSkeletons", expectedExceptions = DateTimeException.class)
    public void test_ofLocalizedPattern_unavailable(String skeleton) {
        DateTimeFormatter.ofLocalizedPattern(skeleton).format(ZDT);
    }

    @Test(dataProvider = "unavailableSkeletons", expectedExceptions = DateTimeException.class)
    public void test_getLocalizedDateTimePattern_unavailable(String skeleton) {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(skeleton, IsoChronology.INSTANCE, Locale.US);
    }
}
