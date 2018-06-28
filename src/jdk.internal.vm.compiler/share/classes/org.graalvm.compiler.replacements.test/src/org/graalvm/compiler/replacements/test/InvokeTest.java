/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.phases.common.AbstractInliningPhase;

/**
 * Tests the implementation of the snippets for lowering the INVOKE* instructions.
 */
public class InvokeTest extends GraalCompilerTest {

    public InvokeTest() {
        createSuites(getInitialOptions()).getHighTier().findPhase(AbstractInliningPhase.class).remove();
    }

    public interface I {

        String virtualMethod(String s);
    }

    public static class A implements I {

        final String name = "A";

        @Override
        public String virtualMethod(String s) {
            return name + s;
        }
    }

    @SuppressWarnings("static-method")
    private String privateMethod(String s) {
        return s;
    }

    @Test
    public void test1() {
        test("invokestatic", "a string");
        test("invokespecialConstructor", "a string");
        test("invokespecial", this, "a string");
        test("invokevirtual", new A(), "a string");
        test("invokevirtual2", new A(), "a string");
        test("invokeinterface", new A(), "a string");
        Object[] args = {null};
        test("invokestatic", args);
        test("invokespecialConstructor", args);
        test("invokespecial", null, null);
        test("invokevirtual", null, null);
        test("invokevirtual2", null, null);
        test("invokeinterface", null, null);
    }

    public static String invokestatic(String s) {
        return staticMethod(s);
    }

    public static String staticMethod(String s) {
        return s;
    }

    public static String invokespecialConstructor(String s) {
        return new A().virtualMethod(s);
    }

    public static String invokespecial(InvokeTest a, String s) {
        return a.privateMethod(s);
    }

    public static String invokevirtual(A a, String s) {
        return a.virtualMethod(s);
    }

    public static String invokevirtual2(A a, String s) {
        a.virtualMethod(s);
        return a.virtualMethod(s);
    }

    public static String invokeinterface(I i, String s) {
        return i.virtualMethod(s);
    }
}
