/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;

/**
 * Test --install-dir parameter. Output of the test should be
 * commoninstalldirtest*.* package bundle. The output package should provide the
 * same functionality as the default package but install test application in
 * specified directory.
 *
 * Linux:
 *
 * Application should be installed in /opt/jpackage/commoninstalldirtest folder.
 *
 * Mac:
 *
 * Application should be installed in /Applications/jpackage/commoninstalldirtest.app
 * folder.
 *
 * Windows:
 *
 * Application should be installed in %ProgramFiles%/TestVendor/InstallDirTest1234
 * folder.
 */

/*
 * @test
 * @summary jpackage with --install-dir
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile InstallDirTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=InstallDirTest.testCommon
 */

/*
 * @test
 * @summary jpackage with --install-dir
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile InstallDirTest.java
 * @requires (os.family == "linux")
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=InstallDirTest.testLinuxInvalid
 */
public class InstallDirTest {

    @Test
    @Parameter(value = "TestVendor\\InstallDirTest1234", ifOS = OperatingSystem.WINDOWS)
    @Parameter(value = "/opt/jpackage", ifOS = OperatingSystem.LINUX)
    @Parameter(value = "/Applications/jpackage", ifOS = OperatingSystem.MACOS)
    public static void testCommon(Path installDir) {
        new PackageTest().configureHelloApp()
        .addInitializer(cmd -> {
            cmd.addArguments("--install-dir", installDir);
        }).run();
    }

    @Test(ifOS = OperatingSystem.LINUX)
    @Parameter("/")
    @Parameter(".")
    @Parameter("foo")
    @Parameter("/opt/foo/.././.")
    public static void testLinuxInvalid(String installDir) {
        testLinuxBad(installDir, "Invalid installation directory");
    }

    private static void testLinuxBad(String installDir,
            String errorMessageSubstring) {
        new PackageTest().configureHelloApp()
        .setExpectedExitCode(1)
        .forTypes(PackageType.LINUX)
        .addInitializer(cmd -> {
            cmd.addArguments("--install-dir", installDir);
            cmd.saveConsoleOutput(true);
        })
        .addBundleVerifier((cmd, result) -> {
            TKit.assertTextStream(errorMessageSubstring).apply(
                    result.getOutput().stream());
        })
        .run();
    }
}
