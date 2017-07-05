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

import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * DiyFp class tests
 *
 * @test
 * @modules jdk.scripting.nashorn/jdk.nashorn.internal.runtime.doubleconv
 * @run testng jdk.nashorn.internal.runtime.doubleconv.test.DiyFpTest
 */
@SuppressWarnings("javadoc")
public class DiyFpTest {

    static final Class<?> DiyFp;
    static final Constructor<?> ctor;

    static {
        try {
            DiyFp = Class.forName("jdk.nashorn.internal.runtime.doubleconv.DiyFp");
            ctor = DiyFp.getDeclaredConstructor(long.class, int.class);
            ctor.setAccessible(true);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method method(final String name, final Class<?>... params) throws NoSuchMethodException {
        final Method m = DiyFp.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void testSubtract() throws Exception {
        final Object diyFp1 = ctor.newInstance(3, 0);
        final Object diyFp2 = ctor.newInstance(1, 0);
        final Object diff = method("minus", DiyFp, DiyFp).invoke(null, diyFp1, diyFp2);;
        assertTrue(2l == (long) method("f").invoke(diff));
        assertTrue(0 == (int) method("e").invoke(diff));
        method("subtract", DiyFp).invoke(diyFp1, diyFp2);
        assertTrue(2l == (long) method("f").invoke(diyFp1));
        assertTrue(0 == (int) method("e").invoke(diyFp2));
    }

    @Test
    public void testMultiply() throws Exception {
        Object diyFp1, diyFp2, product;

        diyFp1 = ctor.newInstance(3, 0);
        diyFp2 = ctor.newInstance(2, 0);
        product = method("times", DiyFp, DiyFp).invoke(null, diyFp1, diyFp2);
        assertEquals(0l, (long) method("f").invoke(product));
        assertEquals(64, (int) method("e").invoke(product));
        method("multiply", DiyFp).invoke(diyFp1, diyFp2);
        assertEquals(0l, (long) method("f").invoke(diyFp1));
        assertEquals(64, (int) method("e").invoke(diyFp1));

        diyFp1 = ctor.newInstance(0x8000000000000000L, 11);
        diyFp2 = ctor.newInstance(2, 13);
        product = method("times", DiyFp, DiyFp).invoke(null, diyFp1, diyFp2);
        assertEquals(1l, (long) method("f").invoke(product));
        assertEquals(11 + 13 + 64, (int) method("e").invoke(product));

        // Test rounding.
        diyFp1 = ctor.newInstance(0x8000000000000001L, 11);
        diyFp2 = ctor.newInstance(1, 13);
        product = method("times", DiyFp, DiyFp).invoke(null, diyFp1, diyFp2);
        assertEquals(1l, (long) method("f").invoke(product));
        assertEquals(11 + 13 + 64, (int) method("e").invoke(product));

        diyFp1 = ctor.newInstance(0x7fffffffffffffffL, 11);
        diyFp2 = ctor.newInstance(1, 13);
        product = method("times", DiyFp, DiyFp).invoke(null, diyFp1, diyFp2);
        assertEquals(0l, (long) method("f").invoke(product));
        assertEquals(11 + 13 + 64, (int) method("e").invoke(product));

        // Big numbers.
        diyFp1 = ctor.newInstance(0xFFFFFFFFFFFFFFFFL, 11);
        diyFp2 = ctor.newInstance(0xFFFFFFFFFFFFFFFFL, 13);
        // 128bit result: 0xfffffffffffffffe0000000000000001
        product = method("times", DiyFp, DiyFp).invoke(null, diyFp1, diyFp2);
        assertEquals(0xFFFFFFFFFFFFFFFel, (long) method("f").invoke(product));
        assertEquals(11 + 13 + 64, (int) method("e").invoke(product));
    }
}
