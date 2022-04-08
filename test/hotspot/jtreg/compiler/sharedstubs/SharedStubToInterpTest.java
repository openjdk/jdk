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

/**
 * @test SharedStubToInterpTest
 * @summary Checks that stubs to the interpreter can be shared for static or final method.
 * @bug 8280481
 * @library /test/lib
 *
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="i386" | os.arch=="x86" | os.arch=="aarch64"
 *
 * @run driver compiler.sharedstubs.SharedStubToInterpTest c2 StaticMethodTest
 * @run driver compiler.sharedstubs.SharedStubToInterpTest c1 StaticMethodTest
 * @run driver compiler.sharedstubs.SharedStubToInterpTest c2 FinalClassTest
 * @run driver compiler.sharedstubs.SharedStubToInterpTest c1 FinalClassTest
 * @run driver compiler.sharedstubs.SharedStubToInterpTest c2 FinalMethodTest
 * @run driver compiler.sharedstubs.SharedStubToInterpTest c1 FinalMethodTest
 */

package compiler.sharedstubs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SharedStubToInterpTest {
    public static void main(String[] args) throws Exception {
        String compiler = args[0];
        String testClassName = SharedStubToInterpTest.class.getName() + "$" + args[1];
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        if (compiler.equals("c2")) {
            command.add("-XX:-TieredCompilation");
        } else if (compiler.equals("c1")) {
            command.add("-XX:TieredStopAtLevel=1");
        } else {
            throw new RuntimeException("Unknown compiler: " + compiler);
        }
        command.add("-Xbatch");
        command.add("-XX:CompileCommand=compileonly," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=print," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=exclude," + testClassName + "::" + "log");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "log");
        command.add(testClassName);
        command.add("a");
        command.add("b");
        command.add("c");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer);
    }

    private static String skipTo(Iterator<String> iter, String substring) {
        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.contains(substring)) {
                return nextLine;
            }
        }
        return null;
    }

    private static void checkOutput(OutputAnalyzer output) {
        Iterator<String> iter = output.asLines().listIterator();

        String match = skipTo(iter, "Compiled method");
        while (match != null && !match.contains("Test::test")) {
            match = skipTo(iter, "Compiled method");
        }
        if (match == null) {
            throw new RuntimeException("Missing compiler output for the method 'test'");
        }

        // Shared static stubs are put after Deopt Handler Code.
        match = skipTo(iter, "{runtime_call DeoptimizationBlob}");
        if (match == null) {
            throw new RuntimeException("The start of Deopt Handler Code not found");
        }
        int foundStaticStubs = 0;
        while (iter.hasNext()) {
            if (iter.next().contains("{static_stub}")) {
                foundStaticStubs += 1;
            }
        }

        final int expectedStaticStubs = 1;
        if (foundStaticStubs != expectedStaticStubs) {
            throw new RuntimeException("Found static stubs: " + foundStaticStubs + "; Expected static stubs: " + expectedStaticStubs);
        }
    }

    public static class StaticMethodTest {
        static void log(int i) {
        }

        static void test(int i, String[] args) {
            if (i % args.length == 0) {
                log(i);
            } else {
                log(i);
            }
        }

        public static void main(String[] args) {
            for (int i = 1; i < 50_000; ++i) {
                test(i, args);
            }
        }
    }

    public static final class FinalClassTest {
        void log(int i) {
        }

        void test(int i, String[] args) {
            if (i % args.length == 0) {
                log(i);
            } else {
                log(i);
            }
        }

        public static void main(String[] args) {
            FinalClassTest tFC = new FinalClassTest();
            for (int i = 1; i < 50_000; ++i) {
                tFC.test(i,args);
            }
        }
    }

    public static class FinalMethodTest {
        final void log(int i) {
        }

        void test(int i, String[] args) {
            if (i % args.length == 0) {
                log(i);
            } else {
                log(i);
            }
        }

        public static void main(String[] args) {
            FinalMethodTest tFM = new FinalMethodTest();
            for (int i = 1; i < 50_000; ++i) {
                tFM.test(i,args);
            }
        }
    }
}
