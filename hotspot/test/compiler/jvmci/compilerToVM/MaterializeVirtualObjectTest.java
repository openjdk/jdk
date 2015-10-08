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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library / /testlibrary /../../test/lib
 * @compile ../common/CompilerToVMHelper.java
 * @build sun.hotspot.WhiteBox MaterializeVirtualObjectTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *     -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -XX:CompileCommand=exclude,*::check -XX:+DoEscapeAnalysis
 *     compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import java.lang.reflect.Method;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl;
import jdk.vm.ci.hotspot.HotSpotStackFrameReference;

public class MaterializeVirtualObjectTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int INVOCATIONS = 100_000;
    private static final Method METHOD;
    private static final HotSpotResolvedJavaMethodImpl RESOLVED_METHOD;
    private final boolean invalidate;

    static {
        try {
            METHOD = MaterializeVirtualObjectTest.class.getDeclaredMethod(
                    "testFrame", String.class, Integer.class);
        } catch (NoSuchMethodException e) {
            throw new Error("Can't get executable for test method", e);
        }
        RESOLVED_METHOD = CTVMUtilities.getResolvedMethod(METHOD);
    }

    public MaterializeVirtualObjectTest(boolean invalidate) {
        this.invalidate = invalidate;
    }

    public static void main(String[] args) {
        new MaterializeVirtualObjectTest(true).test();
        new MaterializeVirtualObjectTest(false).test();
    }

    private void test() {
        System.out.printf("CASE: invalidate=%b%n", invalidate);
        for (int i = 0; i < INVOCATIONS; i++) {
            testFrame("someString", i);
        }
        Utils.waitForCondition(() -> WB.isMethodCompiled(METHOD), 100L);
        Asserts.assertTrue(WB.isMethodCompiled(METHOD));
        testFrame("someString", INVOCATIONS);
    }

    private void testFrame(String str, Integer iteration) {
        Helper helper = new Helper(str);
        check(iteration);
        Asserts.assertTrue((helper.string != null) && (this != null)
                && (helper != null), "Some locals are null");
    }

    private void check(int iteration) {
        // Materialize virtual objects on last invocation
        if (iteration == INVOCATIONS) {
            HotSpotStackFrameReference hsFrame = CompilerToVMHelper
                    .getNextStackFrame(/* topmost frame */ null,
                            new HotSpotResolvedJavaMethodImpl[]{
                                RESOLVED_METHOD}, /* don't skip any */ 0);
            Asserts.assertNotNull(hsFrame, "Got null frame");
            Asserts.assertTrue(WB.isMethodCompiled(METHOD),
                    "Expected test method to be compiled");
            Asserts.assertTrue(hsFrame.hasVirtualObjects(),
                    "Has no virtual object before materialization");
            CompilerToVMHelper.materializeVirtualObjects(hsFrame, invalidate);
            Asserts.assertFalse(hsFrame.hasVirtualObjects(),
                    "Has virtual object after materialization");
            Asserts.assertEQ(WB.isMethodCompiled(METHOD), !invalidate,
                    "Unexpected compiled status");
        }
    }

    private class Helper {
        public String string;

        public Helper(String s) {
            this.string = s;
        }
    }
}
