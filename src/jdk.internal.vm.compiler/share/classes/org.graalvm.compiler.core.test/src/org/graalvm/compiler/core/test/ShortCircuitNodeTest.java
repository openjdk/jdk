/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import org.graalvm.compiler.core.test.ea.EATestBase.TestClassInt;

public class ShortCircuitNodeTest extends GraalCompilerTest {

    @Test
    public void test1() {
        // only executeActual, to avoid creating profiling information
        executeActual(getResolvedJavaMethod("test1Snippet"), 1, 2);
    }

    public static final TestClassInt field = null;
    public static TestClassInt field2 = null;

    @SuppressWarnings("unused")
    public static void test1Snippet(int a, int b) {
        /*
         * if a ShortCircuitOrNode is created for the check inside test2, then faulty handling of
         * guards can create a cycle in the graph.
         */
        int v;
        if (a == 1) {
            if (b != 1) {
                int i = field.x;
            }
            field2 = null;
            v = 0;
        } else {
            v = 1;
        }

        if (test2(v, b)) {
            int i = field.x;
        }
    }

    public static boolean test2(int a, int b) {
        return a != 0 || b != 1;
    }
}
