/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.code.site
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *
 * @build jdk.internal.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 *        jdk.test.whitebox.WhiteBox jdk.test.whitebox.parser.DiagnosticCommand
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *                                jdk.test.whitebox.parser.DiagnosticCommand
 * @run junit/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.InvalidateInstalledCodeTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CodeInstallerTest;
import compiler.jvmci.common.CTVMUtilities;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotCompiledCode.Comment;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.meta.Assumptions.Assumption;

import java.util.List;
import org.junit.Test;

public class InvalidateInstalledCodeTest extends CodeInstallerTest {

    @Test
    public void testInvalidation() {
        List<CompileCodeTestCase> testCases
                = CompileCodeTestCase.generate(/* bci = */ 0);
        testCases.addAll(CompileCodeTestCase.generate(/* bci = */ -1));
        testCases.forEach(t -> check(t));
        checkNull();
    }

    private void checkNull() {
        Utils.runAndCheckException(
                () -> CompilerToVMHelper.invalidateHotSpotNmethod(null, true),
                NullPointerException.class);
    }

    private void check(CompileCodeTestCase testCase) {
        HotSpotResolvedJavaMethod javaMethod = CTVMUtilities.getResolvedMethod(testCase.executable);
        HotSpotNmethod nmethod = (HotSpotNmethod) installEmptyCode(new Site[0], new Assumption[0],
                new Comment[0], 8, new DataPatch[0], null);

        Asserts.assertTrue(nmethod.isValid(), testCase + " : code is invalid even before invalidation");

        Asserts.assertTrue(nmethod.isValid(), testCase + " : code is not valid, i = " + nmethod);
        Asserts.assertTrue(nmethod.isAlive(), testCase + " : code is not alive, i = " + nmethod);

        // Make nmethod non-entrant but still alive
        CompilerToVMHelper.invalidateHotSpotNmethod(nmethod, false);
        Asserts.assertFalse(nmethod.isValid(), testCase + " : code is valid, i = " + nmethod);
        Asserts.assertTrue(nmethod.isAlive(), testCase + " : code is not alive, i = " + nmethod);

        // Deoptimize the nmethod and cut the link to it from the HotSpotNmethod
        CompilerToVMHelper.invalidateHotSpotNmethod(nmethod, true);
        Asserts.assertFalse(nmethod.isValid(), testCase + " : code is valid, i = " + nmethod);
        Asserts.assertFalse(nmethod.isAlive(), testCase + " : code is alive, i = " + nmethod);
    }
}
