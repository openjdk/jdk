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
// Copyright 2012 the V8 project authors. All rights reserved.
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials provided
//       with the distribution.
//     * Neither the name of Google Inc. nor the names of its
//       contributors may be used to endorse or promote products derived
//       from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package jdk.nashorn.internal.runtime.doubleconv.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import jdk.nashorn.internal.runtime.doubleconv.DoubleConversion;
import jdk.nashorn.internal.runtime.doubleconv.DtoaBuffer;
import jdk.nashorn.internal.runtime.doubleconv.DtoaMode;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * FastDtoa tests
 */
@SuppressWarnings("javadoc")
public class BignumDtoaTest {

    final static private int BUFFER_SIZE = 100;

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
    public void testBignumVarious() {
        final DtoaBuffer buffer = new DtoaBuffer(BUFFER_SIZE);

        DoubleConversion.bignumDtoa(1, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(1.0, DtoaMode.FIXED, 3, buffer);
        assertTrue(3 >= buffer.getLength() - buffer.getDecimalPoint());
        assertEquals("1", trimRepresentation(buffer.getRawDigits()));
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(1.0, DtoaMode.PRECISION, 3, buffer);
        assertTrue(3 >= buffer.getLength());
        assertEquals("1", trimRepresentation(buffer.getRawDigits()));
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(1.5, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("15", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(1.5, DtoaMode.FIXED, 10, buffer);
        assertTrue(10 >= buffer.getLength() - buffer.getDecimalPoint());
        assertEquals("15", trimRepresentation(buffer.getRawDigits()));
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(1.5, DtoaMode.PRECISION, 10, buffer);
        assertTrue(10 >= buffer.getLength());
        assertEquals("15", trimRepresentation(buffer.getRawDigits()));
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        final double min_double = 5e-324;
        DoubleConversion.bignumDtoa(min_double, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("5", buffer.getRawDigits());
        assertEquals(-323, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(min_double, DtoaMode.FIXED, 5, buffer);
        assertTrue(5 >= buffer.getLength() - buffer.getDecimalPoint());
        assertEquals("", trimRepresentation(buffer.getRawDigits()));
        buffer.reset();

        DoubleConversion.bignumDtoa(min_double, DtoaMode.PRECISION, 5, buffer);
        assertTrue(5 >= buffer.getLength());
        assertEquals("49407", trimRepresentation(buffer.getRawDigits()));
        assertEquals(-323, buffer.getDecimalPoint());
        buffer.reset();

        final double max_double = 1.7976931348623157e308;
        DoubleConversion.bignumDtoa(max_double, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("17976931348623157", buffer.getRawDigits());
        assertEquals(309, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(max_double, DtoaMode.PRECISION, 7, buffer);
        assertTrue(7 >= buffer.getLength());
        assertEquals("1797693", trimRepresentation(buffer.getRawDigits()));
        assertEquals(309, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(4294967272.0, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("4294967272", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(4294967272.0, DtoaMode.FIXED, 5, buffer);
        assertEquals("429496727200000", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(4294967272.0, DtoaMode.PRECISION, 14, buffer);
        assertTrue(14 >= buffer.getLength());
        assertEquals("4294967272", trimRepresentation(buffer.getRawDigits()));
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(4.1855804968213567e298, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("4185580496821357", buffer.getRawDigits());
        assertEquals(299, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(4.1855804968213567e298, DtoaMode.PRECISION, 20, buffer);
        assertTrue(20 >= buffer.getLength());
        assertEquals("41855804968213567225", trimRepresentation(buffer.getRawDigits()));
        assertEquals(299, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(5.5626846462680035e-309, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("5562684646268003", buffer.getRawDigits());
        assertEquals(-308, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(5.5626846462680035e-309, DtoaMode.PRECISION, 1, buffer);
        assertTrue(1 >= buffer.getLength());
        assertEquals("6", trimRepresentation(buffer.getRawDigits()));
        assertEquals(-308, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(2147483648.0, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("2147483648", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(2147483648.0, DtoaMode.FIXED, 2, buffer);
        assertTrue(2 >= buffer.getLength() - buffer.getDecimalPoint());
        assertEquals("2147483648", trimRepresentation(buffer.getRawDigits()));
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(2147483648.0, DtoaMode.PRECISION, 5, buffer);
        assertTrue(5 >= buffer.getLength());
        assertEquals("21475", trimRepresentation(buffer.getRawDigits()));
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(3.5844466002796428e+298, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("35844466002796428", buffer.getRawDigits());
        assertEquals(299, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(3.5844466002796428e+298, DtoaMode.PRECISION, 10, buffer);
        assertTrue(10 >= buffer.getLength());
        assertEquals("35844466", trimRepresentation(buffer.getRawDigits()));
        assertEquals(299, buffer.getDecimalPoint());
        buffer.reset();

        final long smallest_normal64 = 0x0010000000000000L;
        double v = Double.longBitsToDouble(smallest_normal64);
        DoubleConversion.bignumDtoa(v, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("22250738585072014", buffer.getRawDigits());
        assertEquals(-307, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(v, DtoaMode.PRECISION, 20, buffer);
        assertTrue(20 >= buffer.getLength());
        assertEquals("22250738585072013831", trimRepresentation(buffer.getRawDigits()));
        assertEquals(-307, buffer.getDecimalPoint());
        buffer.reset();

        final long largest_denormal64 = 0x000FFFFFFFFFFFFFL;
        v = Double.longBitsToDouble(largest_denormal64);
        DoubleConversion.bignumDtoa(v, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("2225073858507201", buffer.getRawDigits());
        assertEquals(-307, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(v, DtoaMode.PRECISION, 20, buffer);
        assertTrue(20 >= buffer.getLength());
        assertEquals("2225073858507200889", trimRepresentation(buffer.getRawDigits()));
        assertEquals(-307, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(4128420500802942e-24, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("4128420500802942", buffer.getRawDigits());
        assertEquals(-8, buffer.getDecimalPoint());
        buffer.reset();

        DoubleConversion.bignumDtoa(3.9292015898194142585311918e-10, DtoaMode.SHORTEST, 0, buffer);
        assertEquals("39292015898194143", buffer.getRawDigits());
        buffer.reset();

        v = 4194304.0;
        DoubleConversion.bignumDtoa(v, DtoaMode.FIXED, 5, buffer);
        assertTrue(5 >= buffer.getLength() - buffer.getDecimalPoint());
        assertEquals("4194304", trimRepresentation(buffer.getRawDigits()));
        buffer.reset();

        v = 3.3161339052167390562200598e-237;
        DoubleConversion.bignumDtoa(v, DtoaMode.PRECISION, 19, buffer);
        assertTrue(19 >= buffer.getLength());
        assertEquals("3316133905216739056", trimRepresentation(buffer.getRawDigits()));
        assertEquals(-236, buffer.getDecimalPoint());
        buffer.reset();

        v = 7.9885183916008099497815232e+191;
        DoubleConversion.bignumDtoa(v, DtoaMode.PRECISION, 4, buffer);
        assertTrue(4 >= buffer.getLength());
        assertEquals("7989", trimRepresentation(buffer.getRawDigits()));
        assertEquals(192, buffer.getDecimalPoint());
        buffer.reset();

        v = 1.0000000000000012800000000e+17;
        DoubleConversion.bignumDtoa(v, DtoaMode.FIXED, 1, buffer);
        assertTrue(1 >= buffer.getLength() - buffer.getDecimalPoint());
        assertEquals("100000000000000128", trimRepresentation(buffer.getRawDigits()));
        assertEquals(18, buffer.getDecimalPoint());
        buffer.reset();
    }


    @Test
    public void testBignumShortest() {
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
                    final DtoaBuffer buffer = new DtoaBuffer(BUFFER_SIZE);

                    DoubleConversion.bignumDtoa(v, DtoaMode.SHORTEST, 0, buffer);
                    assertEquals(str, buffer.getRawDigits());
                    assertEquals(point, buffer.getDecimalPoint());
                });
    }

    @Test
    public void testBignumFixed()  {
        new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("resources/gay-fixed.txt")))
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
                    final DtoaBuffer buffer = new DtoaBuffer(BUFFER_SIZE);

                    DoubleConversion.bignumDtoa(v, DtoaMode.FIXED, digits, buffer);
                    assertEquals(str, trimRepresentation(buffer.getRawDigits()));
                    assertEquals(point, buffer.getDecimalPoint());
                });
    }

    @Test
    public void testBignumPrecision() {
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
                    final DtoaBuffer buffer = new DtoaBuffer(BUFFER_SIZE);

                    DoubleConversion.bignumDtoa(v, DtoaMode.PRECISION, digits, buffer);
                    assertEquals(str, trimRepresentation(buffer.getRawDigits()));
                    assertEquals(point, buffer.getDecimalPoint());
                });
    }

}
