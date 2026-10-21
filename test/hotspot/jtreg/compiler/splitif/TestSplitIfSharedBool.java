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

/*
 * @test
 * @bug 8376783
 * @summary Split-if must not replace a shared CMove condition with a Phi.
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbatch -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.splitif.TestSplitIfSharedBool
 */

package compiler.splitif;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class TestSplitIfSharedBool {
    static int checksum(int value) {
        int sum = value < 32 ? 1 : 0;
        if (value < 16) sum++;
        if (value > 0) sum++;
        if (value > 16) sum++;
        if (value > 0) sum++;
        if (value > 16) sum++;
        return sum;
    }

    static int test(int value) {
        value = value < 2 ? 2 : value > 4 ? 4 : value;
        return checksum(value + 16);
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 10_000; i++) {
            checksum(1);
        }
        checksum(0);

        for (int i = 0; i < 10_000; i++) {
            test(i & 8);
        }
        test(3);
        for (int i = 0; i < 10_000; i++) {
            test(i & 8);
        }

        Method method = TestSplitIfSharedBool.class.getDeclaredMethod("test", int.class);
        WhiteBox wb = WhiteBox.getWhiteBox();
        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        Asserts.assertEQ(COMP_LEVEL_FULL_OPTIMIZATION, wb.getMethodCompilationLevel(method),
                "The method must finish C2 compilation");
        Asserts.assertEQ(5, test(0));
        Asserts.assertEQ(5, test(3));
        Asserts.assertEQ(5, test(8));
    }
}
