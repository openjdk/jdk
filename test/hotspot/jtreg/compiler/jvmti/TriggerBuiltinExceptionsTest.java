/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8269574
 * @summary Verifies that exceptions are reported correctly to JVMTI in the compiled code
 * @requires vm.jvmti
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm/native
 *                   -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-BackgroundCompilation
 *                   -XX:-TieredCompilation
 *                   -agentlib:TriggerBuiltinExceptions
 *                   compiler.jvmti.TriggerBuiltinExceptionsTest
 */

package compiler.jvmti;

import compiler.testlibrary.CompilerUtils;

import java.lang.reflect.Method;
import static java.lang.Integer.valueOf;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import sun.hotspot.WhiteBox;


public class TriggerBuiltinExceptionsTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int ITERATIONS = 30;           //Arbitrary value, feel free to change
    private static final long COMP_TIMEOUT = 2000L;     //Arbitrary value, feel free to change (millis)
    private static final int COMP_LEVEL = CompilerUtils.getMaxCompilationLevel();

    private static int caughtByJavaTest = 0;
    private static native int caughtByJVMTIAgent();

    public static void methodToCompile(int i, Object src[], Object[] dest) {
        try {
            int idxFromSrc = (int)(src[i]);                   // NPEs, CastExceptions;
            int rangeAdjust = 3 / (idxFromSrc % 3);           // Each 3rd is division by 0
            Object value = src[i - rangeAdjust];              // Array indexing is broken
            dest[i] = (Long)value;                            // ArrayStoreException or CastException
        } catch (Exception e) {
            caughtByJavaTest += 1;
        }
    }

    /**
     * Makes sure that method is compiled.
     */
    private static void compileMethodOrThrow(Method method) {
        boolean enqueued = WB.enqueueMethodForCompilation(method, COMP_LEVEL);
        if (!enqueued) {
            throw new Error(String.format("%s can't be enqueued for compilation on level %d",
                        method, COMP_LEVEL));
        }
        Asserts.assertTrue(
                Utils.waitForCondition(() -> WB.isMethodCompiled(method), COMP_TIMEOUT),
                String.format("Method hasn't been compiled in %d millis", COMP_TIMEOUT));
    }

    /**
     * 1. Compiles method with no profiling information;
     * 2. Causes deoptimization using arguments that causes hot throws;
     * 3. Compiles the method again;
     * 4. Checks that exceptions within compiled code are registered by JVMTI agent.
     */
    public static void main(String[] args) throws Throwable {
        // Preparing the method
        final Method method = TriggerBuiltinExceptionsTest.class.getMethod(
                "methodToCompile", int.class, Object[].class, Object[].class);
        WB.deoptimizeMethod(method);
        TriggerBuiltinExceptionsTest.compileMethodOrThrow(method);

        // Preparing source - badly formed, supposed-to-be-int array
        final Object[] src = new Object[] {
                valueOf(0), null, null, valueOf(3), null, valueOf(5), "string",
                Long.valueOf(Long.MAX_VALUE), null, valueOf(9), "string"};
        final Object[] dst = new Integer[ITERATIONS];

        // 1. Should cause deoptimization as array is null
        // 2. Make the throw hot to make the next compilation aware of it.
        for (int i = 0; i < ITERATIONS; i++) {
            TriggerBuiltinExceptionsTest.methodToCompile(i, src, dst);
        }
        Asserts.assertTrue(
                Utils.waitForCondition(() -> !WB.isMethodCompiled(method), COMP_TIMEOUT),
                String.format("Method hasn't been deoptimized in %d millis", COMP_TIMEOUT));

        // Compile method with throw being hot
        TriggerBuiltinExceptionsTest.compileMethodOrThrow(method);

        // Gathering exceptions in compiled code
        for (int i = 0; i < ITERATIONS; i++) {
            TriggerBuiltinExceptionsTest.methodToCompile(i, src, dst);
        }

        Asserts.assertEQ(
                TriggerBuiltinExceptionsTest.caughtByJVMTIAgent(), caughtByJavaTest,
                "Number of Exceptions caught by java code and JVMTI agent does not match");
    }

}
