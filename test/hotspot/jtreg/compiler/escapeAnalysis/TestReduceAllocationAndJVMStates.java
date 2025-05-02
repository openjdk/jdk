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
 * @test
 * @bug 8335977
 * @summary Check that Reduce Allocation Merges doesn't crash when there
 *          is an uncommon_trap with a chain of JVMS and, the reduced phi
 *          input(s) are local(s) in an old JVMS but not in a younger JVMS.
 *          I.e., check that we don't lose track of "_is_root" when traversing
 *          a JVMS chain.
 * @run main/othervm -Xbatch
 *                   -XX:CompileOnly=compiler.escapeAnalysis.TestReduceAllocationAndJVMStates::test*
 *                   compiler.escapeAnalysis.TestReduceAllocationAndJVMStates
 * @run main compiler.escapeAnalysis.TestReduceAllocationAndJVMStates
 */
package compiler.escapeAnalysis;

public class TestReduceAllocationAndJVMStates {
    static boolean bFld;
    static int iFld;

    public static void main(String[] args) {
        bFld = false;

        for (int i = 0; i < 10000; i++) {
            test(i % 2 == 0);
        }
        bFld = true;

        // This will trigger a deoptimization which
        // will make the issue manifest to the user
        test(true);
    }

    static int test(boolean flag) {
        Super a = new A();
        Super b = new B();
        Super s = (flag ? a : b);

        // This needs to be inlined by C2
        check();

        return a.i + b.i + s.i;
    }

    // This shouldn't be manually inlined
    static void check() {
        if (bFld) {
            iFld = 34;
        }
    }
}

class Super {
    int i;
}
class A extends Super {}
class B extends Super {}
