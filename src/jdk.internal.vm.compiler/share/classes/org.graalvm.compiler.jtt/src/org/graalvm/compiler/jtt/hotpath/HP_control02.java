/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop


package org.graalvm.compiler.jtt.hotpath;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class HP_control02 extends JTTTest {

    public static int test(int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            switch (i) {
                case 30:
                    sum += 30;
                    break;
                case 31:
                    sum += 31;
                    break;
                case 32:
                    sum += 32;
                    break;
                case 33:
                    sum += 33;
                    break;
                case 34:
                    sum += 34;
                    break;
                case 35:
                    sum += 35;
                    break;
                case 36:
                    sum += 36;
                    break;
                case 37:
                    sum += 37;
                    break;
                case 38:
                    sum += 38;
                    break;
                case 39:
                    sum += 39;
                    break;
                case 40:
                    sum += 40;
                    break;
                case 41:
                    sum += 41;
                    break;
                case 42:
                    sum += 42;
                    break;
                default:
                    sum += 1;
                    break;
            }
        }
        return sum;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 60);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 100);
    }

}
