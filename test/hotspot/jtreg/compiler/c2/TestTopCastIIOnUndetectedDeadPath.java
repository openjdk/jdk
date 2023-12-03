/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

/*
 * @test
 * @bug 8319372
 * @summary CastII because of condition guarding it becomes top
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:CompileOnly=TestTopCastIIOnUndetectedDeadPath::test -XX:CompileCommand=quiet -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:StressSeed=426264791 -XX:+StressIGVN TestTopCastIIOnUndetectedDeadPath
 * @run main/othervm -Xcomp -XX:CompileOnly=TestTopCastIIOnUndetectedDeadPath::test -XX:CompileCommand=quiet -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN TestTopCastIIOnUndetectedDeadPath
 */

public class TestTopCastIIOnUndetectedDeadPath {
    static class X {
        static void m(int[] a) {

        }
    }

    static int array[] = new int[10];

    static void test(int val) {
        for (int i = 1; i < 10; ++i) {
            for (int j = i; j < 10; ++j) {
                if (i == 0 && j != 0) {
                    X.m(array);
                }
                array[j - 1] = val;
            }
        }
    }

    public static void main(String[] arg) {
        test(42);
    }
}
