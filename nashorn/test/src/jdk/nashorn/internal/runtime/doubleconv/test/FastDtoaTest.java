/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
// Copyright 2006-2008 the V8 project authors. All rights reserved.

package jdk.nashorn.internal.runtime.doubleconv.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.nashorn.internal.runtime.doubleconv.DoubleConversion;
import jdk.nashorn.internal.runtime.doubleconv.DtoaBuffer;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * FastDtoa tests
 */
@SuppressWarnings("javadoc")
public class FastDtoaTest {

    final static private int kBufferSize = 100;

    // Removes trailing '0' digits.
    // Can return the empty string if all digits are 0.
    private static String trimRepresentation(final String representation) {
        final int len = representation.length();
        int i;
        for (i = len - 1; i >= 0; --i) {
            if (representation.charAt(i) != '0') break;
        }
        return representation.substring(0, i + 1);
    }

    @Test
    public void testFastShortestVarious() {
        final DtoaBuffer buffer = new DtoaBuffer(kBufferSize);
        boolean status;

        final double min_double = 5e-324;
        status = DoubleConversion.fastDtoaShortest(min_double, buffer);
        assertTrue(status);
        assertEquals("5", buffer.getRawDigits());
        assertEquals(-323, buffer.getDecimalPoint());
        buffer.reset();

        final double max_double = 1.7976931348623157e308;
        status = DoubleConversion.fastDtoaShortest(max_double, buffer);
        assertTrue(status);
        assertEquals("17976931348623157", buffer.getRawDigits());
        assertEquals(309, buffer.getDecimalPoint());
        buffer.reset();


        status = DoubleConversion.fastDtoaShortest(4294967272.0, buffer);
        assertTrue(status);
        assertEquals("4294967272", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();


        status = DoubleConversion.fastDtoaShortest(4.1855804968213567e298, buffer);
        assertTrue(status);
        assertEquals("4185580496821357", buffer.getRawDigits());
        assertEquals(299, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaShortest(5.5626846462680035e-309, buffer);
        assertTrue(status);
        assertEquals("5562684646268003", buffer.getRawDigits());
        assertEquals(-308, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaShortest(2147483648.0, buffer);
        assertTrue(status);
        assertEquals("2147483648", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaShortest(3.5844466002796428e+298, buffer);
        if (status) {  // Not all FastDtoa variants manage to compute this number.
            assertEquals("35844466002796428", buffer.getRawDigits());
            assertEquals(299, buffer.getDecimalPoint());
        }
        buffer.reset();

        final long smallest_normal64 = 0x0010000000000000L;
        double v = Double.longBitsToDouble(smallest_normal64);
        status = DoubleConversion.fastDtoaShortest(v, buffer);
        if (status) {
            assertEquals("22250738585072014", buffer.getRawDigits());
            assertEquals(-307, buffer.getDecimalPoint());
        }
        buffer.reset();

        final long largest_denormal64 = 0x000FFFFFFFFFFFFFL;
        v = Double.longBitsToDouble(largest_denormal64);
        status = DoubleConversion.fastDtoaShortest(v, buffer);
        if (status) {
            assertEquals("2225073858507201", buffer.getRawDigits());
            assertEquals(-307, buffer.getDecimalPoint());
        }
        buffer.reset();
    }

    @Test
    public void testFastPrecisionVarious() {
        final DtoaBuffer buffer = new DtoaBuffer(kBufferSize);
        boolean status;

        status = DoubleConversion.fastDtoaCounted(1.0, 3, buffer);
        assertTrue(status);
        assertTrue(3 >= buffer.getLength());
        assertEquals("1", trimRepresentation(buffer.getRawDigits()));
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaCounted(1.5, 10, buffer);
        if (status) {
            assertTrue(10 >= buffer.getLength());
            assertEquals("15", trimRepresentation(buffer.getRawDigits()));
            assertEquals(1, buffer.getDecimalPoint());
        }
        buffer.reset();

        final double min_double = 5e-324;
        status = DoubleConversion.fastDtoaCounted(min_double, 5, buffer);
        assertTrue(status);
        assertEquals("49407", buffer.getRawDigits());
        assertEquals(-323, buffer.getDecimalPoint());
        buffer.reset();

        final double max_double = 1.7976931348623157e308;
        status = DoubleConversion.fastDtoaCounted(max_double, 7, buffer);
        assertTrue(status);
        assertEquals("1797693", buffer.getRawDigits());
        assertEquals(309, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaCounted(4294967272.0, 14, buffer);
        if (status) {
            assertTrue(14 >= buffer.getLength());
            assertEquals("4294967272", trimRepresentation(buffer.getRawDigits()));
            assertEquals(10, buffer.getDecimalPoint());
        }
        buffer.reset();

        status = DoubleConversion.fastDtoaCounted(4.1855804968213567e298, 17, buffer);
        assertTrue(status);
        assertEquals("41855804968213567", buffer.getRawDigits());
        assertEquals(299, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaCounted(5.5626846462680035e-309, 1, buffer);
        assertTrue(status);
        assertEquals("6", buffer.getRawDigits());
        assertEquals(-308, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaCounted(2147483648.0, 5, buffer);
        assertTrue(status);
        assertEquals("21475", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        status = DoubleConversion.fastDtoaCounted(3.5844466002796428e+298, 10, buffer);
        assertTrue(status);
        assertTrue(10 >= buffer.getLength());
        assertEquals("35844466", trimRepresentation(buffer.getRawDigits()));
        assertEquals(299, buffer.getDecimalPoint());
        buffer.reset();

        final long smallest_normal64 = 0x0010000000000000L;
        double v = Double.longBitsToDouble(smallest_normal64);
        status = DoubleConversion.fastDtoaCounted(v, 17, buffer);
        assertTrue(status);
        assertEquals("22250738585072014", buffer.getRawDigits());
        assertEquals(-307, buffer.getDecimalPoint());
        buffer.reset();

        final long largest_denormal64 = 0x000FFFFFFFFFFFFFL;
        v = Double.longBitsToDouble(largest_denormal64);
        status = DoubleConversion.fastDtoaCounted(v, 17, buffer);
        assertTrue(status);
        assertTrue(20 >= buffer.getLength());
        assertEquals("22250738585072009", trimRepresentation(buffer.getRawDigits()));
        assertEquals(-307, buffer.getDecimalPoint());
        buffer.reset();

        v = 3.3161339052167390562200598e-237;
        status = DoubleConversion.fastDtoaCounted(v, 18, buffer);
        assertTrue(status);
        assertEquals("331613390521673906", buffer.getRawDigits());
        assertEquals(-236, buffer.getDecimalPoint());
        buffer.reset();

        v = 7.9885183916008099497815232e+191;
        status = DoubleConversion.fastDtoaCounted(v, 4, buffer);
        assertTrue(status);
        assertEquals("7989", buffer.getRawDigits());
        assertEquals(192, buffer.getDecimalPoint());
        buffer.reset();
    }


    @Test
    public void testFastShortest() {
        final AtomicInteger total = new AtomicInteger();
        final AtomicInteger succeeded = new AtomicInteger();
        final AtomicBoolean neededMaxLength = new AtomicBoolean();

        new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("resources/gay-shortest.txt")))
                .lines()
                .forEach(line -> {
                    if (line.isEmpty() || line.startsWith("//")) {
                        return; // comment or empty line
                    }
                    final String[] tokens = line.split(",\\s+");
                    assertEquals(tokens.length, 3, "*" + line + "*");
                    final double v = Double.parseDouble(tokens[0]);
                    final String str = tokens[1].replace('"', ' ').trim();;
                    final int point = Integer.parseInt(tokens[2]);
                    final DtoaBuffer buffer = new DtoaBuffer(kBufferSize);
                    total.getAndIncrement();

                    if (DoubleConversion.fastDtoaShortest(v, buffer)) {
                        assertEquals(str, buffer.getRawDigits());
                        assertEquals(point, buffer.getDecimalPoint());
                        succeeded.getAndIncrement();
                        if (buffer.getLength() == DtoaBuffer.kFastDtoaMaximalLength) {
                            neededMaxLength.set(true);
                        }
                    }
                });

        assertTrue(succeeded.get() * 1.0 / total.get() > 0.99);
        assertTrue(neededMaxLength.get());
        // Additional constraints: Make sure these numbers are exactly the same as in C++ version
        assertEquals(succeeded.get(), 99440);
        assertEquals(total.get(), 100000);
    }

    @Test
    public void testFastPrecision() {
        final AtomicInteger total = new AtomicInteger();
        final AtomicInteger succeeded = new AtomicInteger();
        // Count separately for entries with less than 15 requested digits.
        final AtomicInteger  succeeded_15  = new AtomicInteger();
        final AtomicInteger  total_15 = new AtomicInteger();

        new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("resources/gay-precision.txt")))
                .lines()
                .forEach(line -> {
                    if (line.isEmpty() || line.startsWith("//")) {
                        return; // comment or empty line
                    }
                    final String[] tokens = line.split(",\\s+");
                    assertEquals(tokens.length, 4);
                    final double v = Double.parseDouble(tokens[0]);
                    final int digits = Integer.parseInt(tokens[1]);
                    final String str = tokens[2].replace('"', ' ').trim();
                    final int point = Integer.parseInt(tokens[3]);
                    final DtoaBuffer buffer = new DtoaBuffer(kBufferSize);
                    total.getAndIncrement();
                    if (digits <= 15) {
                        total_15.getAndIncrement();
                    }

                    if (DoubleConversion.fastDtoaCounted(v, digits, buffer)) {
                        assertEquals(str, trimRepresentation(buffer.getRawDigits()));
                        assertEquals(point, buffer.getDecimalPoint());
                        succeeded.getAndIncrement();
                        if (digits <= 15) {
                            succeeded_15.getAndIncrement();
                        }
                    }
                });

        // The precomputed numbers contain many entries with many requested
        // digits. These have a high failure rate and we therefore expect a lower
        // success rate than for the shortest representation.
        assertTrue(succeeded.get() * 1.0 / total.get() > 0.85);
        // However with less than 15 digits almost the algorithm should almost always
        // succeed.
        assertTrue(succeeded_15.get() * 1.0 / total_15.get() > 0.9999);
        // Additional constraints: Make sure these numbers are exactly the same as in C++ version
        assertEquals(succeeded.get(), 86866);
        assertEquals(total.get(), 100000);
        assertEquals(succeeded_15.get(), 71328);
        assertEquals(total_15.get(), 71330);
    }

}
