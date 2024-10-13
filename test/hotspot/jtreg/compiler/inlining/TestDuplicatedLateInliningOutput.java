/*
 * Copyright (c) 2023, Red Hat and/or its affiliates. All rights reserved.
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
 * @bug 8320237
 * @summary late inlining output shouldn't produce both failure and success messages
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @run driver compiler.inlining.TestDuplicatedLateInliningOutput
 */

package compiler.inlining;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.stream.IntStream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestDuplicatedLateInliningOutput {
    public static void main(String[] args) throws Exception {
        test(
            NonConstantReceiverLauncher.class,
            "@ (\\d+)\\s+java\\.lang\\.invoke\\.LambdaForm\\$DMH\\/0x[0-9a-f]+::invokeStatic \\(\\d+ bytes\\)\\s+force inline by annotation",
            "@ (\\d+)\\s+java\\.lang\\.invoke\\.MethodHandle::invokeBasic\\(\\)V \\(\\d+ bytes\\)\\s+failed to inline: receiver not constant");

        test(
            VirtualCallLauncher.class,
            "@ (\\d+)\\s+compiler\\.inlining\\.TestDuplicatedLateInliningOutput\\$VirtualCallLauncher\\$B::lateInlined2 \\(\\d+ bytes\\)\\s+inline \\(hot\\)",
            "@ (\\d+)\\s+compiler\\.inlining\\.TestDuplicatedLateInliningOutput\\$VirtualCallLauncher\\$A::lateInlined2 \\(\\d+ bytes\\)\\s+failed to inline: virtual call"
        );
    }

    private static void test(Class<?> launcher, String pattern1, String pattern2) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+PrintInlining",
                "-XX:CICompilerCount=1",
                "-XX:-TieredCompilation",
                "-XX:-BackgroundCompilation",
                launcher.getName());

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        analyzer.outputTo(System.out);
        analyzer.errorTo(System.err);

        List<String> lines = analyzer.asLines();
        int index = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).trim().matches(pattern1))
                .findFirst()
                .orElseThrow(() -> new Exception("No inlining found"));

        if (lines.get(index - 1).trim().matches(pattern2)) {
            throw new Exception("Both failure and success message found");
        }
    }

    static class NonConstantReceiverLauncher {
        static final MethodHandle mh1;
        static MethodHandle mh2;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                mh1 = lookup.findStatic(NonConstantReceiverLauncher.class, "lateInlined", MethodType.methodType(void.class));
                mh2 = mh1;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                e.printStackTrace();
                throw new RuntimeException("Method handle lookup failed");
            }
        }

        public static void main(String[] args) throws Throwable {
            for (int i = 0; i < 20_000; i++) {
                test(true);
                inlined(false);
            }
        }

        private static void lateInlined() {
            // noop
        }

        private static void test(boolean flag) throws Throwable {
            MethodHandle mh = null;
            if (flag) {
                mh = inlined(flag);
            }
            mh.invokeExact();
        }

        private static MethodHandle inlined(boolean flag) {
            if (flag) {
                return mh1;
            }
            return mh2;
        }
    }

    static class VirtualCallLauncher {
        static final A obj1 = new B();
        static final A obj2 = new C();
        static final A obj3 = new D();

        public static void main(String[] args) throws Throwable {
            for (int i = 0; i < 20_000; i++) {
                test2(true);
                inlined2(false);
                inlined3(obj1);
                inlined3(obj2);
                inlined3(obj3);
            }
        }

        private static void test2(boolean flag) {
            A a = null;
            if (flag) {
                a = inlined2(flag);
            }
            inlined3(a);
        }

        private static A inlined2(boolean flag) {
            if (flag) {
                return obj1;
            }
            return obj2;
        }

        private static void inlined3(A a) {
            a.lateInlined2();
        }

        private static abstract class A {
            abstract void lateInlined2();
        }

        private static class B extends A {
            @Override
            void lateInlined2() {
                // noop
            }
        }

        private static class C extends A {
            @Override
            void lateInlined2() {
                // noop
            }
        }

        private static class D extends A {
            @Override
            void lateInlined2() {
                // noop
            }
        }
    }
}
