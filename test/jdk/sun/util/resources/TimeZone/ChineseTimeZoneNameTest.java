/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8275721 8174269
 * @modules jdk.localedata
 * @summary Checks Chinese time zone names for `UTC` using CLDR are consistent
 * @run junit ChineseTimeZoneNameTest
 */

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChineseTimeZoneNameTest {

    private static final Locale SIMPLIFIED_CHINESE = Locale.forLanguageTag("zh-Hans");
    private static final Locale TRADITIONAL_CHINESE = Locale.forLanguageTag("zh-Hant");
    private static final ZonedDateTime EPOCH_UTC =
        ZonedDateTime.ofInstant(Instant.ofEpochSecond (0), ZoneId.of ("UTC"));

    private static Object[][] data() {
        return new Object[][] {
            {Locale.CHINESE,                        SIMPLIFIED_CHINESE},
            {Locale.SIMPLIFIED_CHINESE,             SIMPLIFIED_CHINESE},
            {Locale.forLanguageTag("zh-SG"),        SIMPLIFIED_CHINESE},
            {Locale.forLanguageTag("zh-Hans-TW"),   SIMPLIFIED_CHINESE},
            {Locale.forLanguageTag("zh-HK"),        TRADITIONAL_CHINESE},
            {Locale.forLanguageTag("zh-MO"),        TRADITIONAL_CHINESE},
            {Locale.TRADITIONAL_CHINESE,            TRADITIONAL_CHINESE},
            {Locale.forLanguageTag("zh-Hant-CN"),   TRADITIONAL_CHINESE},
        };
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test_ChineseTimeZoneNames(Locale testLoc, Locale resourceLoc) {
        assertEquals(DateTimeFormatter.ofPattern("z", resourceLoc).format(EPOCH_UTC),
            DateTimeFormatter.ofPattern("z", testLoc).format(EPOCH_UTC));
        assertEquals(DateTimeFormatter.ofPattern("zzzz", resourceLoc).format(EPOCH_UTC),
            DateTimeFormatter.ofPattern("zzzz", testLoc).format(EPOCH_UTC));
    }
}
