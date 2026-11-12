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

/*
 * @test
 * @bug 8396192
 * @summary Test that Parse Predicates are not used in back to back If elimination
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,${test.main.class}::*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.predicates;

public class TestParsePredicateWithSplitIf {
    static TestParsePredicateWithSplitIf t = new TestParsePredicateWithSplitIf();
    int iFld;
    static short sFld;
    boolean flag;

    void main() {
        for (int i = 0; i < 100000; i++ ) {
            test();
        }
    }

    static void test() {
        Object o = --(new TestParsePredicateWithSplitIf().flag ? t : new TestParsePredicateWithSplitIf()).iFld;
    }

    TestParsePredicateWithSplitIf() {
        flag = sFld <= 9.2239920536085E137;
        for (int i = 0; i < 40;  i++) {
            new Short(sFld);
        }
    }
}
