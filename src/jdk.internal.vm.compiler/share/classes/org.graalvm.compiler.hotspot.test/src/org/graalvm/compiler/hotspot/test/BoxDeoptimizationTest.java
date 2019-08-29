/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.JAVA_SPEC;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class BoxDeoptimizationTest extends GraalCompilerTest {

    private static void checkJDK() {
        Assume.assumeTrue(JAVA_SPEC == 8 || JAVA_SPEC >= 13);
    }

    public static void testIntegerSnippet() {
        Object[] values = {42, -42, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Integer.valueOf(42));
        Assert.assertSame(values[1], Integer.valueOf(-42));
    }

    @Test
    public void testInteger() {
        checkJDK();
        test("testIntegerSnippet");
    }

    public static void testLongSnippet() {
        long highBitsOnly = 2L << 40;
        Object[] values = {42L, -42L, highBitsOnly, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Long.valueOf(42));
        Assert.assertSame(values[1], Long.valueOf(-42));
        Assert.assertNotSame(values[2], highBitsOnly);
    }

    @Test
    public void testLong() {
        checkJDK();
        test("testLongSnippet");
    }

    public static void testCharSnippet() {
        Object[] values = {'a', 'Z', new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Character.valueOf('a'));
        Assert.assertSame(values[1], Character.valueOf('Z'));
    }

    @Test
    public void testChar() {
        checkJDK();
        test("testCharSnippet");
    }

    public static void testShortSnippet() {
        Object[] values = {(short) 42, (short) -42, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Short.valueOf((short) 42));
        Assert.assertSame(values[1], Short.valueOf((short) -42));
    }

    @Test
    public void testShort() {
        checkJDK();
        test("testShortSnippet");
    }

    public static void testByteSnippet() {
        Object[] values = {(byte) 42, (byte) -42, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Byte.valueOf((byte) 42));
        Assert.assertSame(values[1], Byte.valueOf((byte) -42));
    }

    @Test
    public void testByte() {
        checkJDK();
        test("testByteSnippet");
    }

    public static void testBooleanSnippet() {
        Object[] values = {true, false, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Boolean.valueOf(true));
        Assert.assertSame(values[1], Boolean.valueOf(false));
    }

    @Test
    public void testBoolean() {
        checkJDK();
        test("testBooleanSnippet");
    }
}
