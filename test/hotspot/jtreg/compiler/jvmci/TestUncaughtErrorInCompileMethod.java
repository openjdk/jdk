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
 * @summary Tests handling of an exception thrown by HotSpotJVMCIRuntime.compileMethod.
 *          Requires a debug VM as it uses test.jvmci.compileMethodExceptionIsFatal
 *          which is only read in a debug VM.
 * @requires vm.jvmci
 * @requires vm.debug
 * @library /test/lib /
 * @modules jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.services
 * @run driver jdk.test.lib.FileInstaller ./TestUncaughtErrorInCompileMethod.config
 *     ./META-INF/services/jdk.vm.ci.services.JVMCIServiceLocator
 * @run driver compiler.jvmci.TestUncaughtErrorInCompileMethod
 */

package compiler.jvmci;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.vm.ci.services.JVMCIServiceLocator;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.runtime.JVMCIRuntime;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestUncaughtErrorInCompileMethod extends JVMCIServiceLocator {

    static volatile boolean compilerCreationErrorOccurred;

    /**
     * @param args if args.length != 0, then executing in subprocess
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            testSubprocess(false);
            testSubprocess(true);
        } else {
            int total = 0;
            while (!compilerCreationErrorOccurred) {
                // Do some random work to trigger compilation
                total += getTime();
                total += String.valueOf(total).hashCode();
            }
            System.out.println(total);
        }
    }

    private static long getTime() {
        return System.currentTimeMillis();
    }

    static void testSubprocess(boolean fatalError) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseJVMCICompiler", "-Djvmci.Compiler=ErrorCompiler",
            "-XX:-TieredCompilation",
            "-XX:+PrintCompilation",
            "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED",
            "-Dtest.jvmci.compileMethodExceptionIsFatal=" + (fatalError ? "true" : "false"),
            "-XX:+PrintWarnings",
            "-Xbootclasspath/a:.",
            TestUncaughtErrorInCompileMethod.class.getName(), "true");
        Process p = pb.start();
        OutputAnalyzer output = new OutputAnalyzer(p);

        if (!waitForProcess(p)) {
            // The subprocess might not enter JVMCI compilation.
            // Print the subprocess output and pass the test in this case.
            System.out.println(output.getOutput());
            return;
        }

        if (fatalError) {
            output.shouldContain("testing JVMCI fatal exception handling");
            output.shouldNotHaveExitValue(0);
            File hs_err_file = openHsErrFileFromOutput(output);
            Path hsErrPath = hs_err_file.toPath();
            if (!Files.exists(hsErrPath)) {
                throw new RuntimeException("hs_err_pid file missing at " + hsErrPath);
            }
            String hsErr = Files.readString(hsErrPath);

            /*
             * JVMCI Events (11 events):
             * ...
             * Event: 0.274 Thread 0x0000000146819210 compiler.jvmci.TestUncaughtErrorInCompileMethod$CompilerCreationError
             * Event: 0.274 Thread 0x0000000146819210  at compiler.jvmci.TestUncaughtErrorInCompileMethod$1.createCompiler(TestUncaughtErrorInCompileMethod.java:147)
             * Event: 0.274 Thread 0x0000000146819210  at jdk.internal.vm.ci/jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.getCompiler(HotSpotJVMCIRuntime.java:829)
             * Event: 0.274 Thread 0x0000000146819210  at jdk.internal.vm.ci/jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.compileMethod(HotSpotJVMCIRuntime.java:943)
             */

            // Check that hs-err contains the stack trace of the fatal exception (sample shown above)
            String[] stackTraceSubstrings = {
                "at compiler.jvmci.TestUncaughtErrorInCompileMethod$1.createCompiler(TestUncaughtErrorInCompileMethod.java",
                "at jdk.internal.vm.ci/jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.compileMethod(HotSpotJVMCIRuntime.java"
            };
            for (String expect : stackTraceSubstrings) {
                if (!hsErr.contains(expect)) {
                    throw new RuntimeException("Could not find \"" + expect + "\" in " + hsErrPath);
                }
            }
        } else {
            output.shouldContain("COMPILE SKIPPED: uncaught exception in call_HotSpotJVMCIRuntime_compileMethod [compiler.jvmci.TestUncaughtErrorInCompileMethod$CompilerCreationError");
            output.shouldHaveExitValue(0);
        }
    }

    /**
     * @return true if {@code p} exited on its own, false if it had to be destroyed
     */
    private static boolean waitForProcess(Process p) {
        while (true) {
            try {
                boolean exited = p.waitFor(10, TimeUnit.SECONDS);
                if (!exited) {
                    System.out.println("destroying process: " + p);
                    p.destroy();
                    Thread.sleep(1000);
                    while (p.isAlive()) {
                        System.out.println("forcibly destroying process: " + p);
                        Thread.sleep(1000);
                        p.destroyForcibly();
                    }
                    return false;
                }
                return true;
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
            }
        }
    }

    public TestUncaughtErrorInCompileMethod() {
    }

    static class CompilerCreationError extends InternalError {
        CompilerCreationError(int attempt) {
            super("attempt " + attempt);
        }
    }

    @Override
    public <S> S getProvider(Class<S> service) {
        if (service == JVMCICompilerFactory.class) {
            return service.cast(new JVMCICompilerFactory() {
                final AtomicInteger counter = new AtomicInteger();
                @Override
                public String getCompilerName() {
                    return "ErrorCompiler";
                }

                @Override
                public JVMCICompiler createCompiler(JVMCIRuntime runtime) {
                    int attempt = counter.incrementAndGet();
                    CompilerCreationError e = new CompilerCreationError(attempt);
                    e.printStackTrace();
                    if (attempt >= 10) {
                        // Delay notifying the loop in main so that compilation failures
                        // have time to be reported by -XX:+PrintCompilation.
                        compilerCreationErrorOccurred = true;
                    }
                    throw e;
                }
            });
        }
        return null;
    }

    /**
     * Given the output of a java VM that crashed, extract the name of the hs-err file from the output
     */
    public static String extractHsErrFileNameFromOutput(OutputAnalyzer output) {
        output.shouldMatch("# A fatal error has been detected.*");

        // extract hs-err file
        String hs_err_file = output.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        if (hs_err_file == null) {
            throw new RuntimeException("Did not find hs-err file in output.\n");
        }

        return hs_err_file;
    }

    /**
     * Given the output of a java VM that crashed, extract the name of the hs-err file from the output,
     * open that file and return its File.
     */
    public static File openHsErrFileFromOutput(OutputAnalyzer output) {
        String name = extractHsErrFileNameFromOutput(output);
        File f = new File(name);
        if (!f.exists()) {
            throw new RuntimeException("Cannot find hs-err file at " + f.getAbsolutePath());
        }
        return f;
    }
}
