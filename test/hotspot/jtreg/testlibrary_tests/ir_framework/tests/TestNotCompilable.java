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
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;

/*
 * @test
 * @requires vm.compiler2.enabled & vm.flagless & vm.debug == true
 * @summary Test the functionality of allowNotCompilable.
 * @library /test/lib /
 * @run driver ir_framework.tests.TestNotCompilable
 */

public class TestNotCompilable {
    public static void main(String[] args) throws Exception {
        runTests(false);
        runTests(true);
    }

    private static void runTests(boolean noWarmup) {
        // Run without any flags -> should pass.
        runNormal(TestClassA.class, noWarmup);
        runNormal(TestClassB.class, noWarmup);
        runNormal(TestClassC.class, noWarmup);
        runNormal(TestClassD.class, noWarmup);
        runNormal(TestClassE.class, noWarmup);
        runNormal(TestClassF.class, noWarmup);

        // Forbid compilation -> should throw exception, because "not compilable".
        runWithExcludeExpectFailure(TestClassA.class, noWarmup);
        runWithExcludeExpectFailure(TestClassB.class, noWarmup);
        // Note: @Run does not fail, but the @IR detects that there is no compilation, and fails.
        runWithExcludeExpectFailure(TestClassE.class, noWarmup);
        runOptoNoExecuteExpectFailure(TestClassA.class, noWarmup);
        runOptoNoExecuteExpectFailure(TestClassB.class, noWarmup);

        // Forbid compilation -> annotation allows not compilable -> should pass.
        runWithExcludeExpectSuccess(TestClassC.class, noWarmup);
        runWithExcludeExpectSuccess(TestClassD.class, noWarmup);
        runWithExcludeExpectSuccess(TestClassF.class, noWarmup);
        runOptoNoExecuteExpectSuccess(TestClassC.class, noWarmup);
        runOptoNoExecuteExpectSuccess(TestClassD.class, noWarmup);
        // Note: @Run does not fail because of missing compilation. And OptoNoExecute does still
        //       print IR before it bails out, and we can successfully match it.
        runOptoNoExecuteExpectSuccess(TestClassE.class, noWarmup);
        runOptoNoExecuteExpectSuccess(TestClassF.class, noWarmup);

        // Forbid compilation, but allow methods not to compile -> should pass.
        runWithExcludeAndGlobalAllowNotCompilable(TestClassA.class, noWarmup);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassB.class, noWarmup);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassC.class, noWarmup);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassD.class, noWarmup);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassE.class, noWarmup);
        runWithExcludeAndGlobalAllowNotCompilable(TestClassF.class, noWarmup);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassA.class, noWarmup);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassB.class, noWarmup);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassC.class, noWarmup);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassD.class, noWarmup);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassE.class, noWarmup);
        runOptoNoExecuteAndGlobalAllowNotCompilable(TestClassF.class, noWarmup);
    }

    private static void runNormal(Class c, boolean noWarmup) {
        TestFramework framework = new TestFramework(c);
        if (noWarmup) { framework.setDefaultWarmup(0); }
        framework.start();
    }

    private static void runWithExcludeExpectFailure(Class c, boolean noWarmup) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=exclude,*TestClass*::test*");
        if (noWarmup) { framework.setDefaultWarmup(0); }
        try {
            framework.start();
            throw new RuntimeException("should have thrown TestRunException/TestVMException or IRViolationException");
        } catch (TestVMException e) {
            // Happens when we hit the issue during explicit compilation by the Framework.
        } catch (IRViolationException e) {
            // Happens in STANDALONE Run case, where the user is responsible for ensuring
            // compilation. The failure happens during IR matching.
        }
    }

    private static void runWithExcludeExpectSuccess(Class c, boolean noWarmup) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=exclude,*TestClass*::test*");
        if (noWarmup) { framework.setDefaultWarmup(0); }
        framework.start();
    }

    private static void runOptoNoExecuteExpectFailure(Class c, boolean noWarmup) {
        System.out.println("runOptoNoExecuteExpectFailure: " + c + ", noWarmup: " + noWarmup);
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=compileonly,*TestClass*::test*", "-XX:+OptoNoExecute");
        if (noWarmup) { framework.setDefaultWarmup(0); }
        try {
            framework.start();
            throw new RuntimeException("should have thrown TestRunException/TestVMException");
        } catch (TestVMException e) {}
    }

    private static void runOptoNoExecuteExpectSuccess(Class c, boolean noWarmup) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=compileonly,*TestClass*::test*", "-XX:+OptoNoExecute");
        if (noWarmup) { framework.setDefaultWarmup(0); }
        framework.start();
    }

    private static void runWithExcludeAndGlobalAllowNotCompilable(Class c, boolean noWarmup) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=exclude,*TestClass*::test*");
        if (noWarmup) { framework.setDefaultWarmup(0); }
        framework.allowNotCompilable();
        framework.start();
    }

    private static void runOptoNoExecuteAndGlobalAllowNotCompilable(Class c, boolean noWarmup) {
        TestFramework framework = new TestFramework(c);
        framework.addFlags("-XX:CompileCommand=compileonly,*TestClass*::test*", "-XX:+OptoNoExecute");
        if (noWarmup) { framework.setDefaultWarmup(0); }
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

class TestClassE {
    @Run(test = {"test1", "test2"}, mode = RunMode.STANDALONE)
    public void run() {
        for (int i = 0; i < 10_000; i++) {
            test1(i);
            test2(i);
        }
    }

    @Test
    public void test1(int i) {}

    @Test
    @IR(failOn = IRNode.LOAD)
    public void test2(int i) {}
}

class TestClassF {
    @Run(test = {"test1", "test2"}, mode = RunMode.STANDALONE)
    public void run() {
        for (int i = 0; i < 10_000; i++) {
            test1(i);
            test2(i);
        }
    }

    @Test(allowNotCompilable = true)
    public void test1(int i) {}

    @Test(allowNotCompilable = true)
    @IR(failOn = IRNode.LOAD)
    public void test2(int i) {}
}
