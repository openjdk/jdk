/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4944439
 * @summary Confirm that numbers where all digits after the decimal separator are 0
 *          and which are between Long.MIN_VALUE and Long.MAX_VALUE are returned
 *          as Long(not double).
 * @run junit Bug4944439
 */

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class Bug4944439 {

    // Save JVM default locale
    private static final Locale savedLocale = Locale.getDefault();
    private static final DecimalFormat df = new DecimalFormat();

    // Set JVM default locale to US for testing
    @BeforeAll
    static void initAll() {
        Locale.setDefault(Locale.US);
    }

    // Restore JVM default locale
    @AfterAll
    static void tearDownAll() {
        Locale.setDefault(savedLocale);
    }

    // Check return type and value returned by DecimalFormat.parse() for longs
    @ParameterizedTest
    @MethodSource("longs")
    public void parseLongTest(String s) {
        // This was originally intended to ensure a ParseException is not thrown
        Number parsedNumber = assertDoesNotThrow(() -> df.parse(s),
                "DecimalFormat.parse(\"%s\") should not throw an Exception");
        assertInstanceOf(Long.class, parsedNumber,
                "DecimalFormat.parse(\"%s\") did not return Long");
        // Grab integer portion of value
        Long expectedVal = Long.valueOf(s.substring(0, s.indexOf('.')));
        assertEquals(parsedNumber, expectedVal,
                "DecimalFormat.parse(\"%s\") returned numerically incorrect value");
    }

    // Test some values between Long.MIN_VALUE and Long.MAX_VALUE
    private static Stream<String> longs() {
        ArrayList<String> longs = new ArrayList<>();
        addLongData(Long.MIN_VALUE, Long.MIN_VALUE+10, longs);
        addLongData(-10, 10, longs);
        addLongData(Long.MAX_VALUE-10, Long.MAX_VALUE-1, longs);
        longs.add("9223372036854775807.00");
        longs.add("0.0");
        return longs.stream();
    }

    // Utility to add values between parameters(long, to) to testLongs ArrayList
    private static void addLongData(long from, long to, ArrayList<String> testLongs){
        for (long l = from; l <= to; l++) {
            testLongs.add(l + ".00");
        }
    }

    // Check return type and value returned by DecimalFormat.parse() for doubles
    @ParameterizedTest
    @MethodSource("doubles")
    public void parseDoubleTest(String s) {
        // This was originally intended to ensure a ParseException is not thrown
        Number parsedNumber = assertDoesNotThrow(() -> df.parse(s),
                "DecimalFormat.parse(\"%s\") should not throw an Exception");
        assertInstanceOf(Double.class, parsedNumber,
                "DecimalFormat.parse(\"%s\") did not return Double");
        Double expectedVal = Double.valueOf(s);
        assertEquals(parsedNumber, expectedVal,
                "DecimalFormat.parse(\"%s\") returned numerically incorrect value");
    }

    // Check values not between Long.MIN_VALUE and Long.MAX_VALUE
    private static Stream<String> doubles() {
        return Stream.of(
                "-9223372036854775809", // Long.MIN_VALUE-1
                "9223372036854775808", // Long.MAX_VALUE+1
                "-0.0"
        );
    }
}
