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
 *        compiler.jvmci.compilerToVM.DisassembleCodeBlobTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.jvmci.compilerToVM.DisassembleCodeBlobTest
 */

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import sun.hotspot.code.NMethod;

import java.util.List;

public class DisassembleCodeBlobTest {

    public static void main(String[] args) {
        DisassembleCodeBlobTest test
                = new DisassembleCodeBlobTest();
        List<CompileCodeTestCase> testCases
                = CompileCodeTestCase.generate(/* bci = */ -1);
        testCases.addAll(CompileCodeTestCase.generate(/* bci = */ 0));
        testCases.forEach(test::check);
        test.checkNull();
    }

    private void checkNull() {
        String str = CompilerToVMHelper.disassembleCodeBlob(0L);
        Asserts.assertNull(str, "not null string returned for null pointer");
    }

    private void check(CompileCodeTestCase testCase) {
        System.out.println(testCase);
        // to have a clean state
        NMethod nMethod = testCase.deoptimizeAndCompile();
        if (nMethod == null) {
            throw new Error(testCase + " : method is not compiled");
        }
        String str = CompilerToVMHelper.disassembleCodeBlob(nMethod.address);
        if (str != null) {
            Asserts.assertGT(str.length(), 0,
                   testCase +  " : returned string has to be non-zero length");
        }
        String str2 = CompilerToVMHelper.disassembleCodeBlob(nMethod.address);
        Asserts.assertEQ(str, str2,
                testCase + " : 2nd invocation returned different value");
    }
}
