/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xcomp -XX:-TieredCompilation -XX:+UnlockExperimentalVMOptions -XX:+HotCodeHeap
 *                   -XX:+NMethodRelocation -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:HotCodeIntervalSeconds=0
 *                   -XX:HotCodeSampleSeconds=10 -XX:HotCodeSteadyThreshold=1 -XX:HotCodeSampleRatio=1
 *                   compiler.hotcodegrouper.StressHotCodeGrouper
 */

package compiler.hotcodegrouper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.whitebox.WhiteBox;

public class StressHotCodeGrouper {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static TestMethod[] methods = new TestMethod[100];

    private static byte[] num1;
    private static byte[] num2;

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

    private static void generateCode() throws Exception {
        byte[] result = new byte[num1.length + 1];

        for (int i = 0; i < methods.length; i++) {
            methods[i] = new TestMethod();
        }
    }

    public static void main(String[] args) throws Exception {

        initNums();
        generateCode();

        long start = System.currentTimeMillis();
        Random random = new Random();

        while (System.currentTimeMillis() - start < 60_000) {
            for (TestMethod m : methods) {
                if (random.nextInt(100) < 10) {
                    m.deoptimize();
                }

                byte[] result = new byte[num1.length + 1];
                m.invoke(num1, num2, result);
            }
        }
    }

    private static final class TestMethod {
        private static final String CLASS_NAME = "A";
        private static final String METHOD_TO_COMPILE = "sum";
        private static final String JAVA_CODE = """
        public class A {

            public static void sum(byte[] n1, byte[] n2, byte[] out) {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 100) {}

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

        public void invoke(byte[] num1, byte[] num2, byte[] result) throws Exception {
            method.invoke(null, num1, num2, result);
        }

        public void deoptimize() {
            WHITE_BOX.deoptimizeMethod(method);
        }
    }
}
