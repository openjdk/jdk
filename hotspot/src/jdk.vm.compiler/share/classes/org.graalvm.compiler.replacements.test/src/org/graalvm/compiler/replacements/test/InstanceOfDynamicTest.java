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
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;

/**
 * Tests for {@link InstanceOfDynamicNode}.
 */
public class InstanceOfDynamicTest extends GraalCompilerTest {

    public static int id(int value) {
        return value;
    }

    @Test
    public void test100() {
        final Object nul = null;
        test("isStringDynamic", nul);
        test("isStringDynamic", "object");
        test("isStringDynamic", Object.class);
    }

    @Test
    public void test101() {
        final Object nul = null;
        test("isStringIntDynamic", nul);
        test("isStringIntDynamic", "object");
        test("isStringIntDynamic", Object.class);
    }

    @Test
    public void test103() {
        test("isInstanceDynamic", String.class, null);
        test("isInstanceDynamic", String.class, "object");
        test("isInstanceDynamic", String.class, Object.class);
        test("isInstanceDynamic", int.class, null);
        test("isInstanceDynamic", int.class, "Object");
        test("isInstanceDynamic", int.class, Object.class);
    }

    @Test
    public void test104() {
        test("isInstanceIntDynamic", String.class, null);
        test("isInstanceIntDynamic", String.class, "object");
        test("isInstanceIntDynamic", String.class, Object.class);
        test("isInstanceIntDynamic", int.class, null);
        test("isInstanceIntDynamic", int.class, "Object");
        test("isInstanceIntDynamic", int.class, Object.class);
    }

    public static boolean isStringDynamic(Object o) {
        return String.class.isInstance(o);
    }

    public static int isStringIntDynamic(Object o) {
        if (String.class.isInstance(o)) {
            return o.toString().length();
        }
        return o.getClass().getName().length();
    }

    public static boolean isInstanceDynamic(Class<?> c, Object o) {
        return c.isInstance(o);
    }

    public static int isInstanceIntDynamic(Class<?> c, Object o) {
        if (c.isInstance(o)) {
            return o.toString().length();
        }
        return o.getClass().getName().length();
    }
}
