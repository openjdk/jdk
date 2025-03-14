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
 * @summary Test the functionality of allowMethodNotCompilable.
 * @library /test/lib /
 * @run driver ir_framework.tests.TestNotCompilable
 */

public class TestNotCompilable {
    public static void main(String[] args) throws Exception {
        // Run without any flags -> should pass.
        TestFramework.run();
        // Forbid compilation -> should throw exception, because "not compilable".
        try {
            TestFramework.runWithFlags("-XX:CompileCommand=exclude,*Test*::test*");
            throw new RuntimeException("should have thrown TestRunException");
        } catch (TestVMException e) {
        }
        // Forbid compilation, but allow methods not to compile -> should pass.
        TestFramework framework = new TestFramework(TestNotCompilable.class);
        framework.addFlags("-XX:CompileCommand=exclude,*Test*::test*");
        framework.allowMethodNotCompilable();
        framework.start();
    }

    @Test
    public void test1() {}

    @Test
    @IR(failOn = IRNode.LOAD)
    public void test2() {}
}
