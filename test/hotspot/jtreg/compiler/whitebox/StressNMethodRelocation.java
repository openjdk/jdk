/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test StressNMethodRelocation
 * @summary Call and relocate methods concurrently
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache
 *                   compiler.whitebox.StressNMethodRelocation
 */

package compiler.whitebox;

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.CodeBlob;
import jdk.test.whitebox.code.NMethod;

import jdk.test.lib.compiler.InMemoryJavaCompiler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;

public class StressNMethodRelocation {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final int C2_LEVEL = 4;
    private static final int ACTIVE_METHODS = 1024;

    private static TestMethod[] methods;
    private static byte[] num1;
    private static byte[] num2;

    private static long DURATION = 60_000;

    public static void main(String[] args) throws Exception {
        // Initialize defaults
        initNums();

        // Generate compiled code
        methods = new TestMethod[ACTIVE_METHODS];
        generateCode(methods);

        // Create thread that runs compiled methods
        RunMethods runMethods = new RunMethods();
        Thread runMethodsThread = new Thread(runMethods);

        // Create thread that relocates compiled methods
        RelocateNMethods relocate = new RelocateNMethods();
        Thread relocateThread = new Thread(relocate);

        // Start theads
        runMethodsThread.start();
        relocateThread.start();

        // Wait for threads to finish
        runMethodsThread.join();
        relocateThread.join();
    }

    private static byte[] genNum(Random random, int digitCount) {
        byte[] num = new byte[digitCount];
        int d;
        do {
            d = random.nextInt(10);
        } while (d == 0);

        num[0] = (byte)d;
        for (int i = 1; i < digitCount; ++i) {
            num[i] = (byte)random.nextInt(10);
        }
        return num;
    }

    private static void initNums() {
        final long seed = 8374592837465123L;
        Random random = new Random(seed);

        final int digitCount = 40;
        num1 = genNum(random, digitCount);
        num2 = genNum(random, digitCount);
    }

    private static void generateCode(TestMethod[] m) throws Exception {
        byte[] result = new byte[num1.length + 1];

        for (int i = 0; i < ACTIVE_METHODS; ++i) {
            m[i] = new TestMethod();
            m[i].profile(num1, num2, result);
            m[i].compileWithC2();
        }
    }

    private static final class TestMethod {
        private static final String CLASS_NAME = "A";
        private static final String METHOD_TO_COMPILE = "sum";
        private static final String JAVA_CODE = """
        public class A {

            public static void sum(byte[] n1, byte[] n2, byte[] out) {
                final int digitCount = n1.length;
                int carry = 0;
                for (int i = digitCount - 1; i >= 0; --i) {
                    int sum = n1[i] + n2[i] + carry;
                    out[i] = (byte)(sum % 10);
                    carry = sum / 10;
                }
                if (carry != 0) {
                    for (int i = digitCount; i > 0; --i) {
                        out[i] = out[i - 1];
                    }
                    out[0] = (byte)carry;
                }
            }
        }""";

        private static final byte[] BYTE_CODE;

        static {
            BYTE_CODE = InMemoryJavaCompiler.compile(CLASS_NAME, JAVA_CODE);
        }

        private final Method method;

        private static ClassLoader createClassLoaderFor() {
            return new ClassLoader() {
                @Override
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    if (!name.equals(CLASS_NAME)) {
                        return super.loadClass(name);
                    }

                    return defineClass(name, BYTE_CODE, 0, BYTE_CODE.length);
                }
            };
        }

        public TestMethod() throws Exception {
            var cl = createClassLoaderFor().loadClass(CLASS_NAME);
            method = cl.getMethod(METHOD_TO_COMPILE, byte[].class, byte[].class, byte[].class);
            WHITE_BOX.testSetDontInlineMethod(method, true);
        }

        public void profile(byte[] num1, byte[] num2, byte[] result) throws Exception {
            method.invoke(null, num1, num2, result);
            WHITE_BOX.markMethodProfiled(method);
        }

        public void invoke(byte[] num1, byte[] num2, byte[] result) throws Exception {
            method.invoke(null, num1, num2, result);
        }

        public void compileWithC2() throws Exception {
            WHITE_BOX.enqueueMethodForCompilation(method, C2_LEVEL);
            while (WHITE_BOX.isMethodQueuedForCompilation(method)) {
                Thread.onSpinWait();
            }
            if (WHITE_BOX.getMethodCompilationLevel(method) != C2_LEVEL) {
                throw new IllegalStateException("Method " + method + " is not compiled by C2.");
            }
        }
    }

    private static final class RelocateNMethods implements Runnable {
        public RelocateNMethods() {}

        // Move nmethod back and forth between NonProfiled and Profiled code heaps
        public void run() {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < DURATION) {
                // Relocate NonProfiled to Profiled
                CodeBlob[] nonProfiledBlobs = CodeBlob.getCodeBlobs(BlobType.MethodNonProfiled);
                for (CodeBlob blob : nonProfiledBlobs) {
                    if (blob.isNMethod) {
                        WHITE_BOX.relocateMemNMethodTo0(blob.address, BlobType.MethodProfiled.id);
                    }
                }

                // Relocate Profiled to NonProfiled
                CodeBlob[] profiledBlobs = CodeBlob.getCodeBlobs(BlobType.MethodProfiled);
                for (CodeBlob blob : nonProfiledBlobs) {
                    if (blob.isNMethod) {
                        WHITE_BOX.relocateMemNMethodTo0(blob.address, BlobType.MethodNonProfiled.id);
                    }
                }
            }
        }
    }

    private static final class RunMethods implements Runnable {
        public RunMethods() {}

        public void run() {
            try {
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < DURATION) {
                    callMethods();
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        private void callMethods() throws Exception {
            for (var m : methods) {
                byte[] result = new byte[num1.length + 1];
                m.invoke(num1, num2, result);
            }
        }
    }

}
