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

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests calls to the array copy method.
 */
public class ArrayCopyGeneric extends JTTTest {

    public Object[] arraysFrom;
    public Object[] arraysTo;

    public void init() {
        arraysFrom = new Object[]{new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new short[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                        new long[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new float[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}};
        arraysTo = new Object[]{new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new short[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                        new long[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new float[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}};
    }

    public Object test() {
        init();

        for (int i = 0; i < arraysFrom.length; i++) {
            Object from = arraysFrom[i];
            Object to = arraysTo[i];
            System.arraycopy(from, 1, to, 2, 2);
            System.arraycopy(from, 8, to, 7, 2);
        }
        return arraysTo;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }
}
