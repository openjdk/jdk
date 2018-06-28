/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Collection of tests for {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase}
 * including those that triggered bugs in this phase.
 */
public class ConditionalEliminationTest5 extends ConditionalEliminationTestBase {

    interface A {
    }

    interface B extends A {
    }

    interface C extends B {

    }

    static final class DistinctA {
    }

    static final class DistinctB {
    }

    public static void reference1Snippet(Object a) {
        if (a instanceof B) {
            sink1 = 1;
        }
        sink2 = 2;
    }

    public static void test1Snippet(Object a) {
        if (a instanceof B) {
            if (a instanceof A) {
                sink1 = 1;
            }
        }
        sink2 = 2;
    }

    @Test
    public void test1() {
        testConditionalElimination("test1Snippet", "reference1Snippet");
    }

    public static void reference2Snippet(A a) {
        if (a instanceof B) {
            sink1 = 1;
        }
        sink2 = 2;
    }

    public static void test2Snippet(A a) {
        if (a instanceof B) {
            B newVal = (B) a;
            if (newVal != null) {
                sink1 = 1;
            }
        }
        sink2 = 2;
    }

    @Test
    public void test2() {
        testConditionalElimination("test2Snippet", "reference2Snippet");
    }

    @SuppressWarnings("unused")
    public static void reference3Snippet(Object a, Object b) {
        if (a instanceof DistinctA) {
            DistinctA proxyA = (DistinctA) a;
            if (b instanceof DistinctB) {
                sink1 = 1;
            }
        }
        sink2 = 2;
    }

    @SuppressWarnings("all")
    public static void test3Snippet(Object a, Object b) {
        if (a instanceof DistinctA) {
            DistinctA proxyA = (DistinctA) a;
            if (b instanceof DistinctB) {
                if (proxyA == b) {
                    sink0 = 42;
                }
                sink1 = 1;
            }
        }
        sink2 = 2;
    }

    @Test
    public void test3() {
        testConditionalElimination("test3Snippet", "reference3Snippet", true, false);
    }

    public static void reference4Snippet(Object a) {
        if (!(a instanceof B)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        sink1 = 1;
    }

    public static void test4Snippet1(Object a) {
        if (!(a instanceof B)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (!(a instanceof A)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        sink1 = 1;
    }

    public static void test4Snippet2(Object a) {
        if (!(a instanceof A)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (!(a instanceof B)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        sink1 = 1;
    }

    @SuppressWarnings({"cast", "unused"})
    public static void test4Snippet3(Object a) {
        Object pi = (A) a;
        if (!(a instanceof B)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        sink1 = 1;
    }

    public static void test4Snippet4(Object a) {
        if (!(a instanceof A)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (!(((A) a) instanceof B)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        sink1 = 1;
    }

    @SuppressWarnings({"cast"})
    public static void test4Snippet5(Object a) {
        Object pi = (A) a;
        if (pi == null) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (!(a instanceof B)) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        sink1 = 1;
    }

    @Test
    public void test4() {
        testConditionalElimination("test4Snippet1", "reference4Snippet");
        testConditionalElimination("test4Snippet2", "reference4Snippet");
        testConditionalElimination("test4Snippet3", "reference4Snippet");
        testConditionalElimination("test4Snippet5", "reference4Snippet");
    }

    @Ignore
    @Test
    public void test5() {
        testConditionalElimination("test4Snippet4", "reference4Snippet", false, true);
    }
}
