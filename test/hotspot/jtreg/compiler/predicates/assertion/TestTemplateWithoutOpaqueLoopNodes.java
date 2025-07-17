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
 */

/*
 * @test id=Xcomp
 * @bug 8333252
 * @summary Test that no Template Assertion Predicate is created in Loop Prediction for one-iteration loop.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,*TestTemplateWithoutOpaqueLoopNodes::test
 *                   compiler.predicates.assertion.TestTemplateWithoutOpaqueLoopNodes
 */

/*
 * @test id=Xbatch
 * @bug 8333252
 * @summary Test that no Template Assertion Predicate is created in Loop Prediction for one-iteration loop.
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,*TestTemplateWithoutOpaqueLoopNodes::test
 *                   compiler.predicates.assertion.TestTemplateWithoutOpaqueLoopNodes
 */

package compiler.predicates.assertion;

public class TestTemplateWithoutOpaqueLoopNodes {
    static long lFld;
    static long lArr[] = new long[10];

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }

    static void test() {
        int i16 = 1, i17, i19, i20 = 1, i22;
        for (i17 = 6; i17 < 7; i17++) {
            switch ((i16 >> 1) + 38) {
                case 38:
                    for (i19 = 1; i19 < 200000; i19++) {
                    }
                case 1:
                    for (i22 = 1; i22 < 2; i22 += 2) {
                        lArr[i22] = i20;
                    }
                    break;
                case 4:
                    lFld = 42;
            }
        }
    }
}
