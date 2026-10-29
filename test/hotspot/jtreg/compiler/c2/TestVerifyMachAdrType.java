/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;

import java.util.ArrayList;
import java.util.Arrays;

/*
 * @test
 * bug 8396024
 * @summary A node whose memory Proj is unused should still be considered a memory producer by
 *          MachNode::out_adr_type_impl.
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xcomp -XX:-TieredCompilation -XX:CompileOnly=${test.main.class}$Test::*
 *                   ${test.main.class}
 */
public class TestVerifyMachAdrType {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws NoSuchMethodException {
        var testMethod = Test.class.getDeclaredMethod("test");
        var runner = new Thread(Test::test);
        runner.setDaemon(true);
        runner.start();
        while (WHITE_BOX.getMethodCompilationLevel(testMethod, true) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            Thread.yield();
        }
    }

    private static class Test {
        ArrayList<Object> unused = new ArrayList<>(Arrays.asList());
        int[] empty = {};

        public static void test() {
            int i = 0;
            do {
                new Test();
                long l = 0;
                do {
                    l++;
                } while (l < 32);
                float f = 0.0F;
                new Test().fun1(1.0000880971554 <= -f ? 2097152 : -67108864);
            } while (i < 60);
        }

        void fun0(Boolean arg_0) {}

        void fun1(Integer arg_0) {
            new Test();
            fun0(true);
        }
    }
}
