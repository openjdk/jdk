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
 * @run main/othervm -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,TestTopCastIIOnUndetectedDeadPath2::test -XX:-TieredCompilation
 *                   -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=256120824 TestTopCastIIOnUndetectedDeadPath2
 * @run main/othervm -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,TestTopCastIIOnUndetectedDeadPath2::test -XX:-TieredCompilation
 *                   -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN TestTopCastIIOnUndetectedDeadPath2
 */

public class TestTopCastIIOnUndetectedDeadPath2 {
    static int array[] = new int[100];

    static int test() {
        int res = 0;
        for (int i = 1; i < 100; ++i) {
            try {
                res = array[i - 1];
                int x = (42 % i);
            } catch (ArithmeticException e) {
            }
        }
        return res;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test();
        }
    }
}
