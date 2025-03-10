/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8333394
 * @summary Test bailout of range check policy with an If with a Phi as condition.
 * @run main/othervm -XX:CompileCommand=compileonly,*TestIfWithPhiInput*::* -Xcomp -XX:-TieredCompilation
 *                   compiler.predicates.assertion.TestIfWithPhiInput
 */

package compiler.predicates.assertion;

public class TestIfWithPhiInput {
    static int x;
    static int y;

    public static void main(String[] strArr) {
        test();
    }

    static int test() {
        int i = 1;
        do {
            try {
                y = y / y;
            } catch (ArithmeticException a_e) {
            }
            for (int j = i; j < 6; j++) {
                y = i;
            }
        } while (++i < 52);
        return x;
    }
}
