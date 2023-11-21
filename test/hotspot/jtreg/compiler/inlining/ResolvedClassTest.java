/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8279515
 *
 * @requires vm.flagless & vm.compiler1.enabled & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 *
 * @run driver compiler.jsr292.ResolvedClassTest
 */

package compiler.jsr292;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;

public class ResolvedClassTest {
    /* ======================================================================== */
    static void testStatic() throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+IgnoreUnrecognizedVMOptions", "-showversion",
                "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
                "-Xbatch", "-XX:CompileCommand=quiet", "-XX:CompileCommand=compileonly," + TestStatic.class.getName() + "::test",
                TestStatic.class.getName());

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        analyzer.shouldNotContain("TestStatic$A::m (1 bytes)   failed to inline: not inlineable");
        analyzer.shouldNotContain("TestStatic$A::m (1 bytes)   failed to inline: no static binding");

        analyzer.shouldContain("TestStatic$A::m (1 bytes)   inline");
    }

    static class TestStatic {
        static class A {
            static void m() {}
        }
        static class B extends A {}

        // @DontInline
        static void test() {
            B.m(); // invokestatic B "m" => A::m
        }

        public static void main(String[] args) {
            for (int i = 0; i < 20_000; i++) {
                test();
            }
        }
    }

    /* ======================================================================== */
    static void testStaticInit() throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+IgnoreUnrecognizedVMOptions", "-showversion",
                "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
                "-Xbatch", "-XX:CompileCommand=quiet", "-XX:CompileCommand=compileonly," + TestStaticInit.class.getName() + "::test",
                TestStaticInit.class.getName());

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        analyzer.shouldContain("TestStaticInit$A::m (1 bytes)   failed to inline: no static binding");
    }

    static class TestStaticInit {
        static class A {
            static {
                for (int i = 0; i < 20_000; i++) {
                    TestStaticInit.test();
                }
            }

            static void m() {}
        }
        static class B extends A {}

        // @DontInline
        static void test() {
            B.m(); // A::<clinit> => test() => A::m()
        }

        public static void main(String[] args) {
            A.m(); // trigger initialization of A
        }
    }

    /* ======================================================================== */
    static void testIndy() throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+IgnoreUnrecognizedVMOptions", "-showversion",
                "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
                "-Xbatch", "-XX:CompileCommand=quiet", "-XX:CompileCommand=compileonly," + TestIndy.class.getName() + "::test",
                TestIndy.class.getName());

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        analyzer.shouldNotMatch("java\\.lang\\.invoke\\..+::linkToTargetMethod \\(9 bytes\\)   failed to inline: not inlineable");

        analyzer.shouldMatch("java\\.lang\\.invoke\\..+::linkToTargetMethod \\(9 bytes\\)   force inline by annotation");
        analyzer.shouldContain("java/lang/invoke/MethodHandle::invokeBasic (not loaded)   failed to inline: not inlineable");
    }

    static class TestIndy {
        static String str = "";

        // @DontInline
        static void test() {
            String s1 = "" + str; // indy (linked)

            for (int i = 0; i < 200_000; i++) {} // trigger OSR compilation

            String s2 = "" + str; // indy (not linked)
        }

        public static void main(String[] args) {
            test();
        }
    }

    /* ======================================================================== */

    public static void main(String[] args) throws IOException {
        testStatic();
        testStaticInit();
        testIndy();
    }
}
