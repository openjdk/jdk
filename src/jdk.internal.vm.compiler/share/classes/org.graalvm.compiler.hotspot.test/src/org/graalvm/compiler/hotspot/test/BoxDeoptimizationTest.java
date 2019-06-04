/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class BoxDeoptimizationTest extends GraalCompilerTest {
    private static boolean isJDK13OrLater = JavaVersionUtil.JAVA_SPECIFICATION_VERSION >= 13;

    public static void testInteger() {
        Object[] values = {42, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Integer.valueOf(42));
    }

    @Test
    public void test1() {
        Assume.assumeTrue(isJDK13OrLater);
        test("testInteger");
    }

    public static void testLong() {
        Object[] values = {42L, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Long.valueOf(42));
    }

    @Test
    public void test2() {
        Assume.assumeTrue(isJDK13OrLater);
        test("testLong");
    }

    public static void testChar() {
        Object[] values = {'a', new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Character.valueOf('a'));
    }

    @Test
    public void test3() {
        Assume.assumeTrue(isJDK13OrLater);
        test("testChar");
    }

    public static void testShort() {
        Object[] values = {(short) 42, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Short.valueOf((short) 42));
    }

    @Test
    public void test4() {
        Assume.assumeTrue(isJDK13OrLater);
        test("testShort");
    }

    public static void testByte() {
        Object[] values = {(byte) 42, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Byte.valueOf((byte) 42));
    }

    @Test
    public void test5() {
        Assume.assumeTrue(isJDK13OrLater);
        test("testByte");
    }

    public static void testBoolean() {
        Object[] values = {true, new Exception()};
        GraalDirectives.deoptimize();
        Assert.assertSame(values[0], Boolean.valueOf(true));
    }

    @Test
    public void test6() {
        Assume.assumeTrue(isJDK13OrLater);
        test("testBoolean");
    }
}
