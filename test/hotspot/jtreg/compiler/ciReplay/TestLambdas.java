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
 * @bug 8275670
 * @library / /test/lib
 * @summary testing of ciReplay with nested BoundMethodHandles
 * @requires vm.flightRecorder != true & vm.compMode != "Xint" & vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.ciReplay.TestLambdas
 */

package compiler.ciReplay;

public class TestLambdas extends CiReplayBase {
    public static void main(String args[]) {
        new TestLambdas().runTest(false, TIERED_DISABLED_VM_OPTION);
    }

    @Override
    public void testAction() {
        positiveTest(TIERED_DISABLED_VM_OPTION);
    }

    @Override
    public String getTestClass() {
        return Test.class.getName();
    }
}

class Test {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(i);
        }
    }

    static void test(int i) {
        concat_lambdas();
    }


    // Create nested BoundMethodHandle using StringConcatHelper
    static boolean concat_lambdas() {
        String source = "0123456789abcdefgABCDEFG";
        String result = "";

        int max = 10;
        for (int cp = 0; cp < max; ++cp) {
            String regex = new String(Character.toChars(cp));
            result =  source.substring(0, 3) + regex
                 + source.substring(3, 9) + regex
                 + source.substring(9, 15) + regex
                 + source.substring(15);
        }
        return result.equals("xyzzy");
    }
}
