/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.RunnablePackageTest.Action.CREATE_AND_UNPACK;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.ApplicationLayout;
import jdk.jpackage.test.ConfigurationTarget;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageFile;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

/*
 * @test
 * @summary Test jpackage command line with overlapping input and output paths
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror InOutPathTest.java
 * @run main/othervm/timeout=2880 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=InOutPathTest
 */
public final class InOutPathTest {

    @Parameters
    public static Collection<?> input() {
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
    public void test() {
        runTest(packageTypes, configure);
    }

    private static Envelope wrap(ThrowingConsumer<JPackageCommand, ? extends Exception> v, String label) {
        return new Envelope(ThrowingConsumer.toConsumer(v), label);
    }

    private static void runTest(Set<PackageType> packageTypes,
            Consumer<JPackageCommand> configure) {

        ConfigurationTarget cfg;
        if (packageTypes.contains(PackageType.IMAGE)) {
            cfg = new ConfigurationTarget(
                    JPackageCommand.helloAppImage(JAR_PATH.toString() + ":"));
        } else {
            cfg = new ConfigurationTarget(new PackageTest()
                    .forTypes(packageTypes)
                    .configureHelloApp(JAR_PATH.toString() + ":"));
        }

        var verifier = new AppDirContentVerifier();

        cfg.addInitializer(cmd -> {
            // Make sure the input directory is empty in every test run.
            // This is needed because jpackage output directories in this test
            // are subdirectories of the input directory.
            cmd.setInputToEmptyDirectory();
            configure.accept(cmd);
            if (cmd.hasArgument("--temp") && cmd.isImagePackageType()) {
                // Request to build app image with user supplied temp directory,
                // ignore external runtime if any to make use of the temp directory
                // for runtime generation.
                cmd.ignoreDefaultRuntime(true);
            } else {
                cmd.setFakeRuntime();
            }
        })
        .addInitializer(JPackageCommand::executePrerequisiteActions)
        .addInitializer(verifier::captureInputDir);

        cfg.cmd().ifPresent(JPackageCommand::executeAndAssertHelloAppImageCreated);

        cfg.addInstallVerifier(verifier::verify);

        cfg.test().ifPresent(pkg -> {
            pkg.run(CREATE_AND_UNPACK);
        });
    }

    private static void outputDirInInputDir(JPackageCommand cmd) throws
            IOException {
        // Set output dir as a subdir of input dir
        Path outputDir = cmd.inputDir().resolve("out");
        Files.createDirectories(outputDir);
        cmd.setArgumentValue("--dest", outputDir);
    }

    private static void outputDirSameAsInputDir(JPackageCommand cmd) {
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
        Files.createDirectories(appContentFile.getParent());
        TKit.createTextFile(appContentFile, List.of("Hello Duke!"));
        cmd.addArguments(argName, appContentFile.getParent());
    }

    private record Envelope(Consumer<JPackageCommand> value, String label) {
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

    private static final class AppDirContentVerifier {

        void captureInputDir(JPackageCommand cmd) {
            var root = Path.of(cmd.getArgumentValue("--input"));
            try (var walk = Files.walk(root)) {
                inputDirFiles = walk.map(root::relativize).toList();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        void verify(JPackageCommand cmd) {
            var expectedContent = new HashSet<>(inputDirFiles);

            expectedContent.add(Path.of(cmd.name() + ".cfg"));
            if (cmd.isImagePackageType()) {
                expectedContent.add(AppImageFile.getPathInAppImage(Path.of("")).getFileName());
            } else {
                expectedContent.add(PackageFile.getPathInAppImage(Path.of("")).getFileName());
            }

            final var rootDir = cmd.isImagePackageType() ? cmd.outputBundle() : cmd.pathToUnpackedPackageFile(
                    cmd.appInstallationDirectory());
            final var appDir = ApplicationLayout.platformAppImage().resolveAt(rootDir).appDirectory();

            try (var walk = Files.walk(appDir)) {
                var unexpectedFiles = walk
                        .map(appDir::relativize)
                        .filter(Predicate.not(expectedContent::contains))
                        .map(Path::toString)
                        .toList();
                TKit.assertStringListEquals(List.of(), unexpectedFiles,
                        "Check there are no unexpected files in `app` folder");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private Collection<Path> inputDirFiles;
    }

    private final Set<PackageType> packageTypes;
    private final Consumer<JPackageCommand> configure;

    // Placing jar file in the "Resources" subdir of the input directory would allow
    // to use the input directory with `--app-content` on OSX.
    // For other platforms it doesn't matter. Keep it the same across
    // all platforms for simplicity.
    private static final Path JAR_PATH = Path.of("Resources/duke.jar");
}
