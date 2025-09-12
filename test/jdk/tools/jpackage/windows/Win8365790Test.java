/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
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
 * @run main/othervm/timeout=360 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=Win8365790Test
 */
public class Win8365790Test {

    @Test
    public void test() throws InterruptedException, IOException {

        var outputDir = TKit.createTempDirectory("response-dir");
        var outputFile = outputDir.resolve("output.txt");

        var cmd = JPackageCommand
                .helloAppImage(TEST_APP_JAVA + "*UseShutdownHook")
                .ignoreFakeRuntime()
                .addArguments("--java-options", "-Djpackage.test.appOutput=" + outputFile.toString());

        cmd.executeAndAssertImageCreated();

        // Launch the main launcher and send Ctrl+C signal to it.
        Thread.ofVirtual().start(() -> {
            configureAndExecute(0, Executor.of("powershell", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Unrestricted", "-File", TEST_PS1.toString())
                    .addArguments("-TimeoutSeconds", "5")
                    .addArgument("-Executable").addArgument(cmd.appLauncherPath())
                    .dumpOutput());
        });

        TKit.waitForFileCreated(outputFile, Duration.ofSeconds(20), Duration.ofSeconds(1));

        TKit.assertFileExists(outputFile);
        TKit.assertEquals("shutdown hook executed", Files.readString(outputFile), "Check shutdown hook executed");
    }

    private static final Path TEST_APP_JAVA = TKit.TEST_SRC_ROOT.resolve("apps/UseShutdownHook.java");
    private static final Path TEST_PS1 = TKit.TEST_SRC_ROOT.resolve(Path.of("resources/Win8365790Test.ps1")).normalize();
}
