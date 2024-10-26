/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

 /* @test
 * @bug 8340311
 * @summary Test that jpackage windows app launcher doesn't create child process
 *          if `win.norestart` property is set in the corresponding .cfg file
 * @library ../helpers
 * @library /test/lib
 * @requires os.family == "windows"
 * @build jdk.jpackage.test.*
 * @build WinNoRestartTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinNoRestartTest
 *
 */
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CfgFile;
import jdk.jpackage.test.HelloApp;
import static jdk.jpackage.test.WindowsHelper.findAppLauncherPID;
import static jdk.jpackage.test.WindowsHelper.killProcess;

public class WinNoRestartTest {

    @Test
    public static void test() throws InterruptedException, IOException {
        var cmd = JPackageCommand.helloAppImage().ignoreFakeRuntime();

        // Configure test app to launch in a way it will not exit
        cmd.addArguments("--java-options", "-Djpackage.test.noexit=true");
        cmd.execute();

        var origCfgFile = CfgFile.load(cmd.appLauncherCfgPath(null));

        for (var testConfig : testConfig()) {
            testConfig.apply(cmd, origCfgFile);
        }
    }

    private static record NoRerunSectionConfig(String sectionName,
            String firstValue, String secondValue) {

        void apply(CfgFile cfgFile) {
            if (firstValue != null) {
                cfgFile.setValue(sectionName, "win.norestart", firstValue);
            }
            if (secondValue != null) {
                cfgFile.setValue(sectionName, "win.norestart", secondValue);
            }
        }
    }

    private static record NoRerunConfig(NoRerunSectionConfig firstSection,
            NoRerunSectionConfig secondSection, boolean expectedNoRestarted) {

        void apply(JPackageCommand cmd, CfgFile origCfgFile) throws InterruptedException {
            // Alter the main launcher .cfg file
            var cfgFile = new CfgFile();
            if (firstSection != null) {
                firstSection.apply(cfgFile);
            }
            cfgFile = CfgFile.combine(cfgFile, origCfgFile);

            if (secondSection != null) {
                secondSection.apply(cfgFile);
            }

            // Save updated main launcher .cfg file
            cfgFile.save(cmd.appLauncherCfgPath(null));

            try ( // Launch the app in a separate thread
                ExecutorService exec = Executors.newSingleThreadExecutor()) {
                exec.execute(() -> {
                    HelloApp.executeLauncher(cmd);
                });

                // Wait a bit to let the app start
                Thread.sleep(Duration.ofSeconds(10));

                // Get PID of the main app launcher process
                final long pid = findAppLauncherPID(cmd, null,
                        expectedNoRestarted ? 1 : 2).get();

                // Kill the main app launcher process
                killProcess(pid);
            }
        }
    }

    private static List<NoRerunConfig> testConfig() {
        return List.of(
            testConfig(true, "true"),
            testConfig(true, "7"),
            testConfig(true, "TRUE"),
            testConfig(false, "false"),
            testConfig(false, ""),
            testConfig(false, "true2"),
            testConfig(false, "true", ""),
            testConfig(true, "false", "true")
        );
    }

    private static NoRerunConfig testConfig(boolean expectedNorestart,
            String norestartValue) {
        return new NoRerunConfig(null, new NoRerunSectionConfig("Application",
                norestartValue, null), expectedNorestart);
    }

    private static NoRerunConfig testConfig(boolean expectedNorestart,
            String firstNorestartValue, String secondNorestartValue) {
        return new NoRerunConfig(null, new NoRerunSectionConfig("Application",
                firstNorestartValue, secondNorestartValue), expectedNorestart);
    }
}
