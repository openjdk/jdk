/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.TKit.TextStreamVerifier;

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
 * @compile -Xlint:all -Werror InstallDirTest.java
 * @requires (jpackage.test.SQETest != null)
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=InstallDirTest.testCommon
 */

/*
 * @test
 * @summary jpackage with --install-dir
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror InstallDirTest.java
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=InstallDirTest
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
        new PackageTest().configureHelloApp()
        .setExpectedExitCode(1)
        .forTypes(PackageType.LINUX)
        .addInitializer(cmd -> {
            cmd.addArguments("--install-dir", installDir);
            cmd.saveConsoleOutput(true);
        })
        .addBundleVerifier((cmd, result) -> {
            cmd.validateOutput(JPackageStringBundle.MAIN.cannedFormattedString("error.invalid-install-dir"));
        })
        .run();
    }

    record DmgTestSpec(Path installDir, boolean runtimeInstaller) {

        DmgTestSpec {
            Objects.requireNonNull(installDir);
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            Builder acceptedInstallDir(String v) {
                installDir = Path.of(v);
                return this;
            }

            Builder runtimeInstaller() {
                runtimeInstaller = true;
                return this;
            }

            DmgTestSpec create() {
                return new DmgTestSpec(installDir, runtimeInstaller);
            }

            private Path installDir;
            private boolean runtimeInstaller;
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(installDir);
            if (runtimeInstaller) {
                sb.append(", runtime");
            }
            return sb.toString();
        }

        void run() {
            final var test = new PackageTest().forTypes(PackageType.MAC_DMG).ignoreBundleOutputDir();
            if (runtimeInstaller) {
                test.addInitializer(cmd -> {
                    cmd.removeArgumentWithValue("--input");
                });
            } else {
                test.configureHelloApp();
            }

            test.addInitializer(JPackageCommand::setFakeRuntime).addInitializer(cmd -> {
                cmd.addArguments("--install-dir", installDir);
            }).run(Action.CREATE_AND_UNPACK);
        }
    }

    @Test(ifOS = OperatingSystem.MACOS)
    @ParameterSupplier
    public static void testDmg(DmgTestSpec testSpec) {
        testSpec.run();
    }

    public static List<Object[]> testDmg() {
        return Stream.of(
                DmgTestSpec.build().acceptedInstallDir("/foo"),
                DmgTestSpec.build().acceptedInstallDir("/foo/bar"),
                DmgTestSpec.build().acceptedInstallDir("/foo").runtimeInstaller(),
                DmgTestSpec.build().acceptedInstallDir("/foo/bar").runtimeInstaller(),

                DmgTestSpec.build().acceptedInstallDir("/Library/Java/JavaVirtualMachines"),
                DmgTestSpec.build().acceptedInstallDir("/Applications").runtimeInstaller(),

                DmgTestSpec.build().acceptedInstallDir("/Applications"),
                DmgTestSpec.build().acceptedInstallDir("/Applications/foo/bar/buz"),

                DmgTestSpec.build().runtimeInstaller().acceptedInstallDir("/Library/Java/JavaVirtualMachines"),
                DmgTestSpec.build().runtimeInstaller().acceptedInstallDir("/Library/Java/JavaVirtualMachines/foo/bar/buz")
        ).map(DmgTestSpec.Builder::create).map(testSpec -> {
            return new Object[] { testSpec };
        }).toList();
    }
}
