/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.HelloApp.configureAndExecute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CfgFile;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.LauncherVerifier;
import jdk.jpackage.test.TKit;

/**
 * Test the child process has a chance to handle Ctrl+C signal.
 */

/*
 * @test
 * @summary Test case for JDK-8365790
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @build Win8365790Test
 * @requires (os.family == "windows")
 * @run main/othervm/timeout=100 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=Win8365790Test
 */
public class Win8365790Test {

    @Test
    public void test() throws InterruptedException, IOException {

        var outputDir = TKit.createTempDirectory("response-dir");

        var mainOutputFile = outputDir.resolve("output.txt");
        var mainTraceFile = outputDir.resolve("trace.txt");

        var probeOutputFile = outputDir.resolve("probe-output.txt");
        var probeTraceFile = outputDir.resolve("probe-trace.txt");

        var cmd = JPackageCommand
                .helloAppImage(TEST_APP_JAVA + "*UseShutdownHook")
                .ignoreFakeRuntime()
                .addArguments("--java-options", "-Djpackage.test.trace-file=" + mainTraceFile.toString())
                .addArguments("--arguments", mainOutputFile.toString())
                .addArguments("--arguments", Long.toString(Duration.ofSeconds(TETS_APP_AUTOCLOSE_TIMEOUT_SECONDS).getSeconds()));

        new AdditionalLauncher("probe")
                .withoutVerifyActions(LauncherVerifier.Action.values())
                .addJavaOptions("-Djpackage.test.trace-file=" + probeTraceFile.toString())
                .addDefaultArguments(probeOutputFile.toString(), Long.toString(Duration.ofSeconds(TETS_APP_AUTOCLOSE_TIMEOUT_SECONDS).getSeconds()))
                .applyTo(cmd);

        cmd.executeAndAssertImageCreated();

        cmd.readLauncherCfgFile("probe")
                .add(new CfgFile().addValue("Application", "win.norestart", Boolean.TRUE.toString()))
                .save(cmd.appLauncherCfgPath("probe"));

        // Try Ctrl+C signal on a launcher with disabled restart functionality.
        // It will create a single launcher process instead of the parent and the child processes.
        // Ctrl+C always worked for launcher with disabled restart functionality.
        var probeOutput = runLauncher(cmd, "probe", probeTraceFile, probeOutputFile);

        if (!probeOutput.equals("shutdown hook executed")) {
            // Ctrl+C signal didn't make it. Test environment doesn't support Ctrl+C signal
            // delivery from the prowershell process to a child process, don't run the main
            // test.
            TKit.throwSkippedException(
                    "The environment does NOT support Ctrl+C signal delivery from the prowershell process to a child process");
        }

        var mainOutput = runLauncher(cmd, null, mainTraceFile, mainOutputFile);

        TKit.assertEquals("shutdown hook executed", mainOutput, "Check shutdown hook executed");
    }

    private static String runLauncher(JPackageCommand cmd, String launcherName, Path traceFile, Path outputFile) throws IOException {
        // Launch the specified launcher and send Ctrl+C signal to it.

        var state = TKit.state();

        var future = CompletableFuture.runAsync(() -> {
            TKit.withState(() -> {
                configureAndExecute(0, Executor.of("powershell", "-NonInteractive", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Unrestricted")
                        .addArgument("-File").addArgument(TEST_PS1)
                        .addArguments("-TimeoutSeconds", Long.toString(Duration.ofSeconds(5).getSeconds()))
                        .addArgument("-Executable").addArgument(cmd.appLauncherPath(launcherName))
                        .dumpOutput());
            }, state);
        }, Executors.newVirtualThreadPerTaskExecutor());

        TKit.waitForFileCreated(traceFile, Duration.ofSeconds(20), Duration.ofSeconds(2));

        try {
            TKit.waitForFileCreated(outputFile, Duration.ofSeconds(TETS_APP_AUTOCLOSE_TIMEOUT_SECONDS * 2), Duration.ofSeconds(2));
        } finally {
            TKit.traceFileContents(traceFile, "Test app trace");
        }

        TKit.assertFileExists(outputFile);

        // Call join() on the future to make the test fail if the future execution resulted in a throw.
        future.join();

        return Files.readString(outputFile);
    }

    private static final long TETS_APP_AUTOCLOSE_TIMEOUT_SECONDS = 30;

    private static final Path TEST_APP_JAVA = TKit.TEST_SRC_ROOT.resolve("apps/UseShutdownHook.java");
    private static final Path TEST_PS1 = TKit.TEST_SRC_ROOT.resolve(Path.of("resources/Win8365790Test.ps1")).normalize();
}
