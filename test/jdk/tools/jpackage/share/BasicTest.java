/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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


import static jdk.jpackage.test.RunnablePackageTest.Action.CREATE;
import static jdk.jpackage.test.RunnablePackageTest.Action.CREATE_AND_UNPACK;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.ConfigurationTarget;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageOutputValidator;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;
import jdk.tools.jlink.internal.LinkableRuntimeImage;

/*
 * @test
 * @summary jpackage basic testing
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror BasicTest.java
 * @run main/othervm/timeout=2880 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=BasicTest
 */

public final class BasicTest {

    public static Collection<?> addModulesParams() {
        List<Object[][]> params = new ArrayList<>();
        params.add(new Object[][] { new String[] { "--add-modules", "ALL-DEFAULT"  } });
        params.add(new Object[][] { new String[] { "--add-modules", "java.desktop" } });
        params.add(new Object[][] { new String[] { "--add-modules", "java.desktop,jdk.jartool" } });
        params.add(new Object[][] { new String[] { "--add-modules", "java.desktop", "--add-modules", "jdk.jartool" } });
        if (isAllModulePathCapable()) {
            final Path jmods = Path.of(System.getProperty("java.home"), "jmods");
            params.add(new Object[][] { new String[] { "--add-modules", "ALL-MODULE-PATH",
                                                       // Since JDK-8345259 ALL-MODULE-PATH requires --module-path arg
                                                       "--module-path", jmods.toString() } });
        }
        return Collections.unmodifiableList(params);
    }

    private static boolean isAllModulePathCapable() {
        Path jmods = Path.of(System.getProperty("java.home"), "jmods");
        boolean noJmods = Files.notExists(jmods);
        if (LinkableRuntimeImage.isLinkableRuntime() && noJmods) {
           TKit.trace("ALL-MODULE-PATH test skipped for linkable run-time image");
           return false;
        }
        return true;
    }

    @Test
    public void testNoArgs() {
        List<String> output =
                getJPackageToolProvider().executeAndGetOutput();
        TKit.assertStringListEquals(List.of("Usage: jpackage <options>",
                "Use jpackage --help (or -h) for a list of possible options"),
                output, "Check jpackage output");
    }

    @Test
    public void testJpackageProps() {
        String appVersion = "3.0";
        JPackageCommand cmd = JPackageCommand.helloAppImage(
                JavaAppDesc.parse("Hello"))
                // Disable default logic adding `--verbose` option
                // to jpackage command line.
                .ignoreDefaultVerbose(true)
                .saveConsoleOutput(true)
                .addArguments("--app-version", appVersion, "--arguments",
                    "jpackage.app-version jpackage.app-path")
                .ignoreFakeRuntime();

        cmd.executeAndAssertImageCreated();

        List<String> output = HelloApp.executeLauncher(cmd).getOutput();

        TKit.assertTextStream("jpackage.app-version=" + appVersion).apply(output);
        TKit.assertTextStream("jpackage.app-path=").apply(output);
    }

    @Test
    public void testVersion() {
        List<String> output =
                getJPackageToolProvider()
                        .addArgument("--version")
                        .executeAndGetOutput();
        TKit.assertStringListEquals(List.of(System.getProperty("java.version")),
                output, "Check jpackage output");
    }

    @Test
    public void testHelp() {
        List<String> hOutput = getJPackageToolProvider()
                .addArgument("-h").executeAndGetOutput();
        List<String> helpOutput = getJPackageToolProvider()
                .addArgument("--help").executeAndGetOutput();

        TKit.assertStringListEquals(hOutput, helpOutput,
                "Check -h and --help parameters produce the same output");

        final String windowsPrefix = "--win-";
        final String linuxPrefix = "--linux-";
        final String osxPrefix = "--mac-";

        final String expectedPrefix;
        final List<String> unexpectedPrefixes;

        if (TKit.isWindows()) {
            expectedPrefix = windowsPrefix;
            unexpectedPrefixes = List.of(osxPrefix, linuxPrefix);
        } else if (TKit.isLinux()) {
            expectedPrefix = linuxPrefix;
            unexpectedPrefixes = List.of(windowsPrefix, osxPrefix);
        } else if (TKit.isOSX()) {
            expectedPrefix = osxPrefix;
            unexpectedPrefixes = List.of(linuxPrefix,  windowsPrefix);
        } else {
            throw TKit.throwUnknownPlatformError();
        }

        Function<String, Predicate<String>> createPattern = (prefix) -> {
            return Pattern.compile("^  " + prefix).asPredicate();
        };

        Function<List<String>, Long> countStrings = (prefixes) -> {
            return hOutput.stream().filter(
                    prefixes.stream().map(createPattern).reduce(x -> false,
                            Predicate::or)).peek(TKit::trace).count();
        };

        TKit.trace("Check parameters in help text");
        TKit.assertNotEquals(0, countStrings.apply(List.of(expectedPrefix)).longValue(),
                "Check help text contains platform specific parameters");
        TKit.assertEquals(0, countStrings.apply(unexpectedPrefixes).longValue(),
                "Check help text doesn't contain unexpected parameters");
    }

    @Test
    @Parameter("false")
    @Parameter("true")
    public void testQuiet(boolean appImage) {

        ConfigurationTarget target;
        if (appImage) {
            target = new ConfigurationTarget(JPackageCommand.helloAppImage());
        } else {
            target = new ConfigurationTarget(new PackageTest().configureHelloApp());
        }

        target.addInitializer(cmd -> {
            // Disable the default logic adding `--verbose` option to jpackage command line.
            cmd.ignoreDefaultVerbose(true)
            .useToolProvider(true)
            .saveConsoleOutput(true)
            .setFakeRuntime();
        });

        Consumer<Executor.Result> asserter = result -> {
            TKit.assertStringListEquals(List.of(), result.getOutput(), "Check output is empty");
        };

        target.cmd().map(JPackageCommand::execute).ifPresent(asserter);
        target.test().ifPresent(test -> {
            test.addBundleVerifier((_, result) -> {
                asserter.accept(result);
            }).run(CREATE);
        });
    }

    @Test
    @Parameter("false")
    @Parameter("true")
    public void testVerbose(boolean appImage) {

        ConfigurationTarget target;
        if (appImage) {
            target = new ConfigurationTarget(JPackageCommand.helloAppImage());
        } else {
            target = new ConfigurationTarget(new PackageTest().configureHelloApp());
        }

        target.addInitializer(cmd -> {
            // Disable the default logic adding `--verbose` option to jpackage command line.
            cmd.ignoreDefaultVerbose(true)
                    .useToolProvider(true)
                    .addArgument("--verbose")
                    .saveConsoleOutput(true)
                    .setFakeRuntime();

            List<CannedFormattedString> verboseContent;
            if (appImage) {
                verboseContent = List.of(
                        JPackageStringBundle.MAIN.cannedFormattedString("message.create-app-image"),
                        JPackageStringBundle.MAIN.cannedFormattedString("message.app-image-created"));
            } else {
                verboseContent = List.of(
                        JPackageStringBundle.MAIN.cannedFormattedString("message.create-package"),
                        JPackageStringBundle.MAIN.cannedFormattedString("message.package-created"));
            }

            new JPackageOutputValidator()
                    .expectMatchingStrings(verboseContent.toArray(CannedFormattedString[]::new))
                    .matchTimestamps()
                    .stripTimestamps()
                    .applyTo(cmd);
        });

        target.cmd().ifPresent(JPackageCommand::execute);
        target.test().ifPresent(test -> {
            test.run(CREATE);
        });
    }

    @Test
    @Parameter("false")
    @Parameter("true")
    public void testErrorsAlwaysPrinted(boolean verbose) {
        final var cmd = JPackageCommand.helloAppImage()
                .ignoreDefaultVerbose(true)
                .useToolProvider(false)
                .discardStdout(true)
                .removeArgumentWithValue("--main-class");

        if (verbose) {
            cmd.addArgument("--verbose");
        }

        cmd.validateErr(
                JPackageCommand.makeError("error.no-main-class-with-main-jar", "hello.jar"),
                JPackageCommand.makeAdvice("error.no-main-class-with-main-jar.advice", "hello.jar"));

        cmd.execute(1);
    }

    @Test
    public void testNoName() {
        final String mainClassName = "Greetings";

        JPackageCommand cmd = JPackageCommand.helloAppImage(mainClassName)
                .removeArgumentWithValue("--name");

        Path expectedImageDir = cmd.outputDir().resolve(mainClassName);
        if (TKit.isOSX()) {
            expectedImageDir = expectedImageDir.getParent().resolve(
                    expectedImageDir.getFileName().toString() + ".app");
        }

        cmd.executeAndAssertHelloAppImageCreated();
        TKit.assertEquals(expectedImageDir.toAbsolutePath().normalize().toString(),
                cmd.outputBundle().toAbsolutePath().normalize().toString(),
                String.format(
                        "Check [%s] directory is filled with application image data",
                        expectedImageDir));
    }

    @Test
    // Regular app
    @Parameter("Hello")
    // Modular app in .jar file
    @Parameter("com.other/com.other.Hello")
    // Modular app in .jmod file
    @Parameter("hello.jmod:com.other/com.other.Hello")
    // Modular app in exploded .jmod file
    @Parameter("hello.ejmod:com.other/com.other.Hello")
    public void testApp(String javaAppDesc) {
        JavaAppDesc appDesc = JavaAppDesc.parse(javaAppDesc);
        JPackageCommand cmd = JPackageCommand.helloAppImage(appDesc);
        if (appDesc.jmodFileName() != null) {
            // .jmod files are not supported at run-time. They should be
            // bundled in Java run-time with jlink command, so disable
            // use of external Java run-time if any configured.
            cmd.ignoreDefaultRuntime(true);
        }
        cmd.executeAndAssertHelloAppImageCreated();
    }

    @Test
    public void testWhitespaceInPaths() {
        JPackageCommand.helloAppImage("a/b c.jar:Hello")
        .setArgumentValue("--input", TKit.workDir().resolve("The quick brown fox"))
        .setArgumentValue("--dest", TKit.workDir().resolve("jumps over the lazy dog"))
        .executeAndAssertHelloAppImageCreated();
    }

    @Test
    @Parameter("true")
    @Parameter("false")
    public void testNoOutputDir(boolean appImage) throws IOException {
        var cmd = JPackageCommand.helloAppImage();

        final var execDir = cmd.outputDir();

        final ThrowingConsumer<JPackageCommand, IOException> initializer = cmdNoOutputDir -> {
            cmd.executePrerequisiteActions();

            final var pkgType = cmdNoOutputDir.packageType();

            cmdNoOutputDir
                    .clearArguments()
                    .addArguments(cmd.getAllArguments())
                    // Restore the value of `--type` parameter.
                    .setPackageType(pkgType)
                    .removeArgumentWithValue("--dest")
                    .setArgumentValue("--input", execDir.relativize(cmd.inputDir()))
                    .setDirectory(execDir)
                    // Force to use jpackage as executable because we need to
                    // change the current directory.
                    .useToolProvider(false);

            Optional.ofNullable(cmdNoOutputDir.getArgumentValue("--runtime-image",
                    () -> null, Path::of)).ifPresent(runtimePath -> {
                        if (!runtimePath.isAbsolute()) {
                            cmdNoOutputDir.setArgumentValue("--runtime-image",
                                    execDir.relativize(runtimePath));
                        }
                    });

            // JPackageCommand.execute() will not do the cleanup if `--dest` parameter
            // is not specified, do it manually.
            Files.createDirectories(execDir);
            TKit.deleteDirectoryContentsRecursive(execDir);
        };

        if (appImage) {
            var cmdNoOutputDir = new JPackageCommand()
                    .setPackageType(cmd.packageType());
            initializer.accept(cmdNoOutputDir);
            cmdNoOutputDir.executeAndAssertHelloAppImageCreated();
        } else {
            // Save time by packing non-functional runtime.
            // Build the runtime in app image only. This is sufficient coverage.
            cmd.setFakeRuntime();
            new PackageTest()
                    .addInitializer(initializer)
                    .addInstallVerifier(HelloApp::executeLauncherAndVerifyOutput)
                    // Prevent adding `--dest` parameter to jpackage command line.
                    .ignoreBundleOutputDir()
                    .run(CREATE_AND_UNPACK);
        }
    }

    @Test
    @ParameterSupplier("addModulesParams")
    public void testAddModules(String[] addModulesArg) {
        JPackageCommand cmd = JPackageCommand
                .helloAppImage("goodbye.jar:com.other/com.other.Hello")
                .ignoreDefaultRuntime(true); // because of --add-modules
        Stream.of(addModulesArg).forEachOrdered(cmd::addArgument);
        cmd.executeAndAssertHelloAppImageCreated();
    }

    public static enum TestTempType {
        TEMPDIR_EMPTY,
        TEMPDIR_NOT_EMPTY,
        TEMPDIR_NOT_EXIST,
    }

    /**
     * Test --temp option. Doesn't make much sense for app image as temporary
     * directory is used only on Windows. Test it in packaging mode.
     */
    @Test
    @Parameter("TEMPDIR_EMPTY")
    @Parameter("TEMPDIR_NOT_EMPTY")
    @Parameter("TEMPDIR_NOT_EXIST")
    public void testTemp(TestTempType type) throws IOException {
        final Path tempRoot = TKit.createTempDirectory("tmp");

        var pkgTest = new PackageTest()
        .configureHelloApp()
        // Force save of package bundle in test work directory.
        .addInitializer(JPackageCommand::setDefaultInputOutput)
        .addInitializer(cmd -> {
            Path tempDir = tempRoot.resolve(cmd.packageType().name());
            switch (type) {
                    case TEMPDIR_EMPTY -> Files.createDirectories(tempDir);
                    case TEMPDIR_NOT_EXIST -> Files.createDirectories(tempDir.getParent());
                    case TEMPDIR_NOT_EMPTY -> {
                        Files.createDirectories(tempDir);
                        TKit.createTextFile(tempDir.resolve("foo.txt"), List.of(
                                "Hello Duke!"));
                    }
                }
                cmd.addArguments("--temp", tempDir);
            }
        );

        if (TestTempType.TEMPDIR_NOT_EMPTY.equals(type)) {
            pkgTest.setExpectedExitCode(1).addInitializer(cmd -> {
                cmd.validateErr(JPackageCommand.makeError(
                        "error.parameter-not-empty-directory", cmd.getArgumentValue("--temp"), "--temp"));
            }).addBundleVerifier(cmd -> {
                // Check jpackage didn't use the supplied directory.
                Path tempDir = Path.of(cmd.getArgumentValue("--temp"));
                TKit.assertDirectoryContent(tempDir).match(Path.of("foo.txt"));
                TKit.assertStringListEquals(List.of("Hello Duke!"),
                        Files.readAllLines(tempDir.resolve("foo.txt")),
                        "Check the contents of the file in the supplied temporary directory");
            });
        } else {
            pkgTest.addBundleVerifier(cmd -> {
                // Check jpackage used the supplied directory.
                Path tempDir = Path.of(cmd.getArgumentValue("--temp"));
                TKit.assertDirectoryNotEmpty(tempDir);
            });
        }

        pkgTest.run(PackageTest.Action.CREATE);
    }

    @Test
    public void testAtFile() throws IOException {
        JPackageCommand cmd = JPackageCommand
                .helloAppImage()
                .setArgumentValue("--dest", TKit.createTempDirectory("output"));

        // Init options file with the list of options configured
        // for JPackageCommand instance.
        final Path optionsFile = TKit.createTempFile(Path.of("options"));
        Files.write(optionsFile,
                List.of(String.join(" ", cmd.getAllArguments())));

        // Build app jar file.
        cmd.executePrerequisiteActions();

        // Instead of running jpackage command through configured
        // JPackageCommand instance, run vanilla jpackage command with @ file.
        getJPackageToolProvider()
                .addArgument(String.format("@%s", optionsFile))
                .execute();

        // Verify output of jpackage command.
        cmd.assertImageCreated();
        HelloApp.executeLauncherAndVerifyOutput(cmd);
    }

    @Test
    @Parameter("1")
    @Parameter("123")
    public void testExitCode(int exitCode) {
        JPackageCommand cmd = JPackageCommand
                .helloAppImage()
                .addArguments("--java-options", String.format(
                        "-Djpackage.test.exitCode=%d", exitCode));
        cmd.executeAndAssertHelloAppImageCreated();
    }

    private static Executor getJPackageToolProvider() {
        return getToolProvider(JavaTool.JPACKAGE);
    }

    private static Executor getToolProvider(JavaTool tool) {
        return new Executor().dumpOutput().saveOutput().setToolProvider(tool);
    }
}
