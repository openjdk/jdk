/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.tutorial;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Examples for the Graal tutorial. Run them using the unittest harness of the mx script. To look at
 * the examples in IGV (the graph visualization tool), use the {@code -Dgraal.Dump} and
 * {@code -Dgraal.MethodFilter} options. For example, run the first test case using
 *
 * <pre>
 * mx unittest -Dgraal.Dump= -Dgraal.MethodFilter=String.hashCode GraalTutorial#testStringHashCode
 * </pre>
 */
public class GraalTutorial extends InvokeGraal {

    /*
     * Example for the Graal API: access the Graal API metadata object for a method.
     */

    @Test
    public void testPrintBytecodes() {
        ResolvedJavaMethod method = findMethod(String.class, "hashCode");
        Bytecode bytecode = new ResolvedJavaMethodBytecode(method);

        byte[] bytecodes = bytecode.getCode();
        Assert.assertNotNull(bytecodes);

        System.out.println(new BytecodeDisassembler().disassemble(bytecode));
    }

    /*
     * A simple Graal compilation example: Compile the method String.hashCode()
     */

    @Test
    public void testStringHashCode() throws InvalidInstalledCodeException {
        int expectedResult = "Hello World".hashCode();

        InstalledCode installedCode = compileAndInstallMethod(findMethod(String.class, "hashCode"));

        int result = (int) installedCode.executeVarargs("Hello World");
        Assert.assertEquals(expectedResult, result);
    }

    /*
     * Tutorial example for speculative optimizations.
     */

    int f1;
    int f2;

    public void speculativeOptimization(boolean flag) {
        f1 = 41;
        if (flag) {
            f2 = 42;
            return;
        }
        f2 = 43;
    }

    @Test
    public void testSpeculativeOptimization() throws InvalidInstalledCodeException {
        /*
         * Collect profiling information by running the method in the interpreter.
         */

        for (int i = 0; i < 10000; i++) {
            /* Execute several times so that enough profiling information gets collected. */
            speculativeOptimization(false);
        }

        /*
         * Warmup to collect profiling information is done, now we compile the method. Since the
         * value of "flag" was always false during the warmup, the compiled code speculates that the
         * value remains false.
         */

        InstalledCode compiledMethod = compileAndInstallMethod(findMethod(GraalTutorial.class, "speculativeOptimization"));
        f1 = 0;
        f2 = 0;
        compiledMethod.executeVarargs(this, true);
        Assert.assertEquals(41, f1);
        Assert.assertEquals(42, f2);

        /*
         * We executed the compiled method with a "flag" value that triggered deoptimization (since
         * the warmup always used the different "flag" value). The interpreter updated the profiling
         * information, so the second compilation does not perform the speculative optimization.
         */

        compiledMethod = compileAndInstallMethod(findMethod(GraalTutorial.class, "speculativeOptimization"));
        f1 = 0;
        f2 = 0;
        compiledMethod.executeVarargs(this, false);
        Assert.assertEquals(41, f1);
        Assert.assertEquals(43, f2);
    }

    /*
     * Tutorial example for snippets and lowering.
     */

    static class A {
    }

    static class B extends A {
    }

    public static int instanceOfUsage(Object obj) {
        if (obj instanceof A) {
            return 42;
        } else {
            return 0;
        }
    }

    @Test
    public void testInstanceOfUsage() throws InvalidInstalledCodeException {
        /*
         * Collect profiling information by running the method in the interpreter.
         */

        A a = new A();
        /* Allocate an (unused) instance of B so that the class B gets loaded. */
        @SuppressWarnings("unused")
        B b = new B();
        int expectedResult = instanceOfUsage(a);
        for (int i = 0; i < 10000; i++) {
            /* Execute several times so that enough profiling information gets collected. */
            instanceOfUsage(a);
        }

        /* Warmup to collect profiling information is done, now compile the method. */

        InstalledCode compiledMethod = compileAndInstallMethod(findMethod(GraalTutorial.class, "instanceOfUsage"));

        int result = (int) compiledMethod.executeVarargs(a);
        Assert.assertEquals(expectedResult, result);
    }

    /*
     * Tutorial example for intrinsic methods.
     */

    public static double intrinsicUsage(double val) {
        return Math.sin(val);
    }

    @Test
    public void testIntrinsicUsage() throws InvalidInstalledCodeException {
        double expectedResult = intrinsicUsage(42d);

        InstalledCode compiledMethod = compileAndInstallMethod(findMethod(GraalTutorial.class, "intrinsicUsage"));

        double result = (double) compiledMethod.executeVarargs(42d);
        Assert.assertEquals(expectedResult, result, 0);
    }
}
