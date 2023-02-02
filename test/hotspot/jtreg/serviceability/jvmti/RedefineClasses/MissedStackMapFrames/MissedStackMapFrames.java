/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @bug 8228604
 *
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @library /test/lib
 *
 * @run main/othervm/native -agentlib:MissedStackMapFrames MissedStackMapFrames
 */

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;


public class MissedStackMapFrames {
    static {
        System.loadLibrary("MissedStackMapFrames");
    }

    /* For each test class:
     *  - loads class (JNIEnv::FindClass);
     *  - retransforms class (jvmtiEnv::RetransformClasses).
     * Saves class bytes passed to ClassFileLoadHook.
     */
    private static native boolean doTest();

    /* methods to analyze doTest results */
    private static native int testCount();
    private static native Class testClass(int idx);
    private static native byte[] loadBytes(int idx);
    private static native byte[] retransformBytes(int idx);

    private static int getStackMapFrameCount(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        final int[] frameCount = {0};
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private int methodFrames = 0;
                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local,
                                           int numStack, Object[] stack) {
                        methodFrames++;
                    }
                    @Override
                    public void visitEnd() {
                        log("  method " + name + " - " + methodFrames + " frames");
                        frameCount[0] += methodFrames;
                    }
                };
            }
        };
        reader.accept(cv, 0);
        return frameCount[0];
    }

    private static int checkStackMapFrames(String mode, byte[] classfileBuffer) {
        log(mode + ", len = " + classfileBuffer.length);
        int frameCount = getStackMapFrameCount(classfileBuffer);
        log("  Has stack map frames: " + frameCount);
        if (frameCount == 0) {
            throw new RuntimeException(mode + " - no stack frames");
        }
        return frameCount;
    }

    private static void checkStackMapFrames(String mode, byte[] classfileBuffer, int expectedCount) {
        int actualCount = checkStackMapFrames(mode, classfileBuffer);
        if (actualCount != expectedCount) {
            throw new RuntimeException(mode + " - unexpected stack frames count: " + actualCount
                                       + " (expected " + expectedCount + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        if (!doTest()) {
            throw new RuntimeException("Test failed");
        }

        // verify results
        for (int i = 0; i < testCount(); i++) {
            Class cls = testClass(i);
            byte[] loadBytes = loadBytes(i);
            byte[] retransformBytes = retransformBytes(i);
            int loadCount = checkStackMapFrames(cls + "(load)", loadBytes);
            checkStackMapFrames(cls + "(retransform)", retransformBytes, loadCount);
        }
    }

    private static void log(Object msg) {
        System.out.println(msg);
    }

}
