/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import java.lang.reflect.Array;

import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the implementation of Array.createInstance.
 */
public class DynamicNewArrayTest extends GraalCompilerTest {

    private class Element {
    }

    @Test
    public void test1() {
        test("test1snippet");
    }

    @Test
    public void test2() {
        test("test2snippet");
    }

    @Test
    public void test3() {
        test("dynamic", Long.class, 7);
    }

    @Test
    public void test4() {
        test("dynamic", Boolean.class, -7);
        test("dynamicSynchronized", Boolean.class, -7);
    }

    @Test
    public void test5() {
        test("dynamic", byte.class, 7);
    }

    @Test
    public void test6() {
        test("dynamic", null, 5);
    }

    @Test
    public void test7() {
        test("dynamic", void.class, 5);
    }

    @Test
    public void testStub() {
        ResolvedJavaMethod method = getResolvedJavaMethod("dynamic");
        // this will use the stub call because Element[] is not loaded
        Result actual1 = executeActual(method, null, Element.class, 7);
        // this call will use the fast path
        Result actual2 = executeActual(method, null, Element.class, 7);
        Result expected = executeExpected(method, null, Element.class, 7);
        assertEquals(actual1, expected);
        assertEquals(actual2, expected);
    }

    public static Object test1snippet() {
        return Array.newInstance(Integer.class, 7);
    }

    public static Object test2snippet() {
        return Array.newInstance(char.class, 7);
    }

    public static Object dynamic(Class<?> elementType, int length) {
        return Array.newInstance(elementType, length);
    }

    public static synchronized Object dynamicSynchronized(Class<?> elementType, int length) {
        return Array.newInstance(elementType, length);
    }
}
