/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.misc
 * @library /testlibrary
 * @run main ContinuousCallSiteTargetChange
 */
import java.lang.invoke.*;
import jdk.test.lib.*;

public class ContinuousCallSiteTargetChange {
    static void testServer() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-server", "-XX:-TieredCompilation", "-Xbatch",
                "-XX:PerBytecodeRecompilationCutoff=10", "-XX:PerMethodRecompilationCutoff=10",
                "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
                "ContinuousCallSiteTargetChange$Test", "100");

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        analyzer.shouldNotContain("made not compilable");
        analyzer.shouldNotContain("decompile_count > PerMethodRecompilationCutoff");
    }

    static void testClient() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-client", "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-Xbatch",
                "-XX:PerBytecodeRecompilationCutoff=10", "-XX:PerMethodRecompilationCutoff=10",
                "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
                "ContinuousCallSiteTargetChange$Test", "100");

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        analyzer.shouldNotContain("made not compilable");
        analyzer.shouldNotContain("decompile_count > PerMethodRecompilationCutoff");
    }

    public static void main(String[] args) throws Exception {
        testServer();
        testClient();
    }

    static class Test {
        static final MethodType mt = MethodType.methodType(void.class);
        static final CallSite cs = new MutableCallSite(mt);

        static final MethodHandle mh = cs.dynamicInvoker();

        static void f() {
        }

        static void test1() throws Throwable {
            mh.invokeExact();
        }

        static void test2() throws Throwable {
            cs.getTarget().invokeExact();
        }

        static void iteration() throws Throwable {
            MethodHandle mh1 = MethodHandles.lookup().findStatic(ContinuousCallSiteTargetChange.Test.class, "f", mt);
            cs.setTarget(mh1);
            for (int i = 0; i < 20_000; i++) {
                test1();
                test2();
            }
        }

        public static void main(String[] args) throws Throwable {
            int iterations = Integer.parseInt(args[0]);
            for (int i = 0; i < iterations; i++) {
                iteration();
            }
        }
    }
}
