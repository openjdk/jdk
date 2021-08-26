/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @bug 8273021
 * @summary C2: Improve Add and Xor ideal optimizations
 * @library /test/lib
 * @run main/othervm -XX:-Inline -XX:-TieredCompilation -XX:TieredStopAtLevel=4 -XX:CompileCommand=compileonly,compiler.c2.TestAddXorIdeal::* compiler.c2.TestAddXorIdeal
 */
package compiler.c2;

import jdk.test.lib.Asserts;

public class TestAddXorIdeal {

    public static int test1(int x) {
        return ~x + 1;
    }

    public static int test2(int x) {
        return ~(x - 1);
    }

    public static long test3(long x) {
        return ~x + 1L;
    }

    public static long test4(long x) {
        return ~(x - 1L);
    }

    public static void main(String... args) {
        for (int i = -5_000; i < 5_000; i++) {
            Asserts.assertTrue(test1(i + 5) == -(i + 5));
            Asserts.assertTrue(test2(i - 7) == -(i - 7));
            Asserts.assertTrue(test3(i + 100) == -(i + 100));
            Asserts.assertTrue(test4(i - 1024) == -(i - 1024));
        }
    }
}