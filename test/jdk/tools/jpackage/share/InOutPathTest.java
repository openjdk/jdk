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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.AppImageFile;
import jdk.jpackage.internal.ApplicationLayout;
import jdk.jpackage.internal.PackageFile;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageCommand.AppLayoutAssert;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import static jdk.jpackage.test.RunnablePackageTest.Action.CREATE_AND_UNPACK;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Test jpackage command line with overlapping input and output paths
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile InOutPathTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=InOutPathTest
 */
public final class InOutPathTest {

    @Parameters
    public static Collection input() {
        List<Object[]> data = new ArrayList<>();

        for (var packageTypeAlias : PackageTypeAlias.values()) {
            data.addAll(List.of(new Object[][]{
                {packageTypeAlias, wrap(InOutPathTest::outputDirInInputDir, "--dest in --input")},
                {packageTypeAlias, wrap(InOutPathTest::outputDirSameAsInputDir, "--dest same as --input")},
                {packageTypeAlias, wrap(InOutPathTest::tempDirInInputDir, "--temp in --input")},
                {packageTypeAlias, wrap(cmd -> {
                    outputDirInInputDir(cmd);
                    tempDirInInputDir(cmd);
                }, "--dest and --temp in --input")},
            }));
            data.addAll(additionalContentInput(packageTypeAlias, "--app-content"));
        }

        return data;
    }

    @Parameters(ifNotOS = OperatingSystem.MACOS)
    public static Collection<Object[]> appContentInputOther() {
        return List.of(new Object[][]{
            {PackageTypeAlias.IMAGE, wrap(cmd -> {
                additionalContent(cmd, "--app-content", cmd.outputBundle());
            }, "--app-content same as output bundle")},
        });
    }

    @Parameters(ifOS = OperatingSystem.MACOS)
    public static Collection<Object[]> appContentInputOSX() {
        var contentsFolder = "Contents/MacOS";
        return List.of(new Object[][]{
            {PackageTypeAlias.IMAGE, wrap(cmd -> {
                additionalContent(cmd, "--app-content", cmd.outputBundle().resolve(contentsFolder));
            }, String.format("--app-content same as the \"%s\" folder in the output bundle", contentsFolder))},
        });
    }

    @Parameters(ifOS = OperatingSystem.MACOS)
    public static Collection<Object[]> inputOSX() {
        return List.of(additionalContentInput(PackageType.MAC_DMG, "--mac-dmg-content").toArray(Object[][]::new));
    }

    private static List<Object[]> additionalContentInput(Object packageTypes, String argName) {
        List<Object[]> data = new ArrayList<>();

        data.addAll(List.of(new Object[][]{
            {packageTypes, wrap(cmd -> {
                additionalContent(cmd, argName, cmd.inputDir());
            }, argName + " same as --input")},
        }));

        if (!TKit.isOSX()) {
            data.addAll(List.of(new Object[][]{
                {packageTypes, wrap(cmd -> {
                    additionalContent(cmd, argName, cmd.inputDir().resolve("foo"));
                }, argName + " in --input")},
                {packageTypes, wrap(cmd -> {
                    additionalContent(cmd, argName, cmd.outputDir().resolve("bar"));
                }, argName + " in --dest")},
                {packageTypes, wrap(cmd -> {
                    additionalContent(cmd, argName, cmd.outputDir());
                }, argName + " same as --dest")},
                {packageTypes, wrap(cmd -> {
                    tempDirInInputDir(cmd);
                    var tempDir = cmd.getArgumentValue("--temp");
                    Files.createDirectories(Path.of(tempDir));
                    cmd.addArguments(argName, tempDir);
                }, argName + " as --temp; --temp in --input")},
            }));
        }

        return data;
    }

    public InOutPathTest(PackageTypeAlias packageTypeAlias, Envelope configure) {
        this(packageTypeAlias.packageTypes, configure);
    }

    public InOutPathTest(PackageType packageType, Envelope configure) {
        this(Set.of(packageType), configure);
    }

    public InOutPathTest(Set<PackageType> packageTypes, Envelope configure) {
        this.packageTypes = packageTypes;
        this.configure = configure.value;
    }

    @Test
    public void test() throws Throwable {
        runTest(packageTypes, configure);
    }

    private static Envelope wrap(ThrowingConsumer<JPackageCommand> v, String label) {
        return new Envelope(v, label);
    }

    private static boolean isAppImageValid(JPackageCommand cmd) {
        return !cmd.hasArgument("--app-content") && !cmd.hasArgument("--mac-dmg-content");
    }

    private static void runTest(Set<PackageType> packageTypes,
            ThrowingConsumer<JPackageCommand> configure) throws Throwable {
        ThrowingConsumer<JPackageCommand> configureWrapper = cmd -> {
            // Make sure the input directory is empty in every test run.
            // This is needed because jpackage output directories in this test
            // are subdirectories of the input directory.
            cmd.setInputToEmptyDirectory();
            configure.accept(cmd);
            if (cmd.hasArgument("--temp") && cmd.isImagePackageType()) {
                // Request to build app image wit user supplied temp directory,
                // ignore external runtime if any to make use of the temp directory
                // for runtime generation.
                cmd.ignoreDefaultRuntime(true);
            } else {
                cmd.setFakeRuntime();
            }

            if (!isAppImageValid(cmd)) {
                // Standard asserts for .jpackage.xml fail in messed up app image. Disable them.
                // Other standard asserts for app image contents should pass.
                cmd.excludeAppLayoutAsserts(AppLayoutAssert.APP_IMAGE_FILE);
            }
        };

        if (packageTypes.contains(PackageType.IMAGE)) {
            JPackageCommand cmd = JPackageCommand.helloAppImage(JAR_PATH.toString() + ":");
            configureWrapper.accept(cmd);
            cmd.executeAndAssertHelloAppImageCreated();
            if (isAppImageValid(cmd)) {
                verifyAppImage(cmd);
            }

            if (cmd.hasArgument("--app-content")) {
                // `--app-content` can be set to the app image directory which
                // should not exist before jpackage is executed:
                //  jpackage --name Foo --dest output --app-content output/Foo
                // Verify the directory exists after jpackage execution.
                // At least this will catch the case when the value of
                // `--app-content` option refers to a path unrelated to jpackage I/O.
                TKit.assertDirectoryExists(Path.of(cmd.getArgumentValue("--app-content")));
            }
        } else {
            new PackageTest()
                    .forTypes(packageTypes)
                    .configureHelloApp(JAR_PATH.toString() + ":")
                    .addInitializer(configureWrapper)
                    .addInstallVerifier(InOutPathTest::verifyAppImage)
                    .run(CREATE_AND_UNPACK);
        }
    }

    private static void outputDirInInputDir(JPackageCommand cmd) throws
            IOException {
        // Set output dir as a subdir of input dir
        Path outputDir = cmd.inputDir().resolve("out");
        TKit.createDirectories(outputDir);
        cmd.setArgumentValue("--dest", outputDir);
    }

    private static void outputDirSameAsInputDir(JPackageCommand cmd) throws
            IOException {
        // Set output dir the same as the input dir
        cmd.setArgumentValue("--dest", cmd.inputDir());
    }

    private static void tempDirInInputDir(JPackageCommand cmd) {
        // Set temp dir as a subdir of input dir
        Path tmpDir = cmd.inputDir().resolve("tmp");
        cmd.setArgumentValue("--temp", tmpDir);
    }

    private static void additionalContent(JPackageCommand cmd,
            String argName, Path base) throws IOException {
        Path appContentFile = base.resolve(base.toString().replaceAll("[\\\\/]",
                "-") + "-foo.txt");
        TKit.createDirectories(appContentFile.getParent());
        TKit.createTextFile(appContentFile, List.of("Hello Duke!"));
        cmd.addArguments(argName, appContentFile.getParent());
    }

    private static void verifyAppImage(JPackageCommand cmd) throws IOException {
        if (!isAppImageValid(cmd)) {
            // Don't verify the contents of app image as it is invalid.
            // jpackage exited without getting stuck in infinite spiral.
            // No more expectations from the tool for the give arguments.
            return;
        }

        final Path rootDir = cmd.isImagePackageType() ? cmd.outputBundle() : cmd.pathToUnpackedPackageFile(
                cmd.appInstallationDirectory());
        final Path appDir = ApplicationLayout.platformAppImage().resolveAt(
                rootDir).appDirectory();

        final var knownFiles = Set.of(
                JAR_PATH.getName(0).toString(),
                PackageFile.getPathInAppImage(Path.of("")).getFileName().toString(),
                AppImageFile.getPathInAppImage(Path.of("")).getFileName().toString(),
                cmd.name() + ".cfg"
        );

        TKit.assertFileExists(appDir.resolve(JAR_PATH));

        try (Stream<Path> actualFilesStream = Files.list(appDir)) {
            var unexpectedFiles = actualFilesStream.map(path -> {
                return path.getFileName().toString();
            }).filter(Predicate.not(knownFiles::contains)).toList();
            TKit.assertStringListEquals(List.of(), unexpectedFiles,
                    "Check there are no unexpected files in `app` folder");
        }
    }

    private static final record Envelope(ThrowingConsumer<JPackageCommand> value, String label) {
        @Override
        public String toString() {
            // Will produce the same test description for the same label every
            // time it's executed.
            // The test runner will keep the same test output directory.
            return label;
        }
    }

    private enum PackageTypeAlias {
        IMAGE(Set.of(PackageType.IMAGE)),
        NATIVE(PackageType.NATIVE),
        ;

        PackageTypeAlias(Set<PackageType> packageTypes) {
            this.packageTypes = packageTypes;
        }

        private final Set<PackageType> packageTypes;
    }

    private final Set<PackageType> packageTypes;
    private final ThrowingConsumer<JPackageCommand> configure;

    // Placing jar file in the "Resources" subdir of the input directory would allow
    // to use the input directory with `--app-content` on OSX.
    // For other platforms it doesn't matter. Keep it the same across
    // all platforms for simplicity.
    private static final Path JAR_PATH = Path.of("Resources/duke.jar");
}
