/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package compiler.conversions;

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8254317
 * @requires vm.compiler2.enabled
 * @summary Exercises the optimization that moves integer-to-long conversions
 *          upwards through a DAG of integer additions.
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:CompileOnly=compiler.conversions.TestMoveConvI2LThroughAddIDAG::main
 *      compiler.conversions.TestMoveConvI2LThroughAddIDAG
 */

public class TestMoveConvI2LThroughAddIDAG {
    static boolean val = true;
    public static void main(String[] args) {
        // This should make C2 infer that a0-3 are in the value range [2,10],
        // enabling the optimization in ConvI2LNode::Ideal() for LP64 platforms.
        int a0 = val ? 2 : 10;
        int a1 = val ? 2 : 10;
        int a2 = val ? 2 : 10;
        int a3 = val ? 2 : 10;
        int b0 = a0 + a1;
        int b1 = a1 + a2;
        int b2 = a2 + a3;
        int c0 = b0 + b1;
        int c1 = b1 + b2;
        int d = c0 + c1;
        long out = d;
        Asserts.assertEQ(out, 16L);
    }
}
