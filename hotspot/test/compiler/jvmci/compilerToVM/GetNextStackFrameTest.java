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
 * @library / /testlibrary /test/lib
 * @compile ../common/CompilerToVMHelper.java
 * @run main ClassFileInstaller
 *      jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockExperimentalVMOptions
 *      -XX:+EnableJVMCI compiler.jvmci.compilerToVM.GetNextStackFrameTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import java.lang.reflect.Method;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotStackFrameReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.test.lib.Asserts;

public class GetNextStackFrameTest {
    private static final int RECURSION_AMOUNT = 3;
    private static final ResolvedJavaMethod REC_FRAME_METHOD;
    private static final ResolvedJavaMethod FRAME1_METHOD;
    private static final ResolvedJavaMethod FRAME2_METHOD;
    private static final ResolvedJavaMethod FRAME3_METHOD;
    private static final ResolvedJavaMethod FRAME4_METHOD;
    private static final ResolvedJavaMethod RUN_METHOD;

    static {
        Method method;
        try {
            Class<?> aClass = GetNextStackFrameTest.class;
            method = aClass.getDeclaredMethod("recursiveFrame", int.class);
            REC_FRAME_METHOD = CTVMUtilities.getResolvedMethod(method);
            method = aClass.getDeclaredMethod("frame1");
            FRAME1_METHOD = CTVMUtilities.getResolvedMethod(method);
            method = aClass.getDeclaredMethod("frame2");
            FRAME2_METHOD = CTVMUtilities.getResolvedMethod(method);
            method = aClass.getDeclaredMethod("frame3");
            FRAME3_METHOD = CTVMUtilities.getResolvedMethod(method);
            method = aClass.getDeclaredMethod("frame4");
            FRAME4_METHOD = CTVMUtilities.getResolvedMethod(method);
            method = Thread.class.getDeclaredMethod("run");
            RUN_METHOD = CTVMUtilities.getResolvedMethod(Thread.class, method);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: can't find a test method : " + e, e);
        }
    }

    public static void main(String[] args) {
        new GetNextStackFrameTest().test();
    }

    private void test() {
        // Create new thread to get new clean stack
        Thread thread = new Thread(() -> recursiveFrame(RECURSION_AMOUNT));
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new Error("Interrupted while waiting to join", e);
        }
    }

    // Helper methods for a longer stack
    private void recursiveFrame(int recursionAmount) {
        if (--recursionAmount != 0) {
            recursiveFrame(recursionAmount);
        } else {
            frame1();
        }
    }

    private void frame1() {
        frame2();
    }

    private void frame2() {
        frame3();
    }

    private void frame3() {
        frame4();
    }

    private void frame4() {
        check();
    }

    private void check() {
        findFirst();
        walkThrough();
        skipAll();
        findNextSkipped();
        findYourself();
    }

    /**
     * Finds the first topmost frame from the list of methods to search
     */
    private void findFirst() {
        checkNextFrameFor(null /* topmost frame */,
                new ResolvedJavaMethod[]
                        {FRAME2_METHOD, FRAME3_METHOD, FRAME4_METHOD},
                FRAME4_METHOD, 0);
    }

    /**
     * Walks through whole stack and checks that every frame could be found
     * while going down the stack till the end
     */
    private void walkThrough() {
        // Check that we would get a frame 4 starting from the topmost frame
        HotSpotStackFrameReference nextStackFrame = checkNextFrameFor(
                null /* topmost frame */,
                new ResolvedJavaMethod[] {FRAME4_METHOD},
                FRAME4_METHOD, 0);
        // Check that we would get a frame 3 starting from frame 4 when we try
        // to search one of the next two frames
        nextStackFrame = checkNextFrameFor(nextStackFrame,
                new ResolvedJavaMethod[] {FRAME3_METHOD,
                        FRAME2_METHOD},
                FRAME3_METHOD, 0);
        // Check that we would get a frame 1
        nextStackFrame = checkNextFrameFor(nextStackFrame,
                new ResolvedJavaMethod[] {FRAME1_METHOD},
                FRAME1_METHOD, 0);
        // Check that we would skip (RECURSION_AMOUNT - 1) methods and find a
        // recursionFrame starting from frame 1
        nextStackFrame = checkNextFrameFor(nextStackFrame,
                new ResolvedJavaMethod[] {REC_FRAME_METHOD},
                REC_FRAME_METHOD, RECURSION_AMOUNT - 1);
        // Check that we would get a Thread::run method frame;
        nextStackFrame = checkNextFrameFor(nextStackFrame,
                new ResolvedJavaMethod[] {RUN_METHOD},
                RUN_METHOD, 0);
        // Check that there are no more frames after thread's run method
        nextStackFrame = CompilerToVMHelper.getNextStackFrame(nextStackFrame,
                null /* any */, 0);
        Asserts.assertNull(nextStackFrame,
                "Found stack frame after Thread::run");
    }

    /**
     * Skips all frames to get null at the end of the stack
     */
    private void skipAll() {
        // Skip all frames (stack size) + 2 (getNextStackFrame() itself
        // and from CompilerToVMHelper)
        int initialSkip = Thread.currentThread().getStackTrace().length + 2;
        HotSpotStackFrameReference nextStackFrame = CompilerToVMHelper
                .getNextStackFrame(null /* topmost frame */, null /* any */,
                        initialSkip);
        Asserts.assertNull(nextStackFrame, "Unexpected frame");
    }

    /**
     * Search for any frame skipping one frame
     */
    private void findNextSkipped() {
        // Get frame 4
        HotSpotStackFrameReference nextStackFrame = CompilerToVMHelper
                .getNextStackFrame(null /* topmost frame */,
                        new ResolvedJavaMethod[] {FRAME4_METHOD}, 0);
        // Get frame 2 by skipping one method starting from frame 4
        checkNextFrameFor(nextStackFrame, null /* any */,
                FRAME2_METHOD , 1 /* skip one */);
    }

    /**
     * Finds test method in the stack
     */
    private void findYourself() {
        Method method;
        Class<?> aClass = CompilerToVMHelper.CompilerToVMClass();
        try {
            method = aClass.getDeclaredMethod(
                    "getNextStackFrame",
                    HotSpotStackFrameReference.class,
                    ResolvedJavaMethod[].class,
                    int.class);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: can't find getNextStackFrame : " + e, e);
        }
        ResolvedJavaMethod self
                = CTVMUtilities.getResolvedMethod(aClass, method);
        checkNextFrameFor(null /* topmost frame */, null /* any */, self, 0);
    }

    /**
     * Searches next frame and checks that it equals to expected
     *
     * @param currentFrame  start frame to search from
     * @param searchMethods a list of methods to search
     * @param expected      expected frame
     * @param skip          amount of frames to be skipped
     * @return frame reference
     */
    private HotSpotStackFrameReference checkNextFrameFor(
            HotSpotStackFrameReference currentFrame,
            ResolvedJavaMethod[] searchMethods,
            ResolvedJavaMethod expected,
            int skip) {
        HotSpotStackFrameReference nextStackFrame = CompilerToVMHelper
                .getNextStackFrame(currentFrame, searchMethods, skip);
        Asserts.assertNotNull(nextStackFrame);
        Asserts.assertTrue(nextStackFrame.isMethod(expected),
                "Unexpected next frame: " + nextStackFrame
                        + " from current frame: " + currentFrame);
        return nextStackFrame;
    }
}
