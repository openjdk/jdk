/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.micro;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class FloatingReads extends JTTTest {

    public static long init = Runtime.getRuntime().totalMemory();
    private final int f = 10;
    private int a;
    private int b;
    private int c;

    public int test(int d) {
        a = 3;
        b = 5;
        c = 7;
        for (int i = 0; i < d; i++) {
            if (i % 2 == 0) {
                a += b;
            }
            if (i % 4 == 0) {
                b += c;
            } else if (i % 3 == 0) {
                b -= a;
            }
            if (i % 5 == 0) {
                for (int j = 0; j < i; j++) {
                    c += a;
                }
                a -= f;
            }
            b = a ^ c;
            if (i % 6 == 0) {
                c--;
            } else if (i % 7 == 0) {
                Runtime.getRuntime().totalMemory();
            }
        }
        return a + b + c;
    }

    @Test
    public void run0() {
        runTest("test", 10);
    }

    @Test
    public void run1() {
        runTest("test", 1000);
    }

    @Test
    public void run2() {
        runTest("test", 1);
    }

    @Test
    public void run3() {
        runTest("test", 0);
    }
}
