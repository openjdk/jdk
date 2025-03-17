/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.shared.TestRunException;
import compiler.lib.ir_framework.driver.TestVMException;

/*
 * @test
 * @summary Test the functionality of allowNotCompilable.
 * @library /test/lib /
 * @run driver ir_framework.tests.TestNotCompilable
 */

public class TestNotCompilable {
    public static void main(String[] args) throws Exception {
        // Run without any flags -> should pass.
        runNormal(TestClassA.class);
        runNormal(TestClassB.class);
        runNormal(TestClassC.class);
        runNormal(TestClassD.class);

        // Forbid compilation -> should throw exception, because "not compilable".
        runWithExcludeExpectFailure(TestClassA.class);
        runWithExcludeExpectFailure(TestClassB.class);
        runOptoNoExecuteExpectFailure(TestClassA.class);
        runOptoNoExecuteExpectFailure(TestClassB.class);

        // Forbid compilation -> annotation allows not compilable -> should pass.
        runWithExcludeExpectSuccess(TestClassC.class);
        runWithExcludeExpectSuccess(TestClassD.class);
        runOptoNoExecuteExpectSuccess(TestClassC.class);
        runOptoNoExecuteExpectSuccess(TestClassD.class);

        // Forbid compilation, but allow methods not to compile -> should pass.
        runWithExcludeAndGlobalAllowNotCompilable(TestClassA.class);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassB.class);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassC.class);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassD.class);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassA.class);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassB.class);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassC.class);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassD.class);
    }

    private static void runNormal(Class c) {
        TestFramework framework = new TestFramework(c);
        framework.start();
    }

    private static void runWithExcludeExpectFailure(Class c) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=exclude,*TestClass*::test");
        try {
            framework.start();
            throw new RuntimeException("should have thrown TestRunException");
        } catch (TestVMException e) {}
    }

    private static void runWithExcludeExpectSuccess(Class c) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=exclude,*TestClass*::test");
        framework.start();
    }

    private static void runOptoNoExecuteExpectFailure(Class c) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=compileonly,*TestClass*::test", "-XX:+OptoNoExecute");
        try {
            framework.start();
            throw new RuntimeException("should have thrown TestRunException");
        } catch (TestVMException e) {}
    }

    private static void runOptoNoExecuteExpectSuccess(Class c) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=compileonly,*TestClass*::test", "-XX:+OptoNoExecute");
        framework.start();
    }

    private static void runWithExcludeAndGlobalAllowNotCompilable(Class c) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=exclude,*TestClass*::test");
        framework.allowNotCompilable();
        framework.start();
    }

    private static void runOptoNoExecuteAndGlobalAllowNotCompilable(Class c) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=compileonly,*TestClass*::test", "-XX:+OptoNoExecute");
        framework.allowNotCompilable();
        framework.start();
    }
}

class TestClassA {
    @Test
    public void test() {}
}

class TestClassB {
    @Test
    @IR(failOn = IRNode.LOAD)
    public void test() {}
}

class TestClassC {
    @Test(allowNotCompilable = true)
    public void test() {}
}

class TestClassD {
    @Test(allowNotCompilable = true)
    @IR(failOn = IRNode.LOAD)
    public void test() {}
}
