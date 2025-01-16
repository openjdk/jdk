/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8294609
 * @requires vm.compiler2.enabled & vm.flagless
 *
 * @library /test/lib
 *
 * @build compiler.c2.unloaded.TestInlineUnloaded
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar launcher.jar
 *                  compiler.c2.unloaded.TestInlineUnloaded
 *                  compiler.c2.unloaded.TestInlineUnloaded$Launcher
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar parent.jar
 *                  compiler.c2.unloaded.TestInlineUnloaded$Parent
 *                  compiler.c2.unloaded.TestInlineUnloaded$Parent$U
 *                  compiler.c2.unloaded.TestInlineUnloaded$Parent$TestCase
 *                  compiler.c2.unloaded.TestInlineUnloaded$Parent$Invoker
 *                  compiler.c2.unloaded.TestInlineUnloaded$Parent$TestNull
 *                  compiler.c2.unloaded.TestInlineUnloaded$Parent$TestLoadedRemotely
 *                  compiler.c2.unloaded.TestInlineUnloaded$Parent$TestUnloaded
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar caller.jar
 *                  compiler.c2.unloaded.TestInlineUnloaded$Caller
 *                  compiler.c2.unloaded.TestInlineUnloaded$Caller$TestNull
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar callee.jar
 *                  compiler.c2.unloaded.TestInlineUnloaded$Callee
 *                  compiler.c2.unloaded.TestInlineUnloaded$Callee$TestNull
 *
 * @run driver compiler.c2.unloaded.TestInlineUnloaded
 */

package compiler.c2.unloaded;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Consumer;

public class TestInlineUnloaded {
    static final String THIS_CLASS = TestInlineUnloaded.class.getName();

    public static class Parent {
        public class U {
        }

        public interface TestCase {
            U test(Invoker obj, U arg);

            void testArg(Invoker obj, U arg);

            U testRet(Invoker obj);

            void test(Invoker obj);
        }

        public interface Invoker {
            void invokeArg(U obj);

            U invokeRet();

            U invoke(U obj);
        }

        private static class TestNull implements Runnable {
            final TestCase test;
            final Invoker recv;

            public TestNull(TestCase test, Invoker recv) {
                this.test = test;
                this.recv = recv;
            }

            @Override
            public void run() {
                test.testArg(recv, null);
                test.testRet(recv);
                test.test(recv, null);
            }
        }

        public static class TestLoadedRemotely extends TestNull {
            public TestLoadedRemotely(TestCase test, Invoker recv) throws Exception {
                super(test, recv);
                Class.forName(U.class.getName()); // preload in parent context
            }
        }

        public static class TestUnloaded extends TestNull {
            public TestUnloaded(TestCase test, Invoker recv) {
                super(test, recv);
            }
        }
    }

    public static class Caller {
        public static class TestNull implements Parent.TestCase {
            public TestNull() {}

            public Parent.U test(Parent.Invoker obj, Parent.U arg) {
                return obj.invoke(arg);
            }

            public void testArg(Parent.Invoker obj, Parent.U arg) {
                obj.invokeArg(arg);
            }

            public Parent.U testRet(Parent.Invoker obj) {
                return obj.invokeRet();
            }

            public void test(Parent.Invoker obj) {
                test(obj, null);
            }
        }
    }

    public static class Callee {
        public static class TestNull implements Parent.Invoker {
            public void invokeArg(Parent.U obj) {}

            public Parent.U invokeRet() {
                return null;
            }

            public Parent.U invoke(Parent.U obj) {
                return obj;
            }
        }
    }

    public static class Launcher {
        public static void main(String... args) throws Exception {
            final String testName = args[0];

            URLClassLoader parentCL = new URLClassLoader("parent", new URL[] { new URL("file:parent.jar") }, ClassLoader.getSystemClassLoader());
            URLClassLoader callerCL = new URLClassLoader("caller", new URL[] { new URL("file:caller.jar") }, parentCL);
            URLClassLoader calleeCL = new URLClassLoader("callee", new URL[] { new URL("file:callee.jar") }, parentCL);

            Object caller = Class.forName(THIS_CLASS + "$Caller$TestNull", false, callerCL)
                                 .getDeclaredConstructor().newInstance();
            Object callee = Class.forName(THIS_CLASS + "$Callee$TestNull", false, calleeCL)
                                 .getDeclaredConstructor().newInstance();

            Class<?> testClass = Class.forName(THIS_CLASS + "$Parent$TestCase", false, parentCL);
            Class<?> invClass  = Class.forName(THIS_CLASS + "$Parent$Invoker",  false, parentCL);
            Class<?> test      = Class.forName(THIS_CLASS + "$Parent$" + testName, false, parentCL);
            Runnable r = (Runnable) test.getDeclaredConstructor(testClass, invClass)
                                       .newInstance(caller, callee);

            for (int i = 0; i < 20_000; i ++) {
                r.run();
            }
        }
    }

    static void run(String testCaseName, Consumer<OutputAnalyzer> processor) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();

        pb.command(JDKToolFinder.getJDKTool("java"),
            "-cp", "launcher.jar",
            "-XX:+IgnoreUnrecognizedVMOptions", "-showversion",
            "-XX:-TieredCompilation", "-Xbatch",
            "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
            "-XX:CompileCommand=quiet", "-XX:CompileCommand=compileonly,*TestNull::run",
            Launcher.class.getName(), testCaseName);

        System.out.println("Command line: [" + pb.command() + "]");

        OutputAnalyzer analyzer = ProcessTools.executeProcess(pb);

        analyzer.shouldHaveExitValue(0);

        // The test is applicable only to C2 (present in Server VM).
        analyzer.stderrShouldContain("Server VM");

        analyzer.shouldContain("TestNull::run"); // ensure that relevant method is compiled

        processor.accept(analyzer); // test-specific checks
    }

    public static void main(String[] args) throws Exception {
        run("TestUnloaded", output -> {
            output.shouldMatch("TestNull::testArg .* unloaded signature classes");
            output.shouldMatch("TestNull::testRet .* unloaded signature classes");
            output.shouldMatch("TestNull::test .* unloaded signature classes");

            output.shouldMatch("TestNull::testArg .* failed to inline");
            output.shouldMatch("TestNull::testRet .* failed to inline");
            output.shouldMatch("TestNull::test .* failed to inline");
        });
        run("TestLoadedRemotely", output -> {
            output.shouldMatch("TestNull::testArg .* inline");
            output.shouldMatch("TestNull::testRet .* inline");
            output.shouldMatch("TestNull::test .* inline");

            output.shouldNotMatch("TestNull::testArg .* unloaded signature classes");
            output.shouldNotMatch("TestNull::testRet .* unloaded signature classes");
            output.shouldNotMatch("TestNull::test .* unloaded signature classes");
        });
    }
}
