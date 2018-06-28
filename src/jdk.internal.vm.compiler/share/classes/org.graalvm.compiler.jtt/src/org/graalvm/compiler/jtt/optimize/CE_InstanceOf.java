/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.optimize;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;

public class CE_InstanceOf extends JTTTest {

    static class A {
    }

    static class B extends A {
    }

    static class C extends A {
    }

    public static A testRedundantCast(Object value) {
        if (value != null && value.getClass() == A.class) {
            return (A) value;
        }
        return null;
    }

    public static boolean testRedundantInstanceOf(Object value) {
        if (value != null && value.getClass() == A.class) {
            return (value instanceof A);
        }
        return false;
    }

    public static boolean testRedundantInstanceOf2(Object value) {
        if (value.getClass() == A.class) {
            return (value instanceof A);
        }
        return false;
    }

    public static boolean testNonRedundantInstanceOf(Object value) {
        if (value instanceof A) {
            return (value != null && value.getClass() == A.class);
        }
        return false;
    }

    public static Object testNonRedundantInstanceOf2(Object value) {
        if (value != null && value.getClass() == Object[].class) {
            return ((Object[]) value)[0];
        }
        return null;
    }

    private static final List<A> testArgs = Collections.unmodifiableList(Arrays.asList(new A(), new B(), null));

    @Test
    public void run0() throws Throwable {
        for (A a : testArgs) {
            runTest("testRedundantCast", a);
        }
    }

    @Test
    public void run1() throws Throwable {
        for (A a : testArgs) {
            runTest("testRedundantInstanceOf", a);
        }
    }

    @Test
    public void run2() throws Throwable {
        for (A a : testArgs) {
            runTest("testRedundantInstanceOf2", a);
        }
    }

    @Test
    public void run3() throws Throwable {
        for (A a : testArgs) {
            runTest("testNonRedundantInstanceOf", a);
        }
    }

    @Test
    public void run4() throws Throwable {
        for (A a : testArgs) {
            runTest("testNonRedundantInstanceOf2", a);
        }
    }
}
