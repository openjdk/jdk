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
// Copyright 2010 the V8 project authors. All rights reserved.
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
import java.util.concurrent.atomic.AtomicInteger;
import jdk.nashorn.internal.runtime.doubleconv.DoubleConversion;
import jdk.nashorn.internal.runtime.doubleconv.DtoaBuffer;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * FixedDtoa tests
 */
@SuppressWarnings("javadoc")
public class FixedDtoaTest {

    static final int kBufferSize = 500;

    @Test
    public void testFastShortestVarious() {
        final DtoaBuffer buffer = new DtoaBuffer(kBufferSize);

        assertTrue(DoubleConversion.fixedDtoa(1.0, 1, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1.0, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1.0, 0, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0xFFFFFFFFL, 5, buffer));
        assertEquals("4294967295", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(4294967296.0, 5, buffer));
        assertEquals("4294967296", buffer.getRawDigits());
        assertEquals(10, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1e21, 5, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(22, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(999999999999999868928.00, 2, buffer));
        assertEquals("999999999999999868928", buffer.getRawDigits());
        assertEquals(21, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(6.9999999999999989514240000e+21, 5, buffer));
        assertEquals("6999999999999998951424", buffer.getRawDigits());
        assertEquals(22, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1.5, 5, buffer));
        assertEquals("15", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1.55, 5, buffer));
        assertEquals("155", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1.55, 1, buffer));
        assertEquals("16", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1.00000001, 15, buffer));
        assertEquals("100000001", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.1, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(0, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.01, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.001, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-2, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0001, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-3, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00001, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-4, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000001, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-5, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000001, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-6, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000001, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-7, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000001, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-8, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000001, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-9, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000000001, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-10, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000001, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-11, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000001, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-12, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000000000001, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-13, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000001, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-14, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000000001, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-15, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000000000000001, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-16, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000001, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-17, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000000000001, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-18, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000000000000000001, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-19, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.10000000004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(0, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.01000000004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00100000004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-2, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00010000004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-3, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00001000004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-4, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000100004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-5, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000010004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-6, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000001004, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-7, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000000104, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-8, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000001000004, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-9, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000100004, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-10, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000010004, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-11, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000001004, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-12, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000000104, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-13, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000001000004, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-14, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000100004, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-15, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000010004, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-16, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000001004, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-17, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000000104, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-18, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000000014, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-19, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.10000000006, 10, buffer));
        assertEquals("1000000001", buffer.getRawDigits());
        assertEquals(0, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.01000000006, 10, buffer));
        assertEquals("100000001", buffer.getRawDigits());
        assertEquals(-1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00100000006, 10, buffer));
        assertEquals("10000001", buffer.getRawDigits());
        assertEquals(-2, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00010000006, 10, buffer));
        assertEquals("1000001", buffer.getRawDigits());
        assertEquals(-3, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00001000006, 10, buffer));
        assertEquals("100001", buffer.getRawDigits());
        assertEquals(-4, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000100006, 10, buffer));
        assertEquals("10001", buffer.getRawDigits());
        assertEquals(-5, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000010006, 10, buffer));
        assertEquals("1001", buffer.getRawDigits());
        assertEquals(-6, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000001006, 10, buffer));
        assertEquals("101", buffer.getRawDigits());
        assertEquals(-7, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000000106, 10, buffer));
        assertEquals("11", buffer.getRawDigits());
        assertEquals(-8, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000001000006, 15, buffer));
        assertEquals("100001", buffer.getRawDigits());
        assertEquals(-9, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000100006, 15, buffer));
        assertEquals("10001", buffer.getRawDigits());
        assertEquals(-10, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000010006, 15, buffer));
        assertEquals("1001", buffer.getRawDigits());
        assertEquals(-11, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000001006, 15, buffer));
        assertEquals("101", buffer.getRawDigits());
        assertEquals(-12, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000000000000106, 15, buffer));
        assertEquals("11", buffer.getRawDigits());
        assertEquals(-13, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000001000006, 20, buffer));
        assertEquals("100001", buffer.getRawDigits());
        assertEquals(-14, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000100006, 20, buffer));
        assertEquals("10001", buffer.getRawDigits());
        assertEquals(-15, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000010006, 20, buffer));
        assertEquals("1001", buffer.getRawDigits());
        assertEquals(-16, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000001006, 20, buffer));
        assertEquals("101", buffer.getRawDigits());
        assertEquals(-17, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000000106, 20, buffer));
        assertEquals("11", buffer.getRawDigits());
        assertEquals(-18, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000000000000000016, 20, buffer));
        assertEquals("2", buffer.getRawDigits());
        assertEquals(-19, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.6, 0, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.96, 1, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.996, 2, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.9996, 3, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.99996, 4, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.999996, 5, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.9999996, 6, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.99999996, 7, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.999999996, 8, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.9999999996, 9, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.99999999996, 10, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.999999999996, 11, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.9999999999996, 12, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.99999999999996, 13, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.999999999999996, 14, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.9999999999999996, 15, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00999999999999996, 16, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000999999999999996, 17, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-2, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.0000999999999999996, 18, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-3, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.00000999999999999996, 19, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-4, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.000000999999999999996, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-5, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(323423.234234, 10, buffer));
        assertEquals("323423234234", buffer.getRawDigits());
        assertEquals(6, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(12345678.901234, 4, buffer));
        assertEquals("123456789012", buffer.getRawDigits());
        assertEquals(8, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(98765.432109, 5, buffer));
        assertEquals("9876543211", buffer.getRawDigits());
        assertEquals(5, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(42, 20, buffer));
        assertEquals("42", buffer.getRawDigits());
        assertEquals(2, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(0.5, 0, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(1, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1e-23, 10, buffer));
        assertEquals("", buffer.getRawDigits());
        assertEquals(-10, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1e-123, 2, buffer));
        assertEquals("", buffer.getRawDigits());
        assertEquals(-2, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1e-123, 0, buffer));
        assertEquals("", buffer.getRawDigits());
        assertEquals(0, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1e-23, 20, buffer));
        assertEquals("", buffer.getRawDigits());
        assertEquals(-20, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1e-21, 20, buffer));
        assertEquals("", buffer.getRawDigits());
        assertEquals(-20, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1e-22, 20, buffer));
        assertEquals("", buffer.getRawDigits());
        assertEquals(-20, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(6e-21, 20, buffer));
        assertEquals("1", buffer.getRawDigits());
        assertEquals(-19, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(9.1193616301674545152000000e+19, 0, buffer));
        assertEquals("91193616301674545152", buffer.getRawDigits());
        assertEquals(20, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(4.8184662102767651659096515e-04, 19, buffer));
        assertEquals("4818466210276765", buffer.getRawDigits());
        assertEquals(-3, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1.9023164229540652612705182e-23, 8, buffer));
        assertEquals("", buffer.getRawDigits());
        assertEquals(-8, buffer.getDecimalPoint());
        buffer.reset();

        assertTrue(DoubleConversion.fixedDtoa(1000000000000000128.0, 0, buffer));
        assertEquals("1000000000000000128", buffer.getRawDigits());
        assertEquals(19, buffer.getDecimalPoint());
        buffer.reset();
    }



    @Test
    public void testFastFixed() {
        final AtomicInteger total = new AtomicInteger();
        final AtomicInteger succeeded = new AtomicInteger();

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
                    final String str = tokens[2].replace('"', ' ').trim();;
                    final int point = Integer.parseInt(tokens[3]);
                    final DtoaBuffer buffer = new DtoaBuffer(kBufferSize);
                    total.getAndIncrement();

                    if (DoubleConversion.fixedDtoa(v, digits, buffer)) {
                        assertEquals(str, buffer.getRawDigits());
                        assertEquals(point, buffer.getDecimalPoint());
                        succeeded.getAndIncrement();
                    }
                });

        // should work for all numbers
        assertEquals(succeeded.get(), total.get());
    }

}
