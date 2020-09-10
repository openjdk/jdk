/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @requires vm.jvmci & vm.compMode == "Xmixed"
 * @requires vm.opt.final.EliminateAllocations == true
 *
 * @comment no "-Xcomp -XX:-TieredCompilation" combination allowed until JDK-8140018 is resolved
 * @requires vm.opt.TieredCompilation == null | vm.opt.TieredCompilation == true
 *
 * @library / /test/lib
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.code.stack
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *
 * @build jdk.internal.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbatch -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:CompileCommand=exclude,compiler.jvmci.compilerToVM.GetStackFramesPrimitiveTypesTest::checkStructure
 *                   -XX:CompileCommand=dontinline,compiler.jvmci.compilerToVM.GetStackFramesPrimitiveTypesTest::testFrame1
 *                   -XX:CompileCommand=dontinline,compiler.jvmci.compilerToVM.GetStackFramesPrimitiveTypesTest::testFrame2
 *                   -XX:+DoEscapeAnalysis -XX:-UseCounterDecay
 *                   compiler.jvmci.compilerToVM.GetStackFramesPrimitiveTypesTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import compiler.testlibrary.CompilerUtils;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Asserts;
import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotStackFrameReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.code.stack.InspectedFrame;
import jtreg.SkippedException;
import sun.hotspot.WhiteBox;

import java.lang.reflect.Method;

public class GetStackFramesPrimitiveTypesTest {
    private static final WhiteBox WB;
    private static final int COMPILE_THRESHOLD;
    private static final Method FRAME1_METHOD;
    private static final Method FRAME2_METHOD;
    private static final ResolvedJavaMethod FRAME1_RESOLVED;
    private static final ResolvedJavaMethod FRAME2_RESOLVED;

    static {
        WB = WhiteBox.getWhiteBox();
        try {
            FRAME1_METHOD = GetStackFramesPrimitiveTypesTest.class.getDeclaredMethod("testFrame1",
                   int.class);
            FRAME2_METHOD = GetStackFramesPrimitiveTypesTest.class.getDeclaredMethod("testFrame2",
                   Helper.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new Error("Can't get executable for test method", e);
        }
        FRAME1_RESOLVED = CTVMUtilities.getResolvedMethod(FRAME1_METHOD);
        FRAME2_RESOLVED = CTVMUtilities.getResolvedMethod(FRAME2_METHOD);
        COMPILE_THRESHOLD = WB.getBooleanVMFlag("TieredCompilation")
                ? CompilerWhiteBoxTest.THRESHOLD
                : CompilerWhiteBoxTest.THRESHOLD * 2;
    }

    public static void main(String[] args) {
        int levels[] = CompilerUtils.getAvailableCompilationLevels();
        // we need compilation level 4 to use EscapeAnalysis
        if (levels.length < 1 || levels[levels.length - 1] != 4) {
            throw new SkippedException("Test needs compilation level 4");
        }

        new GetStackFramesPrimitiveTypesTest().test();
    }

    private void test() {
        Asserts.assertFalse(WB.isMethodCompiled(FRAME1_METHOD), "frame1 method is compiled");
        Asserts.assertFalse(WB.isMethodCompiled(FRAME2_METHOD), "frame2 method is compiled");
        testFrame1(COMPILE_THRESHOLD);

        for (int i = 0; i < COMPILE_THRESHOLD * 2; i++) {
            testFrame1(i);
        }
        Asserts.assertTrue(WB.isMethodCompiled(FRAME1_METHOD), "frame1 method not compiled");
        Asserts.assertTrue(WB.isMethodCompiled(FRAME2_METHOD), "frame2 method not compiled");

        testFrame1(COMPILE_THRESHOLD);
    }

    private void testFrame1(int iteration) {
        Helper helper = new Helper("someString", 42, true, (byte) 1, 'c', (short) 42, 42, 0.42, 42f);
        testFrame2(helper, iteration);
        Asserts.assertTrue((helper.string != null) && (this != null)
                        && (helper != null), "some locals are null");

        Asserts.assertTrue("someString".equals(helper.string), "helper.string has unexpected value");
        Asserts.assertTrue(helper.i == 42, "helper.i has unexpected value");
        Asserts.assertTrue(helper.z == true, "helper.z has unexpected value");
        Asserts.assertTrue(helper.b == (byte) 1, "helper.b has unexpected value");
        Asserts.assertTrue(helper.c == 'c', "helper.c has unexpected value");
        Asserts.assertTrue(helper.s == (short) 42, "helper.s has unexpected value");
        Asserts.assertTrue(helper.j == 42, "helper.j has unexpected value");
        Asserts.assertTrue(helper.d == 0.42, "helper.d has unexpected value");
        Asserts.assertTrue(helper.f == 42f, "helper.f has unexpected value");
    }

    private void testFrame2(Helper outerHelper, int iteration) {
        Helper innerHelper = new Helper("foo", 42, true, (byte) 1, 'c', (short) 42, 42, 0.42, 42f);
        if (iteration == COMPILE_THRESHOLD) {
            checkStructure();
        }
        Asserts.assertTrue((innerHelper.string != null) && (this != null)
                  && (innerHelper != null), "some locals are null");
        Asserts.assertTrue((outerHelper.string != null) && (this != null)
                  && (outerHelper != null), "some locals are null");

        Asserts.assertTrue("someString".equals(outerHelper.string), "helper.string has unexpected value");
        Asserts.assertTrue(outerHelper.i == 42, "helper.i has unexpected value");
        Asserts.assertTrue(outerHelper.z == true, "helper.z has unexpected value");
        Asserts.assertTrue(outerHelper.b == (byte) 1, "helper.b has unexpected value");
        Asserts.assertTrue(outerHelper.c == 'c', "helper.c has unexpected value");
        Asserts.assertTrue(outerHelper.s == (short) 42, "helper.s has unexpected value");
        Asserts.assertTrue(outerHelper.j == 42, "helper.j has unexpected value");
        Asserts.assertTrue(outerHelper.d == 0.42, "helper.d has unexpected value");
        Asserts.assertTrue(outerHelper.f == 42f, "helper.f has unexpected value");

        Asserts.assertTrue("foo".equals(innerHelper.string), "helper.string has unexpected value");
        Asserts.assertTrue(innerHelper.i == 42, "helper.i has unexpected value");
        Asserts.assertTrue(innerHelper.z == true, "helper.z has unexpected value");
        Asserts.assertTrue(innerHelper.b == (byte) 1, "helper.b has unexpected value");
        Asserts.assertTrue(innerHelper.c == 'c', "helper.c has unexpected value");
        Asserts.assertTrue(innerHelper.s == (short) 42, "helper.s has unexpected value");
        Asserts.assertTrue(innerHelper.j == 42, "helper.j has unexpected value");
        Asserts.assertTrue(innerHelper.d == 0.42, "helper.d has unexpected value");
        Asserts.assertTrue(innerHelper.f == 42f, "helper.f has unexpected value");
    }

    private void checkStructure() {
        boolean[] framesSeen = new boolean[2];

        InspectedFrame[] frames = CompilerToVMHelper.getStackFrames(
            new ResolvedJavaMethod[] {FRAME2_RESOLVED},
            null,
            0,
            2, // fetch only 2 frames
            new Thread[] {Thread.currentThread()}
        )[0];

        for (InspectedFrame f : frames) {
            if (!framesSeen[1]) {
                Asserts.assertTrue(f.isMethod(FRAME2_RESOLVED),
                        "Expected testFrame2 first");
                framesSeen[1] = true;
                System.out.println("checking frame: " + f);
                Asserts.assertTrue(f.getLocal(0) != null, "this should not be null");
                Asserts.assertTrue(f.getLocal(1) != null, "outerHelper should not be null");
                Asserts.assertEQ(((Helper) f.getLocal(1)).string, "someString", "outerHelper.string should be foo");
                Asserts.assertEQ(((Helper) f.getLocal(1)).i, 42, "outerHelper.i should be 42");
                Asserts.assertEQ(((Helper) f.getLocal(1)).z, true, "outerHelper.z should be true");
                Asserts.assertEQ(((Helper) f.getLocal(1)).b, (byte) 1, "outerHelper.b should be 1");
                Asserts.assertEQ(((Helper) f.getLocal(1)).c, 'c', "outerHelper.c should be c");
                Asserts.assertEQ(((Helper) f.getLocal(1)).s, (short) 42, "outerHelper.s should be 42");
                Asserts.assertEQ(((Helper) f.getLocal(1)).j, (long) 42, "outerHelper.j should be 42");
                Asserts.assertEQ(((Helper) f.getLocal(1)).d, 0.42, "outerHelper.d should be 0.42");
                Asserts.assertEQ(((Helper) f.getLocal(1)).f, 42f, "outerHelper.f should be 42");

                Asserts.assertTrue(f.getLocal(3) != null, "innerHelper should not be null");
                Asserts.assertEQ(((Helper) f.getLocal(3)).string, "foo", "innerHelper.string should be foo");
                Asserts.assertEQ(((Helper) f.getLocal(3)).i, 42, "innerHelper.i should be 42");
                Asserts.assertEQ(((Helper) f.getLocal(3)).z, true, "innerHelper.z should be true");
                Asserts.assertEQ(((Helper) f.getLocal(3)).b, (byte) 1, "innerHelper.b should be 1");
                Asserts.assertEQ(((Helper) f.getLocal(3)).c, 'c', "innerHelper.c should be c");
                Asserts.assertEQ(((Helper) f.getLocal(3)).s, (short) 42, "innerHelper.s should be 42");
                Asserts.assertEQ(((Helper) f.getLocal(3)).j, (long) 42, "innerHelper.j should be 42");
                Asserts.assertEQ(((Helper) f.getLocal(3)).d, 0.42, "innerHelper.d should be 0.42");
                Asserts.assertEQ(((Helper) f.getLocal(3)).f, 42f, "innerHelper.f should be 42");
            } else {
                Asserts.assertFalse(framesSeen[0], "frame1 can not have been seen");
                Asserts.assertTrue(f.isMethod(FRAME1_RESOLVED),
                        "Expected testFrame1 second");
                framesSeen[0] = true;
                Asserts.assertTrue(f.getLocal(0) != null, "this should not be null");
                Asserts.assertTrue(f.getLocal(2) != null, "helper should not be null");
            }
        }
        Asserts.assertTrue(framesSeen[1], "frame3 should have been seen");
        Asserts.assertTrue(framesSeen[0], "frame2 should have been seen");
    }

    private class Helper {
        public String string;
        public int i;
        public boolean z;
        public byte b;
        public char c;
        public short s;
        public long j;
        public double d;
        public float f;

        public Helper(String string, int i, boolean z, byte b, char c, short s, long j, double d, float f) {
            this.string = string;
            this.i = i;
            this.z = z;
            this.b = b;
            this.c = c;
            this.s = s;
            this.j = j;
            this.d = d;
            this.f = f;
        }
    }
}
