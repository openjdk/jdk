/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class ConditionalElimination01 extends JTTTest {

    private static int x;
    private static Object o = new Object();

    private static class A {

        A(int y) {
            this.y = y;
        }

        int y;
    }

    @Override
    protected void before(ResolvedJavaMethod method) {
        super.before(method);
        x = 0;
    }

    public int test(A a) {
        if (o == null) {
            return -1;
        }
        if (a == null) {
            return -2;
        }
        if (o == null) {
            return -3;
        }
        x = 3;
        return a.y + x;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", new A(5));
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", new Object[]{null});
    }

}
