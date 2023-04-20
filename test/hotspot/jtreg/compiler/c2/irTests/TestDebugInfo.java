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
 * @summary Verify that debug information in C2 compiled code is correct.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestDebugInfo
 */
public class TestDebugInfo {

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints");
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
    @IR(failOn = {"MemBarRelease.*testFinalFieldInit.*bci:-1"})
    public static void testFinalFieldInit() {
        array[0] = new MyClass(42);
        array[1] = new MyClass(42);
        array[2] = new MyClass(42);
    }

    // Verify that the MemBarReleaseLock emitted at the synchronizedMethod exit
    // does not incorrectly reference the caller method in its debug information.
    @Test
    @IR(failOn = {"MemBarReleaseLock.*testSynchronized.*bci:-1"})
    public static void testSynchronized() {
        try {
            myVal.synchronizedMethod(false);
            myVal.synchronizedMethod(true);
        } catch (Exception e) {
            // Ignore
        }
    }

    static byte b0 = 0;
    static byte b1 = 0;
    static byte b2 = 0;
    static byte b3 = 0;

    @ForceInline
    public static Integer useless3(Integer val) {
        return ++val;
    }

    @ForceInline
    public static Integer useless2(Integer val) {
        return useless3(useless3(useless3(useless3(useless3(useless3(useless3(useless3(val))))))));
    }

    @ForceInline
    public static Integer useless1(Integer val) {
        return useless2(useless2(useless2(useless2(useless2(useless2(useless2(useless2(val))))))));
    }

    @ForceInline
    public static void useful3() {
        b3 = 3;
    }

    @ForceInline
    public static void useful2() {
        useful3();
        b2 = 2;
    }

    @ForceInline
    public static void useful1() {
        useful2();
        b1 = 1;
    }

    // Verify that RenumberLiveNodes preserves the debug information side table.
    @Test
    @IR(counts = {"StoreB.*name=b3.*useful3.*bci:1.*useful2.*bci:0.*useful1.*bci:0.*testRenumberLiveNodes.*bci:9", "= 1"})
    @IR(counts = {"StoreB.*name=b2.*useful2.*bci:4.*useful1.*bci:0.*testRenumberLiveNodes.*bci:9", "= 1"})
    @IR(counts = {"StoreB.*name=b1.*useful1.*bci:4.*testRenumberLiveNodes.*bci:9", "= 1"})
    @IR(counts = {"StoreB.*name=b0.*testRenumberLiveNodes.*bci:13", "= 1"})
    public static void testRenumberLiveNodes() {
        // This generates ~3700 useless nodes to trigger RenumberLiveNodes
        useless1(42);

        // Do something useful
        useful1();
        b0 = 0;
    }
}
