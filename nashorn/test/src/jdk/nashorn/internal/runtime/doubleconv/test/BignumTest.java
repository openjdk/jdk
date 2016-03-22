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

import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Bignum class tests
 *
 * @test
 * @modules jdk.scripting.nashorn/jdk.nashorn.internal.runtime.doubleconv
 * @run testng jdk.nashorn.internal.runtime.doubleconv.test.BignumTest
 */
@SuppressWarnings("javadoc")
public class BignumTest {

    static final Class<?> Bignum;
    static final Constructor<?> ctor;

    static {
        try {
            Bignum = Class.forName("jdk.nashorn.internal.runtime.doubleconv.Bignum");
            ctor = Bignum.getDeclaredConstructor();
            ctor.setAccessible(true);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method method(final String name, final Class<?>... params) throws NoSuchMethodException {
        final Method m = Bignum.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void testAssign() throws Exception {

        Object bignum = ctor.newInstance();
        Object bignum2 = ctor.newInstance();

        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignUInt64 = method("assignUInt64", long.class);
        final Method assignDecimalString = method("assignDecimalString", String.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method toHexString = method("toHexString");

        assignUInt16.invoke(bignum, (char) 0);
        assertEquals(toHexString.invoke(bignum), "0");
        assignUInt16.invoke(bignum, (char) 0xA);
        assertEquals(toHexString.invoke(bignum), "A");
        assignUInt16.invoke(bignum, (char) 0x20);
        assertEquals(toHexString.invoke(bignum), "20");

        assignUInt64.invoke(bignum, 0);
        assertEquals(toHexString.invoke(bignum), "0");
        assignUInt64.invoke(bignum, 0xA);
        assertEquals(toHexString.invoke(bignum), "A");
        assignUInt64.invoke(bignum, 0x20);
        assertEquals(toHexString.invoke(bignum), "20");
        assignUInt64.invoke(bignum, 0x100);
        assertEquals(toHexString.invoke(bignum), "100");

        // The first real test, since this will not fit into one bigit.
        assignUInt64.invoke(bignum, 0x12345678L);
        assertEquals(toHexString.invoke(bignum), "12345678");

        assignUInt64.invoke(bignum, 0xFFFFFFFFFFFFFFFFL);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFFFF");

        assignUInt64.invoke(bignum, 0x123456789ABCDEF0L);
        assertEquals(toHexString.invoke(bignum), "123456789ABCDEF0");

        assignUInt64.invoke(bignum2, 0x123456789ABCDEF0L);
        assertEquals(toHexString.invoke(bignum2), "123456789ABCDEF0");

        assignDecimalString.invoke(bignum, "0");
        assertEquals(toHexString.invoke(bignum), "0");

        assignDecimalString.invoke(bignum, "1");
        assertEquals(toHexString.invoke(bignum), "1");

        assignDecimalString.invoke(bignum, "1234567890");
        assertEquals(toHexString.invoke(bignum), "499602D2");

        assignHexString.invoke(bignum, "0");
        assertEquals(toHexString.invoke(bignum), "0");

        assignHexString.invoke(bignum, "123456789ABCDEF0");
        assertEquals(toHexString.invoke(bignum), "123456789ABCDEF0");
    }

    @Test
    public void testShiftLeft() throws Exception {

        final Object bignum = ctor.newInstance();

        final Method assignHexString = method("assignHexString", String.class);
        final Method shiftLeft = method("shiftLeft", int.class);
        final Method toHexString = method("toHexString");


        assignHexString.invoke(bignum, "0");
        shiftLeft.invoke(bignum, 100);
        assertEquals(toHexString.invoke(bignum), "0");

        assignHexString.invoke(bignum, "1");
        shiftLeft.invoke(bignum, 1);
        assertEquals(toHexString.invoke(bignum), "2");

        assignHexString.invoke(bignum, "1");
        shiftLeft.invoke(bignum, 4);
        assertEquals(toHexString.invoke(bignum), "10");

        assignHexString.invoke(bignum, "1");
        shiftLeft.invoke(bignum, 32);
        assertEquals(toHexString.invoke(bignum), "100000000");

        assignHexString.invoke(bignum, "1");
        shiftLeft.invoke(bignum, 64);
        assertEquals(toHexString.invoke(bignum), "10000000000000000");

        assignHexString.invoke(bignum, "123456789ABCDEF");
        shiftLeft.invoke(bignum, 64);
        assertEquals(toHexString.invoke(bignum), "123456789ABCDEF0000000000000000");
        shiftLeft.invoke(bignum, 1);
        assertEquals(toHexString.invoke(bignum), "2468ACF13579BDE0000000000000000");
    }



    @Test
    public void testAddUInt64() throws Exception {

        final Object bignum = ctor.newInstance();

        final Method addUInt64 = method("addUInt64", long.class);
        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method shiftLeft = method("shiftLeft", int.class);
        final Method toHexString = method("toHexString");

        assignHexString.invoke(bignum, "0");
        addUInt64.invoke(bignum, 0xA);
        assertEquals(toHexString.invoke(bignum), "A");

        assignHexString.invoke(bignum, "1");
        addUInt64.invoke(bignum, 0xA);
        assertEquals(toHexString.invoke(bignum), "B");

        assignHexString.invoke(bignum, "1");
        addUInt64.invoke(bignum, 0x100);
        assertEquals(toHexString.invoke(bignum), "101");

        assignHexString.invoke(bignum, "1");
        addUInt64.invoke(bignum, 0xFFFF);
        assertEquals(toHexString.invoke(bignum), "10000");

        assignHexString.invoke(bignum, "FFFFFFF");
        addUInt64.invoke(bignum, 0x1);
        assertEquals(toHexString.invoke(bignum), "10000000");

        assignHexString.invoke(bignum, "10000000000000000000000000000000000000000000");
        addUInt64.invoke(bignum, 0xFFFF);
        assertEquals(toHexString.invoke(bignum), "1000000000000000000000000000000000000000FFFF");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        addUInt64.invoke(bignum, 0x1);
        assertEquals(toHexString.invoke(bignum), "100000000000000000000000000000000000000000000");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        addUInt64.invoke(bignum, 0x1);
        assertEquals(toHexString.invoke(bignum), "100000000000000000000000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        addUInt64.invoke(bignum, 1);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000001");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        addUInt64.invoke(bignum, 0xFFFF);
        assertEquals(toHexString.invoke(bignum), "1000000000000000000000FFFF");

        assignHexString.invoke(bignum, "0");
        addUInt64.invoke(bignum, 0xA00000000L);
        assertEquals(toHexString.invoke(bignum), "A00000000");

        assignHexString.invoke(bignum, "1");
        addUInt64.invoke(bignum, 0xA00000000L);
        assertEquals(toHexString.invoke(bignum), "A00000001");

        assignHexString.invoke(bignum, "1");
        addUInt64.invoke(bignum, 0x10000000000L);
        assertEquals(toHexString.invoke(bignum), "10000000001");

        assignHexString.invoke(bignum, "1");
        addUInt64.invoke(bignum, 0xFFFF00000000L);
        assertEquals(toHexString.invoke(bignum), "FFFF00000001");

        assignHexString.invoke(bignum, "FFFFFFF");
        addUInt64.invoke(bignum, 0x100000000L);
        assertEquals(toHexString.invoke(bignum), "10FFFFFFF");

        assignHexString.invoke(bignum, "10000000000000000000000000000000000000000000");
        addUInt64.invoke(bignum, 0xFFFF00000000L);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000000000000FFFF00000000");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        addUInt64.invoke(bignum, 0x100000000L);
        assertEquals(toHexString.invoke(bignum), "1000000000000000000000000000000000000FFFFFFFF");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        addUInt64.invoke(bignum, 0x100000000L);
        assertEquals(toHexString.invoke(bignum), "10000000000000000100000000");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        addUInt64.invoke(bignum, 0xFFFF00000000L);
        assertEquals(toHexString.invoke(bignum), "10000000000000FFFF00000000");
    }

    @Test
    public void testAddBignum() throws Exception {

        final Object bignum = ctor.newInstance();
        final Object other = ctor.newInstance();

        final Method addBignum = method("addBignum", Bignum);
        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method shiftLeft = method("shiftLeft", int.class);
        final Method toHexString = method("toHexString");

        assignHexString.invoke(other, "1");
        assignHexString.invoke(bignum, "0");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "1");

        assignHexString.invoke(bignum, "1");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "2");

        assignHexString.invoke(bignum, "FFFFFFF");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFF");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "100000000000000");

        assignHexString.invoke(bignum, "10000000000000000000000000000000000000000000");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000000000000000000000001");

        assignHexString.invoke(other, "1000000000000");

        assignHexString.invoke(bignum, "1");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "1000000000001");

        assignHexString.invoke(bignum, "FFFFFFF");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "100000FFFFFFF");

        assignHexString.invoke(bignum, "10000000000000000000000000000000000000000000");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000000000001000000000000");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "1000000000000000000000000000000FFFFFFFFFFFF");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000000001000000000000");

        shiftLeft.invoke(other, 64);
        // other == "10000000000000000000000000000"

        assignUInt16.invoke(bignum, (char) 0x1);
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000000001");

        assignHexString.invoke(bignum, "FFFFFFF");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "1000000000000000000000FFFFFFF");

        assignHexString.invoke(bignum, "10000000000000000000000000000000000000000000");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000000000010000000000000000000000000000");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "100000000000000FFFFFFFFFFFFFFFFFFFFFFFFFFFF");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        addBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10010000000000000000000000000");
    }


    @Test
    public void testSubtractBignum() throws Exception {

        final Object bignum = ctor.newInstance();
        final Object other = ctor.newInstance();

        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method shiftLeft = method("shiftLeft", int.class);
        final Method subtractBignum = method("subtractBignum", Bignum);

        final Method toHexString = method("toHexString");

        assignHexString.invoke(bignum, "1");
        assignHexString.invoke(other, "0");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "1");

        assignHexString.invoke(bignum, "2");
        assignHexString.invoke(other, "0");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "2");

        assignHexString.invoke(bignum, "10000000");
        assignHexString.invoke(other, "1");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFF");

        assignHexString.invoke(bignum, "100000000000000");
        assignHexString.invoke(other, "1");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFF");

        assignHexString.invoke(bignum, "10000000000000000000000000000000000000000001");
        assignHexString.invoke(other, "1");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000000000000000000000000");

        assignHexString.invoke(bignum, "1000000000001");
        assignHexString.invoke(other, "1000000000000");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "1");

        assignHexString.invoke(bignum, "100000FFFFFFF");
        assignHexString.invoke(other, "1000000000000");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFF");

        assignHexString.invoke(bignum, "10000000000000000000000000000001000000000000");
        assignHexString.invoke(other, "1000000000000");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000000000000000000000000");

        assignHexString.invoke(bignum, "1000000000000000000000000000000FFFFFFFFFFFF");
        assignHexString.invoke(other, "1000000000000");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        // "10 0000 0000 0000 0000 0000 0000"
        assignHexString.invoke(other, "1000000000000");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFF000000000000");

        assignHexString.invoke(other, "1000000000000");
        shiftLeft.invoke(other, 48);
        // other == "1000000000000000000000000"

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        // bignum == "10000000000000000000000000"
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "F000000000000000000000000");

        assignUInt16.invoke(other, (char) 0x1);
        shiftLeft.invoke(other, 35);
        // other == "800000000"
        assignHexString.invoke(bignum, "FFFFFFF");
        shiftLeft.invoke(bignum, 60);
        // bignum = FFFFFFF000000000000000
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFEFFFFFF800000000");

        assignHexString.invoke(bignum, "10000000000000000000000000000000000000000000");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF800000000");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        subtractBignum.invoke(bignum, other);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF7FFFFFFFF");
    }


    @Test
    public void testMultiplyUInt32() throws Exception {

        final Object bignum = ctor.newInstance();

        final Method assignHexString = method("assignHexString", String.class);
        final Method assignDecimalString = method("assignDecimalString", String.class);
        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method multiplyByUInt32 = method("multiplyByUInt32", int.class);
        final Method shiftLeft = method("shiftLeft", int.class);
        final Method toHexString = method("toHexString");

        assignHexString.invoke(bignum, "0");
        multiplyByUInt32.invoke(bignum, 0x25);
        assertEquals(toHexString.invoke(bignum), "0");

        assignHexString.invoke(bignum, "2");
        multiplyByUInt32.invoke(bignum, 0x5);
        assertEquals(toHexString.invoke(bignum), "A");

        assignHexString.invoke(bignum, "10000000");
        multiplyByUInt32.invoke(bignum, 0x9);
        assertEquals(toHexString.invoke(bignum), "90000000");

        assignHexString.invoke(bignum, "100000000000000");
        multiplyByUInt32.invoke(bignum, 0xFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFF00000000000000");

        assignHexString.invoke(bignum, "100000000000000");
        multiplyByUInt32.invoke(bignum, 0xFFFFFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFF00000000000000");

        assignHexString.invoke(bignum, "1234567ABCD");
        multiplyByUInt32.invoke(bignum, 0xFFF);
        assertEquals(toHexString.invoke(bignum), "12333335552433");

        assignHexString.invoke(bignum, "1234567ABCD");
        multiplyByUInt32.invoke(bignum, 0xFFFFFFF);
        assertEquals(toHexString.invoke(bignum), "12345679998A985433");


        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt32.invoke(bignum, 0x2);
        assertEquals(toHexString.invoke(bignum), "1FFFFFFFFFFFFFFFE");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt32.invoke(bignum, 0x4);
        assertEquals(toHexString.invoke(bignum), "3FFFFFFFFFFFFFFFC");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt32.invoke(bignum, 0xF);
        assertEquals(toHexString.invoke(bignum), "EFFFFFFFFFFFFFFF1");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt32.invoke(bignum, 0xFFFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFFFEFFFFFFFFFF000001");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        // "10 0000 0000 0000 0000 0000 0000"
        multiplyByUInt32.invoke(bignum, 2);
        assertEquals(toHexString.invoke(bignum), "20000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        // "10 0000 0000 0000 0000 0000 0000"
        multiplyByUInt32.invoke(bignum, 0xF);
        assertEquals(toHexString.invoke(bignum), "F0000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0xFFFF);
        shiftLeft.invoke(bignum, 100);
        // "FFFF0 0000 0000 0000 0000 0000 0000"
        multiplyByUInt32.invoke(bignum, 0xFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFE00010000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0xFFFF);
        shiftLeft.invoke(bignum, 100);
        // "FFFF0 0000 0000 0000 0000 0000 0000"
        multiplyByUInt32.invoke(bignum, 0xFFFFFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFEFFFF00010000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0xFFFF);
        shiftLeft.invoke(bignum, 100);
        // "FFFF0 0000 0000 0000 0000 0000 0000"
        multiplyByUInt32.invoke(bignum, 0xFFFFFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFEFFFF00010000000000000000000000000");

        assignDecimalString.invoke(bignum, "15611230384529777");
        multiplyByUInt32.invoke(bignum, 10000000);
        assertEquals(toHexString.invoke(bignum), "210EDD6D4CDD2580EE80");
    }



    @Test
    public void testMultiplyUInt64() throws Exception {

        final Object bignum = ctor.newInstance();

        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignDecimalString = method("assignDecimalString", String.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method multiplyByUInt64 = method("multiplyByUInt64", long.class);
        final Method shiftLeft = method("shiftLeft", int.class);
        final Method toHexString = method("toHexString");

        assignHexString.invoke(bignum, "0");
        multiplyByUInt64.invoke(bignum, 0x25);
        assertEquals(toHexString.invoke(bignum), "0");

        assignHexString.invoke(bignum, "2");
        multiplyByUInt64.invoke(bignum, 0x5);
        assertEquals(toHexString.invoke(bignum), "A");

        assignHexString.invoke(bignum, "10000000");
        multiplyByUInt64.invoke(bignum, 0x9);
        assertEquals(toHexString.invoke(bignum), "90000000");

        assignHexString.invoke(bignum, "100000000000000");
        multiplyByUInt64.invoke(bignum, 0xFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFF00000000000000");

        assignHexString.invoke(bignum, "100000000000000");
        multiplyByUInt64.invoke(bignum, 0xFFFFFFFFFFFFFFFFL);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFFFF00000000000000");

        assignHexString.invoke(bignum, "1234567ABCD");
        multiplyByUInt64.invoke(bignum, 0xFFF);
        assertEquals(toHexString.invoke(bignum), "12333335552433");

        assignHexString.invoke(bignum, "1234567ABCD");
        multiplyByUInt64.invoke(bignum, 0xFFFFFFFFFFL);
        assertEquals(toHexString.invoke(bignum), "1234567ABCBDCBA985433");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt64.invoke(bignum, 0x2);
        assertEquals(toHexString.invoke(bignum), "1FFFFFFFFFFFFFFFE");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt64.invoke(bignum, 0x4);
        assertEquals(toHexString.invoke(bignum), "3FFFFFFFFFFFFFFFC");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt64.invoke(bignum, 0xF);
        assertEquals(toHexString.invoke(bignum), "EFFFFFFFFFFFFFFF1");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFFFF");
        multiplyByUInt64.invoke(bignum, 0xFFFFFFFFFFFFFFFFL);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFFFE0000000000000001");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        // "10 0000 0000 0000 0000 0000 0000"
        multiplyByUInt64.invoke(bignum, 2);
        assertEquals(toHexString.invoke(bignum), "20000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0x1);
        shiftLeft.invoke(bignum, 100);
        // "10 0000 0000 0000 0000 0000 0000"
        multiplyByUInt64.invoke(bignum, 0xF);
        assertEquals(toHexString.invoke(bignum), "F0000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0xFFFF);
        shiftLeft.invoke(bignum, 100);
        // "FFFF0 0000 0000 0000 0000 0000 0000"
        multiplyByUInt64.invoke(bignum, 0xFFFF);
        assertEquals(toHexString.invoke(bignum), "FFFE00010000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0xFFFF);
        shiftLeft.invoke(bignum, 100);
        // "FFFF0 0000 0000 0000 0000 0000 0000"
        multiplyByUInt64.invoke(bignum, 0xFFFFFFFFL);
        assertEquals(toHexString.invoke(bignum), "FFFEFFFF00010000000000000000000000000");

        assignUInt16.invoke(bignum, (char) 0xFFFF);
        shiftLeft.invoke(bignum, 100);
        // "FFFF0 0000 0000 0000 0000 0000 0000"
        multiplyByUInt64.invoke(bignum, 0xFFFFFFFFFFFFFFFFL);
        assertEquals(toHexString.invoke(bignum), "FFFEFFFFFFFFFFFF00010000000000000000000000000");

        assignDecimalString.invoke(bignum, "15611230384529777");
        multiplyByUInt64.invoke(bignum, 0x8ac7230489e80000L);
        assertEquals(toHexString.invoke(bignum), "1E10EE4B11D15A7F3DE7F3C7680000");
    }

    @Test
    public void testMultiplyPowerOfTen() throws Exception {

        final Object bignum = ctor.newInstance();
        final Object bignum2 = ctor.newInstance();

        final Method assignBignum = method("assignBignum", Bignum);
        final Method assignDecimalString = method("assignDecimalString", String.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method multiplyByPowerOfTen = method("multiplyByPowerOfTen", int.class);
        final Method toHexString = method("toHexString");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 1);
        assertEquals(toHexString.invoke(bignum), "3034");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 2);
        assertEquals(toHexString.invoke(bignum), "1E208");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 3);
        assertEquals(toHexString.invoke(bignum), "12D450");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 4);
        assertEquals(toHexString.invoke(bignum), "BC4B20");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 5);
        assertEquals(toHexString.invoke(bignum), "75AEF40");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 6);
        assertEquals(toHexString.invoke(bignum), "498D5880");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 7);
        assertEquals(toHexString.invoke(bignum), "2DF857500");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 8);
        assertEquals(toHexString.invoke(bignum), "1CBB369200");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 9);
        assertEquals(toHexString.invoke(bignum), "11F5021B400");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 10);
        assertEquals(toHexString.invoke(bignum), "B3921510800");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 11);
        assertEquals(toHexString.invoke(bignum), "703B4D2A5000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 12);
        assertEquals(toHexString.invoke(bignum), "4625103A72000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 13);
        assertEquals(toHexString.invoke(bignum), "2BD72A24874000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 14);
        assertEquals(toHexString.invoke(bignum), "1B667A56D488000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 15);
        assertEquals(toHexString.invoke(bignum), "11200C7644D50000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 16);
        assertEquals(toHexString.invoke(bignum), "AB407C9EB0520000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 17);
        assertEquals(toHexString.invoke(bignum), "6B084DE32E3340000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 18);
        assertEquals(toHexString.invoke(bignum), "42E530ADFCE0080000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 19);
        assertEquals(toHexString.invoke(bignum), "29CF3E6CBE0C0500000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 20);
        assertEquals(toHexString.invoke(bignum), "1A218703F6C783200000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 21);
        assertEquals(toHexString.invoke(bignum), "1054F4627A3CB1F400000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 22);
        assertEquals(toHexString.invoke(bignum), "A3518BD8C65EF38800000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 23);
        assertEquals(toHexString.invoke(bignum), "6612F7677BFB5835000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 24);
        assertEquals(toHexString.invoke(bignum), "3FCBDAA0AD7D17212000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 25);
        assertEquals(toHexString.invoke(bignum), "27DF68A46C6E2E74B4000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 26);
        assertEquals(toHexString.invoke(bignum), "18EBA166C3C4DD08F08000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 27);
        assertEquals(toHexString.invoke(bignum), "F9344E03A5B0A259650000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 28);
        assertEquals(toHexString.invoke(bignum), "9BC0B0C2478E6577DF20000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 29);
        assertEquals(toHexString.invoke(bignum), "61586E796CB8FF6AEB740000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 30);
        assertEquals(toHexString.invoke(bignum), "3CD7450BE3F39FA2D32880000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 31);
        assertEquals(toHexString.invoke(bignum), "26068B276E7843C5C3F9500000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 50);
        assertEquals(toHexString.invoke(bignum), "149D1B4CFED03B23AB5F4E1196EF45C08000000000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 100);
        assertEquals(toHexString.invoke(bignum),
                "5827249F27165024FBC47DFCA9359BF316332D1B91ACEECF471FBAB06D9B2" +
                "0000000000000000000000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 200);
        assertEquals(toHexString.invoke(bignum),
                "64C1F5C06C3816AFBF8DAFD5A3D756365BB0FD020E6F084E759C1F7C99E4F" +
                "55B9ACC667CEC477EB958C2AEEB3C6C19BA35A1AD30B35C51EB72040920000" +
                "0000000000000000000000000000000000000000000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 500);
        assertEquals(toHexString.invoke(bignum),
                "96741A625EB5D7C91039FEB5C5ACD6D9831EDA5B083D800E6019442C8C8223" +
                "3EAFB3501FE2058062221E15121334928880827DEE1EC337A8B26489F3A40A" +
                "CB440A2423734472D10BFCE886F41B3AF9F9503013D86D088929CA86EEB4D8" +
                "B9C831D0BD53327B994A0326227CFD0ECBF2EB48B02387AAE2D4CCCDF1F1A1" +
                "B8CC4F1FA2C56AD40D0E4DAA9C28CDBF0A549098EA13200000000000000000" +
                "00000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000");

        assignDecimalString.invoke(bignum, "1234");
        multiplyByPowerOfTen.invoke(bignum, 1000);
        assertEquals(toHexString.invoke(bignum),
                "1258040F99B1CD1CC9819C676D413EA50E4A6A8F114BB0C65418C62D399B81" +
                "6361466CA8E095193E1EE97173553597C96673AF67FAFE27A66E7EF2E5EF2E" +
                "E3F5F5070CC17FE83BA53D40A66A666A02F9E00B0E11328D2224B8694C7372" +
                "F3D536A0AD1985911BD361496F268E8B23112500EAF9B88A9BC67B2AB04D38" +
                "7FEFACD00F5AF4F764F9ABC3ABCDE54612DE38CD90CB6647CA389EA0E86B16" +
                "BF7A1F34086E05ADBE00BD1673BE00FAC4B34AF1091E8AD50BA675E0381440" +
                "EA8E9D93E75D816BAB37C9844B1441C38FC65CF30ABB71B36433AF26DD97BD" +
                "ABBA96C03B4919B8F3515B92826B85462833380DC193D79F69D20DD6038C99" +
                "6114EF6C446F0BA28CC772ACBA58B81C04F8FFDE7B18C4E5A3ABC51E637FDF" +
                "6E37FDFF04C940919390F4FF92000000000000000000000000000000000000" +
                "00000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000");

        assignHexString.invoke(bignum2,
                "3DA774C07FB5DF54284D09C675A492165B830D5DAAEB2A7501" +
                "DA17CF9DFA1CA2282269F92A25A97314296B717E3DCBB9FE17" +
                "41A842FE2913F540F40796F2381155763502C58B15AF7A7F88" +
                "6F744C9164FF409A28F7FA0C41F89ED79C1BE9F322C8578B97" +
                "841F1CBAA17D901BE1230E3C00E1C643AF32638B5674E01FEA" +
                "96FC90864E621B856A9E1CE56E6EB545B9C2F8F0CC10DDA88D" +
                "CC6D282605F8DB67044F2DFD3695E7BA63877AE16701536AE6" +
                "567C794D0BFE338DFBB42D92D4215AF3BB22BF0A8B283FDDC2" +
                "C667A10958EA6D2");
        assertEquals(toHexString.invoke(bignum2),
                "3DA774C07FB5DF54284D09C675A492165B830D5DAAEB2A7501" +
                "DA17CF9DFA1CA2282269F92A25A97314296B717E3DCBB9FE17" +
                "41A842FE2913F540F40796F2381155763502C58B15AF7A7F88" +
                "6F744C9164FF409A28F7FA0C41F89ED79C1BE9F322C8578B97" +
                "841F1CBAA17D901BE1230E3C00E1C643AF32638B5674E01FEA" +
                "96FC90864E621B856A9E1CE56E6EB545B9C2F8F0CC10DDA88D" +
                "CC6D282605F8DB67044F2DFD3695E7BA63877AE16701536AE6" +
                "567C794D0BFE338DFBB42D92D4215AF3BB22BF0A8B283FDDC2" +
                "C667A10958EA6D2");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 1);

        assertEquals(toHexString.invoke(bignum),
                "2688A8F84FD1AB949930261C0986DB4DF931E85A8AD2FA8921284EE1C2BC51" +
                "E55915823BBA5789E7EC99E326EEE69F543ECE890929DED9AC79489884BE57" +
                "630AD569E121BB76ED8DAC8FB545A8AFDADF1F8860599AFC47A93B6346C191" +
                "7237F5BD36B73EB29371F4A4EE7A116CB5E8E5808D1BEA4D7F7E3716090C13" +
                "F29E5DDA53F0FD513362A2D20F6505314B9419DB967F8A8A89589FC43917C3" +
                "BB892062B17CBE421DB0D47E34ACCCE060D422CFF60DCBD0277EE038BD509C" +
                "7BC494D8D854F5B76696F927EA99BC00C4A5D7928434");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 2);

        assertEquals(toHexString.invoke(bignum),
                "1815699B31E30B3CDFBE17D185F44910BBBF313896C3DC95B4B9314D19B5B32" +
                "F57AD71655476B630F3E02DF855502394A74115A5BA2B480BCBCD5F52F6F69D" +
                "E6C5622CB5152A54788BD9D14B896DE8CB73B53C3800DDACC9C51E0C38FAE76" +
                "2F9964232872F9C2738E7150C4AE3F1B18F70583172706FAEE26DC5A78C77A2" +
                "FAA874769E52C01DA5C3499F233ECF3C90293E0FB69695D763DAA3AEDA5535B" +
                "43DAEEDF6E9528E84CEE0EC000C3C8495C1F9C89F6218AF4C23765261CD5ADD" +
                "0787351992A01E5BB8F2A015807AE7A6BB92A08");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 5);

        assertEquals(toHexString.invoke(bignum),
                "5E13A4863ADEE3E5C9FE8D0A73423D695D62D8450CED15A8C9F368952C6DC3" +
                "F0EE7D82F3D1EFB7AF38A3B3920D410AFCAD563C8F5F39116E141A3C5C14B3" +
                "58CD73077EA35AAD59F6E24AD98F10D5555ABBFBF33AC361EAF429FD5FBE94" +
                "17DA9EF2F2956011F9F93646AA38048A681D984ED88127073443247CCC167C" +
                "B354A32206EF5A733E73CF82D795A1AD598493211A6D613C39515E0E0F6304" +
                "DCD9C810F3518C7F6A7CB6C81E99E02FCC65E8FDB7B7AE97306CC16A8631CE" +
                "0A2AEF6568276BE4C176964A73C153FDE018E34CB4C2F40");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 10);

        assertEquals(toHexString.invoke(bignum),
                "8F8CB8EB51945A7E815809F6121EF2F4E61EF3405CD9432CAD2709749EEAFD" +
                "1B81E843F14A3667A7BDCCC9E0BB795F63CDFDB62844AC7438976C885A0116" +
                "29607DA54F9C023CC366570B7637ED0F855D931752038A614922D0923E382C" +
                "B8E5F6C975672DB76E0DE471937BB9EDB11E28874F1C122D5E1EF38CECE9D0" +
                "0723056BCBD4F964192B76830634B1D322B7EB0062F3267E84F5C824343A77" +
                "4B7DCEE6DD464F01EBDC8C671BB18BB4EF4300A42474A6C77243F2A12B03BF" +
                "0443C38A1C0D2701EDB393135AE0DEC94211F9D4EB51F990800");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 50);

        assertEquals(toHexString.invoke(bignum),
                "107A8BE345E24407372FC1DE442CBA696BC23C4FFD5B4BDFD9E5C39559815" +
                "86628CF8472D2D589F2FC2BAD6E0816EC72CBF85CCA663D8A1EC6C51076D8" +
                "2D247E6C26811B7EC4D4300FB1F91028DCB7B2C4E7A60C151161AA7E65E79" +
                "B40917B12B2B5FBE7745984D4E8EFA31F9AE6062427B068B144A9CB155873" +
                "E7C0C9F0115E5AC72DC5A73C4796DB970BF9205AB8C77A6996EB1B417F9D1" +
                "6232431E6313C392203601B9C22CC10DDA88DCC6D282605F8DB67044F2DFD" +
                "3695E7BA63877AE16701536AE6567C794D0BFE338DFBB42D924CF964BD2C0" +
                "F586E03A2FCD35A408000000000000");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 100);

        assertEquals(toHexString.invoke(bignum),
                "46784A90ACD0ED3E7759CC585FB32D36EB6034A6F78D92604E3BAA5ED3D8B" +
                "6E60E854439BE448897FB4B7EA5A3D873AA0FCB3CFFD80D0530880E45F511" +
                "722A50CE7E058B5A6F5464DB7500E34984EE3202A9441F44FA1554C0CEA96" +
                "B438A36F25E7C9D56D71AE2CD313EC37534DA299AC0854FC48591A7CF3171" +
                "31265AA4AE62DE32344CE7BEEEF894AE686A2DAAFE5D6D9A10971FFD9C064" +
                "5079B209E1048F58B5192D41D84336AC4C8C489EEF00939CFC9D55C122036" +
                "01B9C22CC10DDA88DCC6D282605F8DB67044F2DFD3695E7BA3F67B96D3A32" +
                "E11FB5561B68744C4035B0800DC166D49D98E3FD1D5BB2000000000000000" +
                "0000000000");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 200);

        assertEquals(toHexString.invoke(bignum),
                "508BD351221DF139D72D88CDC0416845A53EE2D0E6B98352509A9AC312F8C" +
                "6CB1A144889416201E0B6CE66EA3EBE259B5FD79ECFC1FD77963CE516CC7E" +
                "2FE73D4B5B710C19F6BCB092C7A2FD76286543B8DBD2C596DFF2C896720BA" +
                "DFF7BC9C366ACEA3A880AEC287C5E6207DF2739B5326FC19D773BD830B109" +
                "ED36C7086544BF8FDB9D4B73719C2B5BC2F571A5937EC46876CD428281F6B" +
                "F287E1E07F25C1B1D46BC37324FF657A8B2E0071DB83B86123CA34004F406" +
                "001082D7945E90C6E8C9A9FEC2B44BE0DDA46E9F52B152E4D1336D2FCFBC9" +
                "96E30CA0082256737365158FE36482AA7EB9DAF2AB128F10E7551A3CD5BE6" +
                "0A922F3A7D5EED38B634A7EC95BCF7021BA6820A292000000000000000000" +
                "00000000000000000000000000000000");

        assignBignum.invoke(bignum, bignum2);
        multiplyByPowerOfTen.invoke(bignum, 500);

        assertEquals(toHexString.invoke(bignum),
                "7845F900E475B5086885BAAAE67C8E85185ACFE4633727F82A4B06B5582AC" +
                "BE933C53357DA0C98C20C5AC900C4D76A97247DF52B79F48F9E35840FB715" +
                "D392CE303E22622B0CF82D9471B398457DD3196F639CEE8BBD2C146873841" +
                "F0699E6C41F04FC7A54B48CEB995BEB6F50FE81DE9D87A8D7F849CC523553" +
                "7B7BBBC1C7CAAFF6E9650BE03B308C6D31012AEF9580F70D3EE2083ADE126" +
                "8940FA7D6308E239775DFD2F8C97FF7EBD525DAFA6512216F7047A62A93DC" +
                "38A0165BDC67E250DCC96A0181DE935A70B38704DC71819F02FC5261FF7E1" +
                "E5F11907678B0A3E519FF4C10A867B0C26CE02BE6960BA8621A87303C101C" +
                "3F88798BB9F7739655946F8B5744E6B1EAF10B0C5621330F0079209033C69" +
                "20DE2E2C8D324F0624463735D482BF291926C22A910F5B80FA25170B6B57D" +
                "8D5928C7BCA3FE87461275F69BD5A1B83181DAAF43E05FC3C72C4E93111B6" +
                "6205EBF49B28FEDFB7E7526CBDA658A332000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000");
    }


    @Test
    public void testDivideModuloIntBignum() throws Exception {

        final Object bignum = ctor.newInstance();
        final Object other = ctor.newInstance();
        final Object third = ctor.newInstance();

        final Method addBignum = method("addBignum", Bignum);
        final Method assignBignum = method("assignBignum", Bignum);
        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method divideModuloIntBignum = method("divideModuloIntBignum", Bignum);
        final Method multiplyByUInt32 = method("multiplyByUInt32", int.class);
        final Method shiftLeft = method("shiftLeft", int.class);
        final Method subtractBignum = method("subtractBignum", Bignum);
        final Method toHexString = method("toHexString");

        assignUInt16.invoke(bignum, (char) 10);
        assignUInt16.invoke(other, (char) 2);
        assertEquals((char) 5, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "0");

        assignUInt16.invoke(bignum, (char) 10);
        shiftLeft.invoke(bignum, 500);
        assignUInt16.invoke(other, (char) 2);
        shiftLeft.invoke(other, 500);
        assertEquals((char) 5, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "0");

        assignUInt16.invoke(bignum, (char) 11);
        assignUInt16.invoke(other, (char) 2);
        assertEquals((char) 5, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "1");

        assignUInt16.invoke(bignum, (char) 10);
        shiftLeft.invoke(bignum, 500);
        assignUInt16.invoke(other, (char) 1);
        addBignum.invoke(bignum, other);
        assignUInt16.invoke(other, (char) 2);
        shiftLeft.invoke(other, 500);
        assertEquals((char) 5, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "1");

        assignUInt16.invoke(bignum, (char) 10);
        shiftLeft.invoke(bignum, 500);
        assignBignum.invoke(other, bignum);
        multiplyByUInt32.invoke(bignum, 0x1234);
        assignUInt16.invoke(third, (char) 0xFFF);
        addBignum.invoke(bignum, third);
        assertEquals((char) 0x1234, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "FFF");

        assignUInt16.invoke(bignum, (char) 10);
        assignHexString.invoke(other, "1234567890");
        assertEquals((char) 0, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "A");

        assignHexString.invoke(bignum, "12345678");
        assignHexString.invoke(other, "3789012");
        assertEquals((char) 5, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "D9861E");

        assignHexString.invoke(bignum, "70000001");
        assignHexString.invoke(other, "1FFFFFFF");
        assertEquals((char) 3, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "10000004");

        assignHexString.invoke(bignum, "28000000");
        assignHexString.invoke(other, "12A05F20");
        assertEquals((char) 2, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "2BF41C0");

        assignUInt16.invoke(bignum, (char) 10);
        shiftLeft.invoke(bignum, 500);
        assignBignum.invoke(other, bignum);
        multiplyByUInt32.invoke(bignum, 0x1234);
        assignUInt16.invoke(third, (char) 0xFFF);
        subtractBignum.invoke(other, third);
        assertEquals((char) 0x1234, (char)  divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "1232DCC");
        assertEquals((char) 0, (char) divideModuloIntBignum.invoke(bignum, other));
        assertEquals(toHexString.invoke(bignum), "1232DCC");
    }


    @Test
    public void testCompare() throws Exception {

        final Object bignum1 = ctor.newInstance();
        final Object bignum2 = ctor.newInstance();

        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method compare = method("compare", Bignum, Bignum);
        final Method equal = method("equal", Bignum, Bignum);
        final Method less = method("less", Bignum, Bignum);
        final Method lessEqual = method("lessEqual", Bignum, Bignum);
        final Method shiftLeft = method("shiftLeft", int.class);

        assignUInt16.invoke(bignum1, (char) 1);
        assignUInt16.invoke(bignum2, (char) 1);
        assertEquals(0, compare.invoke(null, bignum1, bignum2));
        assertTrue((boolean) equal.invoke(null, bignum1, bignum2));
        assertTrue((boolean) lessEqual.invoke(null, bignum1, bignum2));
        assertTrue(!(boolean) less.invoke(null, bignum1, bignum2));

        assignUInt16.invoke(bignum1, (char) 0);
        assignUInt16.invoke(bignum2, (char) 1);
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));
        assertTrue(!(boolean) equal.invoke(null, bignum1, bignum2));
        assertTrue(!(boolean) equal.invoke(null, bignum2, bignum1));
        assertTrue((boolean) lessEqual.invoke(null, bignum1, bignum2));
        assertTrue(!(boolean) lessEqual.invoke(null, bignum2, bignum1));
        assertTrue((boolean) less.invoke(null, bignum1, bignum2));
        assertTrue(!(boolean) less.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "1234567890ABCDEF12345");
        assignHexString.invoke(bignum2, "1234567890ABCDEF12345");
        assertEquals(0, compare.invoke(null, bignum1, bignum2));

        assignHexString.invoke(bignum1, "1234567890ABCDEF12345");
        assignHexString.invoke(bignum2, "1234567890ABCDEF12346");
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "1234567890ABCDEF12345");
        shiftLeft.invoke(bignum1, 500);
        assignHexString.invoke(bignum2, "1234567890ABCDEF12345");
        shiftLeft.invoke(bignum2, 500);
        assertEquals(0, compare.invoke(null, bignum1, bignum2));

        assignHexString.invoke(bignum1, "1234567890ABCDEF12345");
        shiftLeft.invoke(bignum1, 500);
        assignHexString.invoke(bignum2, "1234567890ABCDEF12346");
        shiftLeft.invoke(bignum2, 500);
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));

        assignUInt16.invoke(bignum1, (char) 1);
        shiftLeft.invoke(bignum1, 64);
        assignHexString.invoke(bignum2, "10000000000000000");
        assertEquals(0, compare.invoke(null, bignum1, bignum2));
        assertEquals(0, compare.invoke(null, bignum2, bignum1));

        assignUInt16.invoke(bignum1, (char) 1);
        shiftLeft.invoke(bignum1, 64);
        assignHexString.invoke(bignum2, "10000000000000001");
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));

        assignUInt16.invoke(bignum1, (char) 1);
        shiftLeft.invoke(bignum1, 96);
        assignHexString.invoke(bignum2, "10000000000000001");
        shiftLeft.invoke(bignum2, 32);
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "FFFFFFFFFFFFFFFF");
        assignUInt16.invoke(bignum2, (char) 1);
        shiftLeft.invoke(bignum2, 64);
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "FFFFFFFFFFFFFFFF");
        shiftLeft.invoke(bignum1, 32);
        assignUInt16.invoke(bignum2, (char) 1);
        shiftLeft.invoke(bignum2, 96);
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "FFFFFFFFFFFFFFFF");
        shiftLeft.invoke(bignum1, 32);
        assignUInt16.invoke(bignum2, (char) 1);
        shiftLeft.invoke(bignum2, 95);
        assertEquals(+1, compare.invoke(null, bignum1, bignum2));
        assertEquals(-1, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "FFFFFFFFFFFFFFFF");
        shiftLeft.invoke(bignum1, 32);
        assignUInt16.invoke(bignum2, (char) 1);
        shiftLeft.invoke(bignum2, 100);
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "100000000000000");
        assignUInt16.invoke(bignum2, (char) 1);
        shiftLeft.invoke(bignum2, 14*4);
        assertEquals(0, compare.invoke(null, bignum1, bignum2));
        assertEquals(0, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "100000000000001");
        assignUInt16.invoke(bignum2, (char) 1);
        shiftLeft.invoke(bignum2, 14 * 4);
        assertEquals(+1, compare.invoke(null, bignum1, bignum2));
        assertEquals(-1, compare.invoke(null, bignum2, bignum1));

        assignHexString.invoke(bignum1, "200000000000000");
        assignUInt16.invoke(bignum2, (char) 3);
        shiftLeft.invoke(bignum2, 14*4);
        assertEquals(-1, compare.invoke(null, bignum1, bignum2));
        assertEquals(+1, compare.invoke(null, bignum2, bignum1));
    }


    @Test
    public void testPlusCompare() throws Exception {

        final Object a = ctor.newInstance();
        final Object b = ctor.newInstance();
        final Object c = ctor.newInstance();

        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method plusCompare = method("plusCompare", Bignum, Bignum, Bignum);
        final Method plusEqual = method("plusEqual", Bignum, Bignum, Bignum);
        final Method plusLess = method("plusLess", Bignum, Bignum, Bignum);
        final Method plusLessEqual = method("plusLessEqual", Bignum, Bignum, Bignum);
        final Method shiftLeft = method("shiftLeft", int.class);

        assignUInt16.invoke(a, (char) 1);
        assignUInt16.invoke(b, (char) 0);
        assignUInt16.invoke(c, (char) 1);
        assertEquals(0, plusCompare.invoke(null, a, b, c));
        assertTrue((boolean) plusEqual.invoke(null, a, b, c));
        assertTrue((boolean) plusLessEqual.invoke(null, a, b, c));
        assertTrue(!(boolean) plusLess.invoke(null, a, b, c));

        assignUInt16.invoke(a, (char) 0);
        assignUInt16.invoke(b, (char) 0);
        assignUInt16.invoke(c, (char) 1);
        assertEquals(-1, plusCompare.invoke(null, a, b, c));
        assertEquals(+1, plusCompare.invoke(null, c, b, a));
        assertTrue(!(boolean) plusEqual.invoke(null, a, b, c));
        assertTrue(!(boolean) plusEqual.invoke(null, c, b, a));
        assertTrue((boolean) plusLessEqual.invoke(null, a, b, c));
        assertTrue(!(boolean) plusLessEqual.invoke(null, c, b, a));
        assertTrue((boolean) plusLess.invoke(null, a, b, c));
        assertTrue(!(boolean) plusLess.invoke(null, c, b, a));

        assignHexString.invoke(a, "1234567890ABCDEF12345");
        assignUInt16.invoke(b, (char) 1);
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        assertEquals(+1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890ABCDEF12344");
        assignUInt16.invoke(b, (char) 1);
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        assertEquals(0, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4);
        assignHexString.invoke(b, "ABCDEF12345");
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        assertEquals(0, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4);
        assignHexString.invoke(b, "ABCDEF12344");
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4);
        assignHexString.invoke(b, "ABCDEF12346");
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        assertEquals(1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567891");
        shiftLeft.invoke(a, 11 * 4);
        assignHexString.invoke(b, "ABCDEF12345");
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        assertEquals(1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567889");
        shiftLeft.invoke(a, 11 * 4);
        assignHexString.invoke(b, "ABCDEF12345");
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        shiftLeft.invoke(c, 32);
        assertEquals(0, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12344");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        shiftLeft.invoke(c, 32);
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12346");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        shiftLeft.invoke(c, 32);
        assertEquals(1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567891");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        shiftLeft.invoke(c, 32);
        assertEquals(1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567889");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF12345");
        shiftLeft.invoke(c, 32);
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF1234500000000");
        assertEquals(0, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12344");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF1234500000000");
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12346");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF1234500000000");
        assertEquals(1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567891");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF1234500000000");
        assertEquals(1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567889");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 32);
        assignHexString.invoke(c, "1234567890ABCDEF1234500000000");
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        assignHexString.invoke(c, "123456789000000000ABCDEF12345");
        assertEquals(0, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12346");
        assignHexString.invoke(c, "123456789000000000ABCDEF12345");
        assertEquals(1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12344");
        assignHexString.invoke(c, "123456789000000000ABCDEF12345");
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 16);
        assignHexString.invoke(c, "12345678900000ABCDEF123450000");
        assertEquals(0, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12344");
        shiftLeft.invoke(b, 16);
        assignHexString.invoke(c, "12345678900000ABCDEF123450000");
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12345");
        shiftLeft.invoke(b, 16);
        assignHexString.invoke(c, "12345678900000ABCDEF123450001");
        assertEquals(-1, plusCompare.invoke(null, a, b, c));

        assignHexString.invoke(a, "1234567890");
        shiftLeft.invoke(a, 11 * 4 + 32);
        assignHexString.invoke(b, "ABCDEF12346");
        shiftLeft.invoke(b, 16);
        assignHexString.invoke(c, "12345678900000ABCDEF123450000");
        assertEquals(+1, plusCompare.invoke(null, a, b, c));
    }


    @Test
    public void testSquare() throws Exception {

        final Object bignum = ctor.newInstance();

        final Method assignUInt16 = method("assignUInt16", char.class);
        final Method assignHexString = method("assignHexString", String.class);
        final Method square = method("square");
        final Method toHexString = method("toHexString");

        assignUInt16.invoke(bignum, (char) 1);
        square.invoke(bignum);
        assertEquals(toHexString.invoke(bignum), "1");

        assignUInt16.invoke(bignum, (char) 2);
        square.invoke(bignum);
        assertEquals(toHexString.invoke(bignum), "4");

        assignUInt16.invoke(bignum, (char) 10);
        square.invoke(bignum);
        assertEquals(toHexString.invoke(bignum), "64");

        assignHexString.invoke(bignum, "FFFFFFF");
        square.invoke(bignum);
        assertEquals(toHexString.invoke(bignum), "FFFFFFE0000001");

        assignHexString.invoke(bignum, "FFFFFFFFFFFFFF");
        square.invoke(bignum);
        assertEquals(toHexString.invoke(bignum), "FFFFFFFFFFFFFE00000000000001");
    }


    @Test
    public void testAssignPowerUInt16() throws Exception {

        final Object bignum = ctor.newInstance();

        final Method assignPowerUInt16 = method("assignPowerUInt16", int.class, int.class);
        final Method toHexString = method("toHexString");

        assignPowerUInt16.invoke(bignum, 1, 0);
        assertEquals(toHexString.invoke(bignum), "1");

        assignPowerUInt16.invoke(bignum, 1, 1);
        assertEquals(toHexString.invoke(bignum), "1");

        assignPowerUInt16.invoke(bignum, 1, 2);
        assertEquals(toHexString.invoke(bignum), "1");

        assignPowerUInt16.invoke(bignum, 2, 0);
        assertEquals(toHexString.invoke(bignum), "1");

        assignPowerUInt16.invoke(bignum, 2, 1);
        assertEquals(toHexString.invoke(bignum), "2");

        assignPowerUInt16.invoke(bignum, 2, 2);
        assertEquals(toHexString.invoke(bignum), "4");

        assignPowerUInt16.invoke(bignum, 16, 1);
        assertEquals(toHexString.invoke(bignum), "10");

        assignPowerUInt16.invoke(bignum, 16, 2);
        assertEquals(toHexString.invoke(bignum), "100");

        assignPowerUInt16.invoke(bignum, 16, 5);
        assertEquals(toHexString.invoke(bignum), "100000");

        assignPowerUInt16.invoke(bignum, 16, 8);
        assertEquals(toHexString.invoke(bignum), "100000000");

        assignPowerUInt16.invoke(bignum, 16, 16);
        assertEquals(toHexString.invoke(bignum), "10000000000000000");

        assignPowerUInt16.invoke(bignum, 16, 30);
        assertEquals(toHexString.invoke(bignum), "1000000000000000000000000000000");

        assignPowerUInt16.invoke(bignum, 10, 0);
        assertEquals(toHexString.invoke(bignum), "1");

        assignPowerUInt16.invoke(bignum, 10, 1);
        assertEquals(toHexString.invoke(bignum), "A");

        assignPowerUInt16.invoke(bignum, 10, 2);
        assertEquals(toHexString.invoke(bignum), "64");

        assignPowerUInt16.invoke(bignum, 10, 5);
        assertEquals(toHexString.invoke(bignum), "186A0");

        assignPowerUInt16.invoke(bignum, 10, 8);
        assertEquals(toHexString.invoke(bignum), "5F5E100");

        assignPowerUInt16.invoke(bignum, 10, 16);
        assertEquals(toHexString.invoke(bignum), "2386F26FC10000");

        assignPowerUInt16.invoke(bignum, 10, 30);
        assertEquals(toHexString.invoke(bignum), "C9F2C9CD04674EDEA40000000");

        assignPowerUInt16.invoke(bignum, 10, 31);
        assertEquals(toHexString.invoke(bignum), "7E37BE2022C0914B2680000000");

        assignPowerUInt16.invoke(bignum, 2, 0);
        assertEquals(toHexString.invoke(bignum), "1");

        assignPowerUInt16.invoke(bignum, 2, 100);
        assertEquals(toHexString.invoke(bignum), "10000000000000000000000000");

        assignPowerUInt16.invoke(bignum, 17, 0);
        assertEquals(toHexString.invoke(bignum), "1");

        assignPowerUInt16.invoke(bignum, 17, 99);
        assertEquals(toHexString.invoke(bignum),
                "1942BB9853FAD924A3D4DD92B89B940E0207BEF05DB9C26BC1B757" +
                "80BE0C5A2C2990E02A681224F34ED68558CE4C6E33760931");

        assignPowerUInt16.invoke(bignum, 0xFFFF, 99);
        assertEquals(toHexString.invoke(bignum),
                "FF9D12F09B886C54E77E7439C7D2DED2D34F669654C0C2B6B8C288250" +
                "5A2211D0E3DC9A61831349EAE674B11D56E3049D7BD79DAAD6C9FA2BA" +
                "528E3A794299F2EE9146A324DAFE3E88967A0358233B543E233E575B9" +
                "DD4E3AA7942146426C328FF55BFD5C45E0901B1629260AF9AE2F310C5" +
                "50959FAF305C30116D537D80CF6EBDBC15C5694062AF1AC3D956D0A41" +
                "B7E1B79FF11E21D83387A1CE1F5882B31E4B5D8DE415BDBE6854466DF" +
                "343362267A7E8833119D31D02E18DB5B0E8F6A64B0ED0D0062FFFF");
    }
}
