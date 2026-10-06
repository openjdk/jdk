/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.igvn;

/**
 * @test
 * @bug 8374495
 * @summary Transformation (a-b)+(c-d) -> (a+c)-(b+d) in AddNode::IdealIL can apply iteratively and
 *          result in an explosion in node creation. Reusing nodes mitigates that.
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:-TieredCompilation
 *                   -Xbatch
 *                   -XX:+StressLoopPeeling
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */
// This reproduces almost always. But if it is not reliable enough for your taste,
// you can add -XX:RepeatCompilation=10 during investigation. With that, I've never
// seen it fail to fail.

public class AddIdealMakesNodeNumberExplode {
    long lFld;
    byte byFld;

    void main() {
        for (int i = 0; i < 10000; i++) {
            testIndirection();
            test();
        }
    }

    // Variation: Indirectly call test().
    void testIndirection() {
        test();
    }

    void test() {
        int x = byFld;
        int y = 2;
        for (int i = 0; i < 5; i++) {
            for (int j = 1; j < 7; ++j) {
                lFld = y;
                for (int k = 1; k < 2; k++) {
                    lFld -= x;
                }
                y -= (int) lFld;
                switch (i % 2) {
                    case 0:
                        lFld += y;
                }
            }
        }
    }
}
