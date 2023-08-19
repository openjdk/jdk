/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8314233
 * @requires vm.compiler2.enabled
 * @summary Test that loop peeling does not treat unrelated Opaque4 node as Template Assertion Predicate.
 * @run main/othervm -Xbatch -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestPeelingFindsUnrelatedOpaque4Node::*
 *                   -XX:CompileCommand=inline,*String::* compiler.predicates.TestPeelingFindsUnrelatedOpaque4Node
 */

package compiler.predicates;

public class TestPeelingFindsUnrelatedOpaque4Node {
    static int iFld;
    static boolean flag;

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            test();
            flag = !flag;
        }
    }

    static void test() {
        String s = flag ? "34323653" : "343423";
        s.contains("343");
        // Inlined and will call StringLatin1.indexOf intrinsics which emits Opaque4 node which will be wrongly
        // found as Template Assertion Predicate when trying to initialize them which triggers the assert.
        s.contains("3442");

        for (int i = 0; i < 100; i++) {
            if (flag) { // Triggers peeling
                return;
            }
            iFld = 34;
        }
    }
}
