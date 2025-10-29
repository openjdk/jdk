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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageCommand.StandardAssert;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

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

    /**
     * Create a native bundle from a valid predefined app image produced by jpackage.
     */
    @Test
    public static void test() {

        var appImageCmd = JPackageCommand.helloAppImage()
                .setArgumentValue("--dest", TKit.createTempDirectory("appimage"));

        new PackageTest()
        .addRunOnceInitializer(appImageCmd::execute)
        .addInitializer(cmd -> {
            cmd.addArguments("--app-image", appImageCmd.outputBundle());
            cmd.removeArgumentWithValue("--input");
        }).addBundleDesktopIntegrationVerifier(false).run();
    }

    /**
     * Create a native bundle from a predefined app image not produced by jpackage
     * but having a valid ".jpackage.xml" file.
     *
     * @param withIcon {@code true} if jpackage command line should have "--icon"
     *                 option
     */
    @Test
    @Parameter("true")
    @Parameter("false")
    public static void testEmpty(boolean withIcon) throws IOException {

        var appImageCmd = JPackageCommand.helloAppImage()
                .setFakeRuntime()
                .setArgumentValue("--name", "EmptyAppImagePackageTest")
                .setArgumentValue("--dest", TKit.createTempDirectory("appimage"));

        new PackageTest()
        .addRunOnceInitializer(appImageCmd::execute)
        .addRunOnceInitializer(() -> {
            var layout = appImageCmd.appLayout();
            if (!TKit.isOSX()) {
                // Delete the launcher if not on macOS.
                // On macOS, deleting the launcher will render the app bundle invalid.
                TKit.deleteIfExists(appImageCmd.appLauncherPath());
            }
            // Delete the runtime.
            TKit.deleteDirectoryRecursive(layout.runtimeDirectory());
            // Delete the "app" dir.
            TKit.deleteDirectoryRecursive(layout.appDirectory());

            new AppImageFile(appImageCmd.name(), "PhonyMainClass").save(appImageCmd.outputBundle());
            var appImageDir = appImageCmd.outputBundle();

            TKit.trace(String.format("Files in [%s] app image:", appImageDir));
            try (var files = Files.walk(appImageDir)) {
                files.sequential()
                        .filter(Predicate.isEqual(appImageDir).negate())
                        .map(path -> String.format("[%s]", appImageDir.relativize(path)))
                        .forEachOrdered(TKit::trace);
                TKit.trace("Done");
            }
        })
        .addInitializer(cmd -> {
            cmd.addArguments("--app-image", appImageCmd.outputBundle());
            if (withIcon) {
                cmd.addArguments("--icon", iconPath("icon"));
            }
            cmd.removeArgumentWithValue("--input");

            cmd.excludeStandardAsserts(
                    StandardAssert.MAIN_JAR_FILE,
                    StandardAssert.MAIN_LAUNCHER_FILES,
                    StandardAssert.MAC_BUNDLE_STRUCTURE,
                    StandardAssert.RUNTIME_DIRECTORY);
        })
        .run(Action.CREATE_AND_UNPACK);
    }

    /**
     * Bad predefined app image - not an output of jpackage.
     * jpackage command using the bad predefined app image doesn't have "--name" option.
     */
    @Test
    public static void testBadAppImage() throws IOException {
        Path appImageDir = TKit.createTempDirectory("appimage");
        Files.createFile(appImageDir.resolve("foo"));
        configureBadAppImage(appImageDir).addInitializer(cmd -> {
            cmd.removeArgumentWithValue("--name");
        }).run(Action.CREATE);
    }

    /**
     * Bad predefined app image - not an output of jpackage.
     */
    @Test
    public static void testBadAppImage2() throws IOException {
        Path appImageDir = TKit.createTempDirectory("appimage");
        Files.createFile(appImageDir.resolve("foo"));
        configureBadAppImage(appImageDir).run(Action.CREATE);
    }

    /**
     * Bad predefined app image - valid app image missing ".jpackage.xml" file.
     */
    @Test
    public static void testBadAppImage3() {
        Path appImageDir = TKit.createTempDirectory("appimage");

        JPackageCommand appImageCmd = JPackageCommand.helloAppImage().
                setFakeRuntime().setArgumentValue("--dest", appImageDir);

        configureBadAppImage(appImageCmd.outputBundle()).addRunOnceInitializer(() -> {
            appImageCmd.execute();
            Files.delete(AppImageFile.getPathInAppImage(appImageCmd.outputBundle()));
        }).run(Action.CREATE);
    }

    /**
     * Bad predefined app image - valid app image with invalid ".jpackage.xml" file.
     */
    @Test
    public static void testBadAppImageFile() {
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
