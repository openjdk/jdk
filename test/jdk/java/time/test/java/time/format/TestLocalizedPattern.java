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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test DateTimeFormatter.ofLocalizedPattern() methods.
 * @bug 8085887
 */
@Test
public class TestLocalizedPattern {

    final static String[] testSkeletons = {
            // Locales should generally provide availableFormats data for a fairly
            // complete set of time skeletons without B, typically the following:
            "H", "h", "Hm", "hm", "Hms", "hms", "Hmv", "hmv", "Hmsv", "hmsv",
            // Locales that use 12-hour-cycle time formats with B may provide
            // availableFormats data for a smaller set of time skeletons with B:
            "Bh", "Bhm", "Bhms",
            // date skeletons
            "M", "MMM", "MEd", "MMMMEd", "d", "y", "yM", "yMEd", "yMMM", "yMMMMEd",
            "GyM", "GyMEd", "GyMMM", "GyMMMMEd",
            "yQQQ", "yQQQQ",
    };

    final static String[] invalidSkeletons = {
            "afo", "BBh", "hB",
    };

    final static String[] inputSkeletons = {
            "jm", "jjmm", "jjj", "jjjj", "jjjjj", "jjjjjj",
            "J", "J",
            "C", "CC", "CCC", "CCCC", "CCCCC", "CCCCCC"
    };

    private final static List<Locale> sampleLocs = List.of(
            Locale.US,
            Locale.forLanguageTag("ja-JP-u-ca-japanese")
    );

    @DataProvider(name = "Skeletons")
    Object[][] data_Skeletons() {
        return sampleLocs.stream()
                .flatMap(l -> {
                    var rb = ResourceBundle.getBundle("test.java.time.format.Skeletons", l);
                    var keyset = rb.keySet();
                    return keyset.stream()
                            .map(key -> new Object[] {key, rb.getString(key), l});
                })
                .collect(Collectors.toList())
                .toArray(new Object[0][0]);
    };

    @Test(dataProvider = "Skeletons")
    public void test_ofLocalizedPattern(String skeleton, String expected, Locale l) {
        var now = ZonedDateTime.of(2022, 1, 26, 15, 32, 39, 0, ZoneId.of("America/Los_Angeles"));
        var dtf = DateTimeFormatter.ofLocalizedPattern(skeleton, l);
        assertEquals(dtf.format(now), expected);
    }
}
