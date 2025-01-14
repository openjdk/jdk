/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136421
 * @requires vm.jvmci
 * @library /test/lib /
 * @library ../common/patches
 * @library /testlibrary/asm
 * @modules java.base/jdk.internal.misc
 * @modules jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *
 * @build jdk.internal.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.jvmci.compilerToVM.DisassembleCodeBlobTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:-BackgroundCompilation
 *                   compiler.jvmci.compilerToVM.DisassembleCodeBlobTest
 */

package compiler.jvmci.compilerToVM;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.whitebox.code.NMethod;

import java.util.List;

public class DisassembleCodeBlobTest {

    public static void main(String[] args) {
        DisassembleCodeBlobTest test
                = new DisassembleCodeBlobTest();
        List<CompileCodeTestCase> testCases
                = CompileCodeTestCase.generate(/* bci = */ -1);
        testCases.forEach(test::check);
        testCases.stream().findAny().ifPresent(test::checkZero);
        test.checkNull();
    }

    private void checkNull() {
        Utils.runAndCheckException(
                () -> { CompilerToVMHelper.disassembleCodeBlob(null); },
                NullPointerException.class);
    }

    private void checkZero(CompileCodeTestCase testCase) {
        System.out.println("checkZero for " + testCase);
        testCase.deoptimize();
        InstalledCode installedCode = testCase.toInstalledCode();
        String str = CompilerToVMHelper.disassembleCodeBlob(installedCode);
        Asserts.assertNull(str, testCase
                + " : non-null return value for invalid installCode");
    }

    private void checkLineStart(CompileCodeTestCase testCase, String line, String match) {
        Asserts.assertTrue(line.startsWith(match),
                testCase + " : line \"" + line + "\" does not start with: \"" + match +"\"");
    }

    private void check(CompileCodeTestCase testCase) {
        System.out.println(testCase);
        // to have a clean state
        NMethod nMethod = testCase.deoptimizeAndCompile();
        if (nMethod == null) {
            throw new Error(testCase + " : method is not compiled");
        }
        InstalledCode installedCode = testCase.toInstalledCode();
        String str1 = CompilerToVMHelper.disassembleCodeBlob(installedCode);
        if (str1 != null) {
            Asserts.assertGT(str1.length(), 0,
                   testCase +  " : returned string has to be non-zero length");
        }
        // The very first call to the disassembler contains a string specifying the
        // architecture: [Disassembling for mach='i386:x86-64']
        // so discard it and try again.
        String str2 = CompilerToVMHelper.disassembleCodeBlob(installedCode);
        String[] strLines = str2.split("\\R");
        // Check some basic layout
        int MIN_LINES = 5;
        Asserts.assertTrue(strLines.length > 2,
            testCase + " : read " + strLines.length + " lines, " + MIN_LINES + " expected");
        int l = 1;
        checkLineStart(testCase, strLines[l++], "Compiled method "); // 2
        checkLineStart(testCase, strLines[l++], " total in heap  "); // 3
        int foundDisassemblyLine = -1;
        int foundEntryPointLine = -1;
        for (; l < strLines.length; ++l) {
            String line = strLines[l];
            if (line.equals("[Disassembly]") || line.equals("[MachCode]")) {
                Asserts.assertTrue(foundDisassemblyLine == -1,
                    testCase + " : Duplicate disassembly section markers found at lines " + foundDisassemblyLine + " and " + l);
                foundDisassemblyLine = l;
            }
            if (line.equals("[Entry Point]") || line.equals("[Verified Entry Point]")) {
                Asserts.assertTrue(foundDisassemblyLine != -1,
                    testCase + " : entry point found but [Disassembly] section missing ");
                foundEntryPointLine = l;
                break;
            }
        }
        Asserts.assertTrue(foundDisassemblyLine != -1, testCase + " : no disassembly section found");
        Asserts.assertTrue(foundEntryPointLine != -1, testCase + " : no entry point found");
    }
}
