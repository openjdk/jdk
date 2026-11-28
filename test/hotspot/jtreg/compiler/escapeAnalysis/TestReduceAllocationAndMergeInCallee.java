/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8394840
 * @summary Reduce Allocation Merges could drop the _is_root flag of a merge's
 *          scalar replaced inputs when the merge was only used in an inlined
 *          callee, so deopt did not rematerialize all of them.
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */
package compiler.escapeAnalysis;

public class TestReduceAllocationAndMergeInCallee {
    static boolean bFld;
    static int iFld;

    public static void main(String[] args) {
        bFld = false;

        for (int i = 0; i < 20000; i++) {
            boolean flag = i % 2 == 0;
            testLocals(flag);
            testStack(flag);
            testDeeper(flag);
            testLock(flag);
            testFields(flag);
            testFieldsDeadLocals(flag);
        }

        // Make the callee deopt. If the merge lost the _is_root flag of its
        // inputs, deopt only brings back the one the merge selected and leaves
        // the other null: assert on a debug VM, unexpected NPE on a product VM.
        bFld = true;
        testLocals(true);
        testStack(true);
        testDeeper(true);
        testLock(true);
        testFields(true);
        testFieldsDeadLocals(true);
    }

    // a and b are merged inside the inlined use()/sync(), but the caller reads
    // both again afterwards. The variants only change where the caller keeps
    // them across the call.

    static int testLocals(boolean flag) {
        Shape a = new Square();
        Shape b = new Circle();
        return use(flag ? a : b) + a.i + b.i;
    }

    static int testStack(boolean flag) {
        Shape a = new Square();
        Shape b = new Circle();
        return sum(a, b, use(flag ? a : b));
    }

    static int testDeeper(boolean flag) {
        Shape a = new Square();
        Shape b = new Circle();
        return useDeeper(flag ? a : b) + a.i + b.i;
    }

    static int testLock(boolean flag) {
        Shape a = new Square();
        Shape b = new Circle();
        (flag ? a : b).sync();
        return a.i + b.i;
    }

    static int testFields(boolean flag) {
        Picture p = new Picture(new Square(), new Circle());
        return use(flag ? p.a : p.b) + p.a.i + p.b.i;
    }

    // Same as testFields, except a and b are also locals that are dead by the
    // call, so they end up described both as Picture fields and as merge inputs.
    static int testFieldsDeadLocals(boolean flag) {
        Shape a = new Square();
        Shape b = new Circle();
        Picture p = new Picture(a, b);
        return use(flag ? a : b) + p.a.i + p.b.i;
    }

    // Must be inlined into the test methods; the branch on bFld is the trap.
    static int use(Shape s) {
        if (bFld) {
            iFld = 34;
        }
        return s.i;
    }

    static int useDeeper(Shape s) {
        return use(s);
    }

    static int sum(Shape a, Shape b, int i) {
        return a.i + b.i + i;
    }

    static class Shape {
        int i;

        synchronized void sync() {
            if (bFld) {
                iFld = 34;
            }
        }
    }

    static class Square extends Shape { }

    static class Circle extends Shape { }

    static class Picture {
        Shape a, b;

        Picture(Shape a, Shape b) {
            this.a = a;
            this.b = b;
        }
    }
}
