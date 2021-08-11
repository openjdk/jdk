/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.TKit;

/**
 * Tests values of environment variables altered by jpackage launcher.
 */

/*
 * @test
 * @summary Tests values of environment variables altered by jpackage launcher
 * @library ../helpers
 * @library /test/lib
 * @build AppLauncherEnvTest
 * @build jdk.jpackage.test.*
 * @build AppLauncherEnvTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppLauncherEnvTest
 */
public class AppLauncherEnvTest {

    @Test
    public static void test() throws Exception {
        final String testAddDirProp = "jpackage.test.AppDir";

        JPackageCommand cmd = JPackageCommand
                .helloAppImage(TEST_APP_JAVA + "*Hello")
                .addArguments("--java-options", "-D" + testAddDirProp
                        + "=$APPDIR");

        cmd.executeAndAssertImageCreated();

        final String envVarName = envVarName();

        final int attempts = 3;
        final int waitBetweenAttemptsSeconds = 5;
        List<String> output = new Executor()
                .saveOutput()
                .setExecutable(cmd.appLauncherPath().toAbsolutePath())
                .addArguments("--print-env-var=" + envVarName)
                .addArguments("--print-sys-prop=" + testAddDirProp)
                .addArguments("--print-sys-prop=" + "java.library.path")
                .executeAndRepeatUntilExitCode(0, attempts,
                        waitBetweenAttemptsSeconds).getOutput();

        BiFunction<Integer, String, String> getValue = (idx, name) -> {
            return  output.get(idx).substring((name + "=").length());
        };

        final String actualEnvVarValue = getValue.apply(0, envVarName);
        final String appDir = getValue.apply(1, testAddDirProp);

        final String expectedEnvVarValue = Optional.ofNullable(System.getenv(
                envVarName)).orElse("") + File.pathSeparator + appDir;

        TKit.assertEquals(expectedEnvVarValue, actualEnvVarValue, String.format(
                "Check value of %s env variable", envVarName));

        final String javaLibraryPath = getValue.apply(2, "java.library.path");
        TKit.assertTrue(
                List.of(javaLibraryPath.split(File.pathSeparator)).contains(
                        appDir), String.format(
                        "Check java.library.path system property [%s] contains app dir [%s]",
                        javaLibraryPath, appDir));
    }

    private static String envVarName() {
        if (TKit.isLinux()) {
            return "LD_LIBRARY_PATH";
        } else if (TKit.isWindows()) {
            return "PATH";
        } else if (TKit.isOSX()) {
            return "DYLD_LIBRARY_PATH";
        } else {
            throw new IllegalStateException();
        }
    }

    private static final Path TEST_APP_JAVA = TKit.TEST_SRC_ROOT.resolve(
            "apps/PrintEnv.java");
}
