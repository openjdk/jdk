/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 *         & (vm.compMode != "Xcomp" | vm.opt.TieredCompilation == null | vm.opt.TieredCompilation == true)
 * @summary no "-Xcomp -XX:-TieredCompilation" combination allowed until JDK-8140018 is resolved
 * @library / /test/lib
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.code
 *          jdk.vm.ci/jdk.vm.ci.meta
 *
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xmixed -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:CompileCommand=exclude,*::check
 *                   -XX:+DoEscapeAnalysis -XX:-UseCounterDecay
 *                   -XX:CompileCommand=dontinline,compiler/jvmci/compilerToVM/MaterializeVirtualObjectTest,testFrame
 *                   -XX:CompileCommand=inline,compiler/jvmci/compilerToVM/MaterializeVirtualObjectTest,recurse
 *                   -Xbatch
 *                   -Dcompiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate=false
 *                   compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 * @run main/othervm -Xmixed -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:CompileCommand=exclude,*::check
 *                   -XX:+DoEscapeAnalysis -XX:-UseCounterDecay
 *                   -Xbatch
 *                   -Dcompiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate=true
 *                   compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import compiler.testlibrary.CompilerUtils;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Asserts;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotStackFrameReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.hotspot.WhiteBox;

import java.lang.reflect.Method;

public class MaterializeVirtualObjectTest {
    private static final WhiteBox WB;
    private static final Method METHOD;
    private static final ResolvedJavaMethod RESOLVED_METHOD;
    private static final boolean INVALIDATE;
    private static final int COMPILE_THRESHOLD;

    static {
        WB = WhiteBox.getWhiteBox();
        try {
            METHOD = MaterializeVirtualObjectTest.class.getDeclaredMethod(
                    "testFrame", String.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new Error("Can't get executable for test method", e);
        }
        RESOLVED_METHOD = CTVMUtilities.getResolvedMethod(METHOD);
        INVALIDATE = Boolean.getBoolean(
                "compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate");
        COMPILE_THRESHOLD = WB.getBooleanVMFlag("TieredCompilation")
                ? CompilerWhiteBoxTest.THRESHOLD
                : CompilerWhiteBoxTest.THRESHOLD * 2;
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
        /* need to trigger compilation by multiple method invocations
           in order to have method profile data to be gathered */
        for (int i = 0; i < COMPILE_THRESHOLD; i++) {
            testFrame("someString", i);
        }
        Asserts.assertTrue(WB.isMethodCompiled(METHOD), getName()
                + "Method unexpectedly not compiled");
        Asserts.assertTrue(WB.getMethodCompilationLevel(METHOD) == 4, getName()
                + "Method not compiled at level 4");
        testFrame("someString", COMPILE_THRESHOLD);
    }

    private void testFrame(String str, int iteration) {
        Helper helper = new Helper(str);
        recurse(2, iteration);
        Asserts.assertTrue((helper.string != null) && (this != null)
                           && (helper != null), String.format("%s : some locals are null", getName()));
    }
    private void recurse(int depth, int iteration) {
        if (depth == 0) {
            check(iteration);
        } else {
            Integer s = new Integer(depth);
            recurse(depth - 1, iteration);
            Asserts.assertEQ(s.intValue(), depth, String.format("different values: %s != %s", s.intValue(), depth));
        }
    }

    private void check(int iteration) {
        // Materialize virtual objects on last invocation
        if (iteration == COMPILE_THRESHOLD) {
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
