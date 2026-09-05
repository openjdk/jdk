/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8370502
 * @summary Do not segfault while adding node to IGVN worklist
 *
 * @run main/othervm -Xbatch ${test.main.class}
 */

package compiler.c2;

public class TestUnlockNodeNullMemprof {
    public static void main(String[] args) {
        int[] a = new int[0]; // test only valid when size is 0.
        for (int i = 0; i < Integer.valueOf(10000); i++) // test only valid with boxed loop limit
            try {
                test(a);
            } catch (ArrayIndexOutOfBoundsException e) {
            }
    }

    static void test(int[] a) {
        for (int i = 0; i < 1;) {
            a[i] = 0;
            synchronized (TestUnlockNodeNullMemprof.class) {
            }
            for (int j = 0; Integer.valueOf(j) < 1;)
                j = 0;
        }
    }
}
