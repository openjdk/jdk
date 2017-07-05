/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.ea;

import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.code.SourceStackTraceBailoutException;
import org.graalvm.compiler.core.test.GraalCompilerTest;

public class PEAAssertionsTest extends GraalCompilerTest {

    public static Object field;

    public static void snippet1(int i) {
        Integer object = new Integer(i);
        GraalDirectives.ensureVirtualized(object);
    }

    @Test
    public void test1() {
        test("snippet1", 1);
    }

    public static void snippet2(int i) {
        Integer object = new Integer(i);
        GraalDirectives.ensureVirtualized(object);
        field = object; // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void test2() {
        test("snippet2", 1);
    }

    public static void snippet3(int i) {
        Integer object = new Integer(i);
        field = object;
        GraalDirectives.ensureVirtualized(object); // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void test3() {
        test("snippet3", 1);
    }

    public static void snippetHere1(int i) {
        Integer object = new Integer(i);
        GraalDirectives.ensureVirtualizedHere(object);
    }

    @Test
    public void testHere1() {
        test("snippetHere1", 1);
    }

    public static void snippetHere2(int i) {
        Integer object = new Integer(i);
        GraalDirectives.ensureVirtualizedHere(object);
        field = object;
    }

    @Test
    public void testHere2() {
        test("snippetHere2", 1);
    }

    public static void snippetHere3(int i) {
        Integer object = new Integer(i);
        field = object;
        GraalDirectives.ensureVirtualizedHere(object); // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testHere3() {
        test("snippetHere3", 1);
    }

    public static void snippetBoxing1(int i) {
        Integer object = i;
        GraalDirectives.ensureVirtualizedHere(object); // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testBoxing1() {
        test("snippetBoxing1", 1);
    }

    public static void snippetBoxing2(int i) {
        Integer object = i;
        GraalDirectives.ensureVirtualized(object); // assert here
        field = object;
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testBoxing2() {
        test("snippetBoxing2", 1);
    }

    public static void snippetControlFlow1(boolean b, int i) {
        Integer object = new Integer(i);
        if (b) {
            GraalDirectives.ensureVirtualized(object);
        }
        GraalDirectives.controlFlowAnchor();
        field = object;
    }

    @Test
    public void testControlFlow1() {
        test("snippetControlFlow1", true, 1);
    }

    public static void snippetControlFlow2(boolean b, int i) {
        Integer object = new Integer(i);
        if (b) {
            GraalDirectives.ensureVirtualized(object);
        } else {
            GraalDirectives.ensureVirtualized(object);
        }
        GraalDirectives.controlFlowAnchor();
        field = object; // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testControlFlow2() {
        test("snippetControlFlow2", true, 1);
    }

    public static void snippetControlFlow3(boolean b, int i) {
        Integer object = new Integer(i);
        GraalDirectives.ensureVirtualized(object);
        if (b) {
            field = 1;
        } else {
            field = 2;
        }
        GraalDirectives.controlFlowAnchor();
        field = object; // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testControlFlow3() {
        test("snippetControlFlow3", true, 1);
    }

    public static void snippetControlFlow4(boolean b, int i) {
        Integer object = new Integer(i);
        if (b) {
            field = object;
        } else {
            field = 2;
        }
        GraalDirectives.ensureVirtualized(object); // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testControlFlow4() {
        test("snippetControlFlow4", true, 1);
    }

    public static void snippetControlFlow5(boolean b, int i) {
        Integer object = new Integer(i);
        if (b) {
            field = object;
        } else {
            field = 2;
        }
        GraalDirectives.ensureVirtualizedHere(object); // assert here
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testControlFlow5() {
        test("snippetControlFlow5", true, 1);
    }

    public static final class TestClass {
        Object a;
        Object b;
    }

    public static void snippetIndirect1(boolean b, int i) {
        Integer object = new Integer(i);
        TestClass t = new TestClass();
        t.a = object;
        GraalDirectives.ensureVirtualized(object);

        if (b) {
            field = t; // assert here
        } else {
            field = 2;
        }
    }

    @Test(expected = SourceStackTraceBailoutException.class)
    public void testIndirect1() {
        test("snippetIndirect1", true, 1);
    }

    public static void snippetIndirect2(boolean b, int i) {
        Integer object = new Integer(i);
        TestClass t = new TestClass();
        t.a = object;
        GraalDirectives.ensureVirtualized(t);

        if (b) {
            field = object;
        } else {
            field = 2;
        }
    }

    @Test
    public void testIndirect2() {
        test("snippetIndirect2", true, 1);
    }
}
