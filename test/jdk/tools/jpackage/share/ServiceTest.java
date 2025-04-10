/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.LauncherAsServiceVerifier;
import static jdk.jpackage.test.PackageType.MAC_DMG;
import static jdk.jpackage.test.PackageType.WINDOWS;
import jdk.jpackage.test.RunnablePackageTest;
import jdk.jpackage.test.TKit;

/**
 * Launcher as service packaging test. Output of the test should be
 * servicetest*.* and updateservicetest*.* package bundles.
 */

/*
 * @test
 * @summary Launcher as service packaging test
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @key jpackagePlatformPackage
 * @compile -Xlint:all -Werror ServiceTest.java
 * @run main/othervm/timeout=360 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=ServiceTest
 */
public class ServiceTest {

    public ServiceTest() {
        if (TKit.isWindows()) {
            final String propName = "jpackage.test.ServiceTest.service-installer";
            String serviceInstallerExec = System.getProperty(propName);
            if (serviceInstallerExec == null) {
                if (Stream.of(RunnablePackageTest.Action.CREATE,
                        RunnablePackageTest.Action.INSTALL).allMatch(
                                RunnablePackageTest::hasAction)) {
                    TKit.throwSkippedException(String.format(
                            "%s system property not set", propName));
                } else {
                    // Use cmd.exe as a stub as the output packages will not be
                    // created and installed in the test run
                    serviceInstallerExec = Optional.ofNullable(System.getenv(
                            "COMSPEC")).orElseGet(() -> {
                                return JavaTool.JAVA.getPath().toString();
                            });
                    TKit.trace(
                            String.format("Using [%s] as a service installer",
                                    serviceInstallerExec));
                }
            }

            winServiceInstaller = Path.of(serviceInstallerExec);

        } else {
            winServiceInstaller = null;
        }
    }

    @Test
    public void test() throws Throwable {
        var testInitializer = createTestInitializer();
        var pkg = createPackageTest().addHelloAppInitializer("com.foo.ServiceTest");
        LauncherAsServiceVerifier.build().setExpectedValue("A1").applyTo(pkg);
        testInitializer.applyTo(pkg);
        pkg.run();
    }

    @Test
    public void testUpdate() throws Throwable {
        var testInitializer = createTestInitializer().setUpgradeCode(
                "4050AD4D-D6CC-452A-9CB0-58E5FA8C410F");

        // Package name will be used as package ID on macOS. Keep it the same for
        // both packages to allow update installation.
        final String packageName = "com.bar";

        var pkg = createPackageTest()
                .addHelloAppInitializer(String.join(".", packageName, "Hello"))
                .disablePackageUninstaller();
        testInitializer.applyTo(pkg);

        LauncherAsServiceVerifier.build().setExpectedValue("Default").applyTo(pkg);

        var pkg2 = createPackageTest()
                .addHelloAppInitializer(String.join(".", packageName, "Bye"))
                .addInitializer(cmd -> {
                    cmd.addArguments("--app-version", "2.0");
                });
        testInitializer.applyTo(pkg2);

        var builder = LauncherAsServiceVerifier.build()
                .setLauncherName("foo")
                .setAppOutputFileName("foo-launcher-as-service.txt");

        builder.setExpectedValue("Foo").applyTo(pkg);

        builder.setExpectedValue("Foo2").applyTo(pkg2);

        builder.setExpectedValue("Bar")
                .setLauncherName("bar")
                .setAppOutputFileName("bar-launcher-as-service.txt")
                .applyTo(pkg2);

        new PackageTest.Group(pkg, pkg2).run();
    }

    private final class TestInitializer {

        TestInitializer setUpgradeCode(String v) {
            upgradeCode = v;
            return this;
        }

        void applyTo(PackageTest test) throws IOException {
            if (winServiceInstaller != null) {
                var resourceDir = TKit.createTempDirectory("resource-dir");
                Files.copy(winServiceInstaller, resourceDir.resolve(
                        "service-installer.exe"));

                test.forTypes(WINDOWS, () -> test.addInitializer(cmd -> {
                    cmd.addArguments("--resource-dir", resourceDir);
                }));
            }

            if (upgradeCode != null) {
                test.forTypes(WINDOWS, () -> test.addInitializer(cmd -> {
                    cmd.addArguments("--win-upgrade-uuid", upgradeCode);
                }));
            }
        }

        private String upgradeCode;
    }

    private TestInitializer createTestInitializer() {
        return new TestInitializer();
    }

    private static PackageTest createPackageTest() {
        return new PackageTest()
                .excludeTypes(MAC_DMG) // DMG not supported
                .addInitializer(JPackageCommand::setInputToEmptyDirectory);
    }

    private final Path winServiceInstaller;
}
