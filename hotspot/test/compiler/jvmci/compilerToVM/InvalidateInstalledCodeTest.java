/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library /testlibrary /../../test/lib /
 * @compile ../common/CompilerToVMHelper.java
 * @build sun.hotspot.WhiteBox
 *        compiler.jvmci.compilerToVM.InvalidateInstalledCodeTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.jvmci.compilerToVM.InvalidateInstalledCodeTest
 */

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import sun.hotspot.code.NMethod;

import java.util.List;

public class InvalidateInstalledCodeTest {
    public static void main(String[] args) {
        InvalidateInstalledCodeTest test
                = new InvalidateInstalledCodeTest();
        List<CompileCodeTestCase> testCases
                = CompileCodeTestCase.generate(/* bci = */ 0);
        testCases.addAll(CompileCodeTestCase.generate(/* bci = */ -1));
        testCases.forEach(test::check);
        test.checkNull();
    }

    private void checkNull() {
        InstalledCode installedCode = new InstalledCode("<null>");
        installedCode.setAddress(0);
        CompilerToVMHelper.invalidateInstalledCode(installedCode);
    }

    private void check(CompileCodeTestCase testCase) {
        System.out.println(testCase);
        // to have a clean state
        NMethod beforeInvalidation = testCase.deoptimizeAndCompile();
        if (beforeInvalidation == null) {
            throw new Error("method is not compiled, testCase " + testCase);
        }

        // run twice to verify how it works if method is already invalidated
        for (int i = 0; i < 2; ++i) {
            InstalledCode installedCode = new InstalledCode(
                    testCase.executable.getName());
            installedCode.setAddress(beforeInvalidation.address);

            CompilerToVMHelper.invalidateInstalledCode(installedCode);
            NMethod afterInvalidation = testCase.toNMethod();
            if (afterInvalidation != null) {
                System.err.println("before: " + beforeInvalidation);
                System.err.println("after: " + afterInvalidation);
                throw new AssertionError(testCase
                        + " : method hasn't been invalidated, i = " + i);
            }
            Asserts.assertFalse(installedCode.isValid(), testCase
                            + " : code is valid after invalidation, i = " + i);
        }
    }
}
