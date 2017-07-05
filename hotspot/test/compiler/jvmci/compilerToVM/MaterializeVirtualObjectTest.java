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
 */

/*
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary /test/lib
 * @ignore 8139703
 * @compile ../common/CompilerToVMHelper.java
 * @build sun.hotspot.WhiteBox MaterializeVirtualObjectTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *     -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -XX:CompileCommand=exclude,*::check -XX:+DoEscapeAnalysis -Xbatch
 *     -Dcompiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate=false
 *     compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *     -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -XX:CompileCommand=exclude,*::check -XX:+DoEscapeAnalysis -Xbatch
 *     -Dcompiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate=true
 *     compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 */

package compiler.jvmci.compilerToVM;

import java.lang.reflect.Method;
import jdk.vm.ci.hotspot.HotSpotStackFrameReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;

import compiler.jvmci.common.CTVMUtilities;
import compiler.testlibrary.CompilerUtils;

import sun.hotspot.WhiteBox;

public class MaterializeVirtualObjectTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Method METHOD;
    private static final ResolvedJavaMethod RESOLVED_METHOD;
    private static final boolean INVALIDATE = Boolean.getBoolean(
            "compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate");

    static {
        try {
            METHOD = MaterializeVirtualObjectTest.class.getDeclaredMethod(
                    "testFrame", String.class, boolean.class);
        } catch (NoSuchMethodException e) {
            throw new Error("Can't get executable for test method", e);
        }
        RESOLVED_METHOD = CTVMUtilities.getResolvedMethod(METHOD);
    }

    public static void main(String[] args) {
        int levels[] = CompilerUtils.getAvailableCompilationLevels();
        // we need compilation level 4 to use EscapeAnalysis
        if (levels.length < 1 || levels[levels.length - 1] != 4) {
            System.out.println("INFO: Test needs compilation level 4 to"
                    + " be available. Skipping.");
        } else {
            new MaterializeVirtualObjectTest().test();
        }
    }

    private static String getName() {
        return "CASE: invalidate=" + INVALIDATE;
    }

    private void test() {
        System.out.println(getName());
        Asserts.assertFalse(WB.isMethodCompiled(METHOD), getName()
                + " : method unexpectedly compiled");
        /* need to call testFrame at least once to be able to compile it, so
           calling with materialize=false, because testFrame is not compiled */
        testFrame("someString", /* materialize= */ false);
        WB.enqueueMethodForCompilation(METHOD, 4);
        Asserts.assertTrue(WB.isMethodCompiled(METHOD), getName()
                + "Method unexpectedly not compiled");
        // calling with materialize=true to materialize compiled testFrame
        testFrame("someString", /* materialize= */ true);
    }

    private void testFrame(String str, boolean materialize) {
        Helper helper = new Helper(str);
        check(materialize);
        Asserts.assertTrue((helper.string != null) && (this != null)
                && (helper != null), getName() + " : some locals are null");
    }

    private void check(boolean materialize) {
        // Materialize virtual objects on last invocation
        if (materialize) {
            HotSpotStackFrameReference hsFrame = CompilerToVMHelper
                    .getNextStackFrame(/* topmost frame */ null,
                            new ResolvedJavaMethod[]{
                                RESOLVED_METHOD}, /* don't skip any */ 0);
            Asserts.assertNotNull(hsFrame, getName() + " : got null frame");
            Asserts.assertTrue(WB.isMethodCompiled(METHOD), getName()
                    + "Test method should be compiled");
            Asserts.assertTrue(hsFrame.hasVirtualObjects(), getName()
                    + ": has no virtual object before materialization");
            CompilerToVMHelper.materializeVirtualObjects(hsFrame, INVALIDATE);
            Asserts.assertFalse(hsFrame.hasVirtualObjects(), getName()
                    + " : has virtual object after materialization");
            Asserts.assertEQ(WB.isMethodCompiled(METHOD), !INVALIDATE, getName()
                    + " : unexpected compiled status");
        }
    }

    private class Helper {
        public String string;

        public Helper(String s) {
            this.string = s;
        }
    }
}
