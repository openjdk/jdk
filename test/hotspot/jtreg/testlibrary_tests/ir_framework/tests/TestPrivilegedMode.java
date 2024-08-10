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

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;

import jdk.internal.vm.annotation.Stable;
import jdk.test.lib.Asserts;

/*
 * @test
 * @requires vm.flagless
 * @summary Test that IR framework successfully adds test class to boot classpath in order to run in privileged mode.
 * @modules java.base/jdk.internal.vm.annotation
 * @library /test/lib /
 * @run driver ir_framework.tests.TestPrivilegedMode
 */

public class TestPrivilegedMode {
    static @Stable int iFld; // Treated as constant after first being set.

    public static void main(String[] args) {
        try {
            TestFramework.run();
            Asserts.fail("should not reach");
        } catch (IRViolationException e) {
            // Without adding test class to boot classpath, we fail to replace the field load by a constant.
            Asserts.assertTrue(e.getExceptionInfo().contains("Matched forbidden node"));
            Asserts.assertTrue(e.getExceptionInfo().contains("LoadI"));
        }

        // When adding the test class to the boot classpath, we can replace the field load by a constant.
        new TestFramework().addTestClassesToBootClassPath().start();
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = IRNode.LOAD_I)
    public int test() {
        return iFld;
    }

    @Setup
    public void setup() {
        iFld = 34;
    }
}
