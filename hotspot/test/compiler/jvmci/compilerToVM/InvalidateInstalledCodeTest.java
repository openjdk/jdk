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
 * @ignore 8139700
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

import compiler.jvmci.common.CTVMUtilities;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.hotspot.code.NMethod;

import java.util.List;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

public class InvalidateInstalledCodeTest {
    private static final CodeCacheProvider CACHE_PROVIDER
            = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend()
                    .getCodeCache();

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
        Utils.runAndCheckException(
                () -> CompilerToVMHelper.invalidateInstalledCode(null),
                NullPointerException.class);
    }

    private void check(CompileCodeTestCase testCase) {
        System.out.println(testCase);
        HotSpotResolvedJavaMethod javaMethod
                = CTVMUtilities.getResolvedMethod(testCase.executable);
        HotSpotCompilationRequest compRequest = new HotSpotCompilationRequest(
                javaMethod, testCase.bci, /* jvmciEnv = */ 0L);
        String name = testCase.executable.getName();
        CompilationResult compResult = new CompilationResult(name);
        // to pass sanity check of default -1
        compResult.setTotalFrameSize(0);
        InstalledCode installedCode = CACHE_PROVIDER.installCode(
                compRequest, compResult,
                new InstalledCode(name), /* speculationLog = */ null,
                /* isDefault = */ false);
        Asserts.assertTrue(installedCode.isValid(), testCase
                + " : code is invalid even before invalidation");

        NMethod beforeInvalidation = testCase.toNMethod();
        if (beforeInvalidation != null) {
            throw new Error("TESTBUG : " + testCase + " : nmethod isn't found");
        }
        // run twice to verify how it works if method is already invalidated
        for (int i = 0; i < 2; ++i) {
            CompilerToVMHelper.invalidateInstalledCode(installedCode);
            Asserts.assertFalse(installedCode.isValid(), testCase
                            + " : code is valid after invalidation, i = " + i);
            NMethod afterInvalidation = testCase.toNMethod();
            if (afterInvalidation != null) {
                System.err.println("before: " + beforeInvalidation);
                System.err.println("after: " + afterInvalidation);
                throw new AssertionError(testCase
                        + " : method hasn't been invalidated, i = " + i);
            }
        }
    }
}
