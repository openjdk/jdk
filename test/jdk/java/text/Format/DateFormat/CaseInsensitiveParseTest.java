/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8248434
 * @modules jdk.localedata
 * @summary Checks format/parse round trip in case-insensitive manner.
 * @run junit/othervm CaseInsensitiveParseTest
 */

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CaseInsensitiveParseTest {

    private final static String PATTERN = "GGGG/yyyy/MMMM/dddd/hhhh/mmmm/ss/aaaa";
    private final static Date EPOCH = new Date(0L);

    Object[][] locales() {
        return (Object[][])Arrays.stream(DateFormat.getAvailableLocales())
            .map(Stream::of)
            .map(Stream::toArray)
            .toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testUpperCase(Locale loc) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN, loc);
        String formatted = sdf.format(EPOCH);
        assertEquals(EPOCH, sdf.parse(formatted.toUpperCase(Locale.ROOT)),
                "roundtrip failed for string '" + formatted + "', locale: " + loc);
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testLowerCase(Locale loc) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN, loc);
        String formatted = sdf.format(EPOCH);
        assertEquals(EPOCH, sdf.parse(formatted.toLowerCase(Locale.ROOT)),
                "roundtrip failed for string '" + formatted + "', locale: " + loc);
    }
}
