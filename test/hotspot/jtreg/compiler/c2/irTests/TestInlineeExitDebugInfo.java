/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8201516
 * @summary Verify that debug info at inlined method exits refers to inlinee.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestInlineeExitDebugInfo
 */
public class TestInlineeExitDebugInfo {

    public static void main(String[] args) {
        TestFramework.run();
    }

    static class MyClass {
        final int val;

        @ForceInline
        public MyClass(int val) {
            this.val = val;
        }

        @ForceInline
        synchronized void synchronizedMethod(boolean throwIt) {
            if (throwIt) {
                throw new RuntimeException(); // Make sure there is an exception state
            }
        }
    }

    static Object[] array = new Object[3];
    static MyClass myVal = new MyClass(42);

    // Verify that the MemBarRelease emitted at the MyClass constructor exit
    // does not incorrectly reference the caller method in its debug information.
    @Test
    @IR(failOn = { "MemBarRelease.*TestInlineeExitDebugInfo::test.*bci:-1" }, phase = CompilePhase.BEFORE_MATCHING)
    public static void test1() {
        array[0] = new MyClass(42);
        array[1] = new MyClass(42);
        array[2] = new MyClass(42);
    }

    // Verify that the MemBarReleaseLock emitted at the synchronizedMethod exit
    // does not incorrectly reference the caller method in its debug information.
    @Test
    @IR(failOn = { "MemBarReleaseLock.*TestInlineeExitDebugInfo::test.*bci:-1" }, phase = CompilePhase.BEFORE_MATCHING)
    public static void test2() {
        try {
            myVal.synchronizedMethod(false);
            myVal.synchronizedMethod(true);
        } catch (Exception e) {
            // Ignore
        }
    }
}
