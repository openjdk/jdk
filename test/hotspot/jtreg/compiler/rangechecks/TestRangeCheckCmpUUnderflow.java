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

/*
 * @test
 * @bug 8286638
 * @summary Dominator failure because ConvL2I node becomes TOP, kills data-flow, but range-check does not collapse
 *          due to insufficient overflow/underflow handling in CmpUNode::Value.
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.rangechecks.TestRangeCheckCmpUUnderflow::*
 *                   -XX:RepeatCompilation=300
 *                   compiler.rangechecks.TestRangeCheckCmpUUnderflow
*/

package compiler.rangechecks;

public class TestRangeCheckCmpUUnderflow {
    volatile int a;
    int b[];
    float c[];
    void e() {
        int g, f, i;
        for (g = 2;; g++) {
            for (i = g; i < 1; i++) {
                f = a;
                c[i - 1] -= b[i];
            }
        }
    }
    public static void main(String[] args) {
        try {
            TestRangeCheckCmpUUnderflow j = new TestRangeCheckCmpUUnderflow();
            j.e();
        } catch (Exception ex) {}
    }
}
