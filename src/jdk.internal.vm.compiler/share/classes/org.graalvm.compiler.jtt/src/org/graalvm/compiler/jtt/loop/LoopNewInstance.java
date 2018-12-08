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


package org.graalvm.compiler.jtt.loop;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class LoopNewInstance extends JTTTest {

    public static Blop initBlop = new Blop();

    @SuppressWarnings("unused")
    public static int test(int arg) {
        for (int i = 0; i < arg; i++) {
            new Blop();
        }
        return count;
    }

    private static int count;

    private static class Blop {

        private boolean exists;

        Blop() {
            if (!exists) {
                count++;
            }
            exists = true;
        }
    }

    @Override
    protected void before(ResolvedJavaMethod m) {
        count = 0;
    }

    @Test
    public void run0() throws Throwable {
        count = 0;
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        count = 0;
        runTest("test", 5);
    }

}
