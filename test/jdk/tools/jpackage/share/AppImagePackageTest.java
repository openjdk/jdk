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
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.Annotations.Test;

/**
 * Test --app-image parameter. The output installer should provide the same
 * functionality as the default installer (see description of the default
 * installer in SimplePackageTest.java)
 */

/*
 * @test
 * @summary jpackage with --app-image
 * @key jpackagePlatformPackage
 * @library /test/jdk/tools/jpackage/helpers
 * @requires (jpackage.test.SQETest == null)
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror AppImagePackageTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppImagePackageTest
 */
public class AppImagePackageTest {

    @Test
    public static void test() {
        Path appimageOutput = TKit.workDir().resolve("appimage");

        JPackageCommand appImageCmd = JPackageCommand.helloAppImage()
                .setArgumentValue("--dest", appimageOutput);

        new PackageTest()
        .addRunOnceInitializer(() -> appImageCmd.execute())
        .addInitializer(cmd -> {
            cmd.addArguments("--app-image", appImageCmd.outputBundle());
            cmd.removeArgumentWithValue("--input");
        }).addBundleDesktopIntegrationVerifier(false).run();
    }

    @Test
    @Parameter("true")
    @Parameter("false")
    public static void testEmpty(boolean withIcon) throws IOException {
        final String name = "EmptyAppImagePackageTest";
        final String imageName = name + (TKit.isOSX() ? ".app" : "");
        Path appImageDir = TKit.createTempDirectory("appimage").resolve(imageName);

        Files.createDirectories(appImageDir.resolve("bin"));
        Path libDir = Files.createDirectories(appImageDir.resolve("lib"));
        TKit.createTextFile(libDir.resolve("README"),
                List.of("This is some arbitrary text for the README file\n"));

        new PackageTest()
        .addInitializer(cmd -> {
            cmd.addArguments("--app-image", appImageDir);
            if (withIcon) {
                cmd.addArguments("--icon", iconPath("icon"));
            }
            cmd.removeArgumentWithValue("--input");
            new AppImageFile("EmptyAppImagePackageTest", "Hello").save(appImageDir);

            // on mac, with --app-image and without --mac-package-identifier,
            // will try to infer it from the image, so foreign image needs it.
            if (TKit.isOSX()) {
                cmd.addArguments("--mac-package-identifier", name);
            }
        })
        // On macOS we always signing app image and signing will fail, since
        // test produces invalid app bundle.
        .setExpectedExitCode(TKit.isOSX() ? 1 : 0)
        .run(Action.CREATE, Action.UNPACK);
        // default: {CREATE, UNPACK, VERIFY}, but we can't verify foreign image
    }

    @Test
    public static void testBadAppImage() throws IOException {
        Path appImageDir = TKit.createTempDirectory("appimage");
        Files.createFile(appImageDir.resolve("foo"));
        configureBadAppImage(appImageDir).addInitializer(cmd -> {
            cmd.removeArgumentWithValue("--name");
        }).run(Action.CREATE);
    }

    @Test
    public static void testBadAppImage2() throws IOException {
        Path appImageDir = TKit.createTempDirectory("appimage");
        Files.createFile(appImageDir.resolve("foo"));
        configureBadAppImage(appImageDir).run(Action.CREATE);
    }

    @Test
    public static void testBadAppImage3() throws IOException {
        Path appImageDir = TKit.createTempDirectory("appimage");

        JPackageCommand appImageCmd = JPackageCommand.helloAppImage().
                setFakeRuntime().setArgumentValue("--dest", appImageDir);

        configureBadAppImage(appImageCmd.outputBundle()).addRunOnceInitializer(() -> {
            appImageCmd.execute();
            Files.delete(AppImageFile.getPathInAppImage(appImageCmd.outputBundle()));
        }).run(Action.CREATE);
    }

    @Test
    public static void testBadAppImageFile() throws IOException {
        final var appImageRoot = TKit.createTempDirectory("appimage");

        final var appImageCmd = JPackageCommand.helloAppImage().
                setFakeRuntime().setArgumentValue("--dest", appImageRoot);

        final var appImageDir = appImageCmd.outputBundle();

        final var expectedError = JPackageStringBundle.MAIN.cannedFormattedString(
                "error.invalid-app-image", appImageDir, AppImageFile.getPathInAppImage(appImageDir));

        configureBadAppImage(appImageDir, expectedError).addRunOnceInitializer(() -> {
            appImageCmd.execute();
            XmlUtils.createXml(AppImageFile.getPathInAppImage(appImageDir), xml -> {
                xml.writeStartElement("jpackage-state");
                xml.writeEndElement();
            });
        }).run(Action.CREATE);
    }

    private static PackageTest configureBadAppImage(Path appImageDir) {
        return configureBadAppImage(appImageDir,
                JPackageStringBundle.MAIN.cannedFormattedString("error.foreign-app-image", appImageDir));
    }

    private static PackageTest configureBadAppImage(Path appImageDir, CannedFormattedString expectedError) {
        return new PackageTest().addInitializer(cmd -> {
            cmd.addArguments("--app-image", appImageDir);
            cmd.removeArgumentWithValue("--input");
            cmd.ignoreDefaultVerbose(true); // no "--verbose" option
            cmd.validateOutput(expectedError);
        }).setExpectedExitCode(1);
    }

    private static Path iconPath(String name) {
        return TKit.TEST_SRC_ROOT.resolve(Path.of("resources", name
                + TKit.ICON_SUFFIX));
    }

}
