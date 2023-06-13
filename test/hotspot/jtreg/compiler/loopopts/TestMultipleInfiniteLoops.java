/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8292660
 * @summary Test that blocks made unreachable after processing multiple infinite
 *          loops in the block ordering phase are removed correctly.
 *
 * @run main/othervm -Xcomp -XX:CompileOnly=compiler.loopopts.TestMultipleInfiniteLoops::test
 *                   compiler.loopopts.TestMultipleInfiniteLoops
 */

package compiler.loopopts;

public class TestMultipleInfiniteLoops {

    static int foo;

    static void test() {
        int i = 5, j;
        while (i > 0) {
            for (j = i; 1 > j; ) {
                switch (i) {
                case 4:
                    foo = j;
                }
            }
            i++;
        }
    }

    public static void main(String[] args) {
        test();
    }
}
