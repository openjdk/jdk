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
package org.graalvm.compiler.jtt.loop;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Loop12 extends JTTTest {

    private static int[] source = new int[]{10, 15, 20, 25, 30};

    public static int test(int arg) {
        int i = 0;
        if (source[i] != arg) {
            while (++i <= 5 && source[i] != arg) {
                // nothing
            }
        }
        return i;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 10);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 15);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 30);
    }

}
