/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test LockCompilationTest
 * @bug 8059624 8152169
 * @library /testlibrary /test/lib /
 * @modules java.management
 * @build LockCompilationTest
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -Xmixed -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-UseCounterDecay LockCompilationTest
 * @summary testing of WB::lock/unlockCompilation()
 */

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Asserts;

public class LockCompilationTest extends CompilerWhiteBoxTest {

    public static void main(String[] args) throws Exception {
        // This case waits for 5 seconds and verifies that the method hasn't been
        // compiled during that time. Only do that for one of the test cases.

        // Only compile SimpleTestCaseHelper.method and exclude all other to ensure no
        // contention on the compile queue causes problems.
        String directive =
                "[{ match:\"*SimpleTestCaseHelper.method\", Exclude:false}, " +
                " { match:\"*.*\", Exclude:true}]";
        if (WHITE_BOX.addCompilerDirective(directive) != 2) {
            throw new RuntimeException("Could not add directive");
        }
        try {
            CompilerWhiteBoxTest.main(LockCompilationTest::new, new String[] {"METHOD_TEST"});
        } finally {
            WHITE_BOX.removeCompilerDirective(2);
        }

    }

    private LockCompilationTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    protected void test() throws Exception {
        checkNotCompiled();

        System.out.println("locking compilation");
        WHITE_BOX.lockCompilation();

        try {
            System.out.println("trying to compile");
            compile();
            // to check if it works correctly w/ safepoints
            System.out.println("going to safepoint");
            WHITE_BOX.fullGC();
            // Sleep a while and then make sure the compile is still waiting
            Thread.sleep(5000);

            Asserts.assertTrue(
                    WHITE_BOX.isMethodQueuedForCompilation(method),
                    method + " must be in queue");
            Asserts.assertFalse(
                    WHITE_BOX.isMethodCompiled(method, false),
                    method + " must be not compiled");
            Asserts.assertEQ(
                    WHITE_BOX.getMethodCompilationLevel(method, false), 0,
                    method + " comp_level must be == 0");
            Asserts.assertFalse(
                    WHITE_BOX.isMethodCompiled(method, true),
                    method + " must be not osr_compiled");
            Asserts.assertEQ(
                    WHITE_BOX.getMethodCompilationLevel(method, true), 0,
                    method + " osr_comp_level must be == 0");
        } finally {
            System.out.println("unlocking compilation");
            WHITE_BOX.unlockCompilation();
        }
        waitBackgroundCompilation();
        Asserts.assertFalse(
                WHITE_BOX.isMethodQueuedForCompilation(method),
                method + " must not be in queue");
    }
}

