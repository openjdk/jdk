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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CfgFile;
import jdk.jpackage.test.HelloApp;
import static jdk.jpackage.test.WindowsHelper.killAppLauncherProcess;

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
 */

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
                cfgFile.addValue(sectionName, "win.norestart", firstValue);
            }
            if (secondValue != null) {
                cfgFile.addValue(sectionName, "win.norestart", secondValue);
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

                // Find the main app launcher process and kill it
                killAppLauncherProcess(cmd, null, expectedNoRestarted ? 1 : 2);
            }
        }
    }

    private static List<NoRerunConfig> testConfig() {
        return List.of(
            // Test boolean conversion
            withValue(true, "true"),
            withValue(true, "7"),
            withValue(true, "TRUE"),
            withValue(false, "false"),
            withValue(false, ""),
            withValue(false, "true2"),

            // Test multiple values of the property (the last should win)
            withValues(false, "true", ""),
            withValues(true, "false", "true"),

            // Test property ignored in other sections
            withWrongSection("Foo", "true"),
            withWrongSection("JavaOptions", "true")
        );
    }

    private static NoRerunConfig withValue(boolean expectedNorestart,
            String norestartValue) {
        return new NoRerunConfig(null, new NoRerunSectionConfig("Application",
                norestartValue, null), expectedNorestart);
    }

    private static NoRerunConfig withValues(boolean expectedNorestart,
            String firstNorestartValue, String secondNorestartValue) {
        return new NoRerunConfig(null, new NoRerunSectionConfig("Application",
                firstNorestartValue, secondNorestartValue), expectedNorestart);
    }

    private static NoRerunConfig withWrongSection(String sectionName,
            String norestartValue) {
        return new NoRerunConfig(new NoRerunSectionConfig(sectionName,
                norestartValue, null), null, false);
    }
}
