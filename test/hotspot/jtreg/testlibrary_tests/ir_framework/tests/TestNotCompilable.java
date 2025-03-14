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
        TestFramework framework1 = new TestFramework(A.class);
        framework1.start();

        // Forbid compilation -> should throw exception, because "not compilable".
        TestFramework framework2 = new TestFramework(A.class);
        framework2.addFlags("-XX:CompileCommand=exclude,*A::test*");
        try {
            framework2.start();
            throw new RuntimeException("should have thrown TestRunException");
        } catch (TestVMException e) {}

        // Forbid compilation, but allow methods not to compile -> should pass.
        TestFramework framework3 = new TestFramework(A.class);
        framework3.addFlags("-XX:CompileCommand=exclude,*A::test*");
        framework3.allowNotCompilable();
        framework3.start();

        // Run without any flags -> should pass.
        TestFramework framework4 = new TestFramework(B.class);
        framework4.start();

        // Forbid compilation -> annotation allows not compilable -> should pass.
        TestFramework framework5 = new TestFramework(B.class);
        framework5.addFlags("-XX:CompileCommand=exclude,*B::test*");
        framework5.start();

        // Forbid compilation, but allow methods not to compile -> should pass.
        TestFramework framework6 = new TestFramework(B.class);
        framework6.addFlags("-XX:CompileCommand=exclude,*B::test*");
        framework6.allowNotCompilable();
        framework6.start();
    }
}

class A {
    @Test
    public void test1() {}

    @Test
    //TODO: @IR(failOn = IRNode.LOAD)
    public void test2() {}
}

class B {
    @Test(allowNotCompilable = true)
    public void test1() {}

    @Test(allowNotCompilable = true)
    @IR(failOn = IRNode.LOAD)
    public void test2() {}
}
