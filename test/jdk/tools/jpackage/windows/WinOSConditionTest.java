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

import java.io.IOException;
import java.nio.file.Files;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage test that installer blocks on Windows of older version
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror WinOSConditionTest.java
 * @requires (os.family == "windows")
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinOSConditionTest
 */
public class WinOSConditionTest {

    @Test
    public static void test() throws IOException {
        // Use custom always failing condition. Installation is expected to fail.
        // This way the test covers:
        //  1. If jpackage picks custom OS version condition from the resource directory;
        //  2. If the installer created by jpackage uses OS version condition.
        new PackageTest().ignoreBundleOutputDir()
        .forTypes(PackageType.WINDOWS)
        .configureHelloApp()
        .addInitializer(JPackageCommand::setFakeRuntime)
        .addInitializer(cmd -> {
            final var resourceDir = TKit.createTempDirectory("resource-dir");
            Files.copy(TKit.TEST_SRC_ROOT.resolve("resources/fail-os-condition.wxf"), resourceDir.resolve("os-condition.wxf"));
            // Create a per-user installer to let user without admin privileges install it.
            cmd.addArguments("--win-per-user-install",
                    "--resource-dir", resourceDir.toString()).setFakeRuntime();
        })
        .addUninstallVerifier(cmd -> {
            // MSI error code 1603 is generic.
            // Dig into the last msi log file for log messages specific to failed condition.
            try (final var lines = cmd.winMsiLogFileContents().orElseThrow()) {
                TKit.assertTextStream("Doing action: LaunchConditions").predicate(String::endsWith)
                    .andThen(TKit.assertTextStream("Not supported on this version of Windows").predicate(String::endsWith)).apply(lines);
            }
        })
        .createMsiLog(true)
        .setExpectedInstallExitCode(1603)
        // Create, try install the package (installation should fail) and verify it is not installed.
        .run(Action.CREATE, Action.INSTALL, Action.VERIFY_UNINSTALL);
    }
}
