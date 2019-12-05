/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.*;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.jpackage.test.Annotations.*;

/*
 * @test
 * @summary jpackage basic testing
 * @library ../../../../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.incubator.jpackage/jdk.incubator.jpackage.internal
 * @compile BasicTest.java
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=jdk.jpackage.tests.BasicTest
 */

public final class BasicTest {
    @Test
    public void testNoArgs() {
        List<String> output =
                getJPackageToolProvider().executeAndGetOutput();
        TKit.assertStringListEquals(List.of("Usage: jpackage <options>",
                "Use jpackage --help (or -h) for a list of possible options"),
                output, "Check jpackage output");
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
        TKit.assertNotEquals(0, countStrings.apply(List.of(expectedPrefix)),
                "Check help text contains plaform specific parameters");
        TKit.assertEquals(0, countStrings.apply(unexpectedPrefixes),
                "Check help text doesn't contain unexpected parameters");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testVerbose() {
        JPackageCommand cmd = JPackageCommand.helloAppImage()
                .setFakeRuntime().executePrerequisiteActions();

        List<String> expectedVerboseOutputStrings = new ArrayList<>();
        expectedVerboseOutputStrings.add("Creating app package:");
        if (TKit.isWindows()) {
            expectedVerboseOutputStrings.add("Result application bundle:");
            expectedVerboseOutputStrings.add(
                    "Succeeded in building Windows Application Image package");
        } else if (TKit.isLinux()) {
            expectedVerboseOutputStrings.add(
                    "Succeeded in building Linux Application Image package");
        } else if (TKit.isOSX()) {
            expectedVerboseOutputStrings.add("Preparing Info.plist:");
            expectedVerboseOutputStrings.add(
                    "Succeeded in building Mac Application Image package");
        } else {
            TKit.throwUnknownPlatformError();
        }

        TKit.deleteDirectoryContentsRecursive(cmd.outputDir());
        List<String> nonVerboseOutput = cmd.createExecutor().executeAndGetOutput();
        List<String>[] verboseOutput = (List<String>[])new List<?>[1];

        // Directory clean up is not 100% reliable on Windows because of
        // antivirus software that can lock .exe files. Setup
        // diffreent output directory instead of cleaning the default one for
        // verbose jpackage run.
        TKit.withTempDirectory("verbose-output", tempDir -> {
            cmd.setArgumentValue("--dest", tempDir);
            verboseOutput[0] = cmd.createExecutor().addArgument(
                    "--verbose").executeAndGetOutput();
        });

        TKit.assertTrue(nonVerboseOutput.size() < verboseOutput[0].size(),
                "Check verbose output is longer than regular");

        expectedVerboseOutputStrings.forEach(str -> {
            TKit.assertTextStream(str).label("regular output")
                    .predicate(String::contains).negate()
                    .apply(nonVerboseOutput.stream());
        });

        expectedVerboseOutputStrings.forEach(str -> {
            TKit.assertTextStream(str).label("verbose output")
                    .apply(verboseOutput[0].stream());
        });
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
    // Modular app
    @Parameter("com.other/com.other.Hello")
    public void testApp(String javaAppDesc) {
        JPackageCommand.helloAppImage(javaAppDesc)
        .executeAndAssertHelloAppImageCreated();
    }

    @Test
    public void testWhitespaceInPaths() {
        JPackageCommand.helloAppImage("a/b c.jar:Hello")
        .setArgumentValue("--input", TKit.workDir().resolve("The quick brown fox"))
        .setArgumentValue("--dest", TKit.workDir().resolve("jumps over the lazy dog"))
        .executeAndAssertHelloAppImageCreated();
    }

    @Test
    @Parameter("ALL-MODULE-PATH")
    @Parameter("ALL-DEFAULT")
    @Parameter("java.desktop")
    @Parameter("java.desktop,jdk.jartool")
    @Parameter({ "java.desktop", "jdk.jartool" })
    public void testAddModules(String... addModulesArg) {
        JPackageCommand cmd = JPackageCommand
                .helloAppImage("goodbye.jar:com.other/com.other.Hello");
        Stream.of(addModulesArg).map(v -> Stream.of("--add-modules", v)).flatMap(
                s -> s).forEachOrdered(cmd::addArgument);
        cmd.executeAndAssertHelloAppImageCreated();
    }

    /**
     * Test --temp option. Doesn't make much sense for app image as temporary
     * directory is used only on Windows. Test it in packaging mode.
     * @throws IOException
     */
    @Test
    public void testTemp() throws IOException {
        TKit.withTempDirectory("temp-root", tempRoot -> {
            Function<JPackageCommand, Path> getTempDir = cmd -> {
                return tempRoot.resolve(cmd.outputBundle().getFileName());
            };

            ThrowingConsumer<JPackageCommand> addTempDir = cmd -> {
                Path tempDir = getTempDir.apply(cmd);
                Files.createDirectories(tempDir);
                cmd.addArguments("--temp", tempDir);
            };

            new PackageTest().configureHelloApp().addInitializer(addTempDir)
            .addBundleVerifier(cmd -> {
                // Check jpackage actually used the supplied directory.
                Path tempDir = getTempDir.apply(cmd);
                TKit.assertNotEquals(0, tempDir.toFile().list().length,
                        String.format(
                                "Check jpackage wrote some data in the supplied temporary directory [%s]",
                                tempDir));
            })
            .run();

            new PackageTest().configureHelloApp().addInitializer(addTempDir)
            .addInitializer(cmd -> {
                // Clean output from the previus jpackage run.
                Files.delete(cmd.outputBundle());
            })
            // Temporary directory should not be empty,
            // jpackage should exit with error.
            .setExpectedExitCode(1)
            .run();
        });
    }

    @Test
    public void testAtFile() throws IOException {
        JPackageCommand cmd = JPackageCommand.helloAppImage();

        // Init options file with the list of options configured
        // for JPackageCommand instance.
        final Path optionsFile = TKit.workDir().resolve("options");
        Files.write(optionsFile,
                List.of(String.join(" ", cmd.getAllArguments())));

        // Build app jar file.
        cmd.executePrerequisiteActions();

        // Make sure output directory is empty. Normally JPackageCommand would
        // do this automatically.
        TKit.deleteDirectoryContentsRecursive(cmd.outputDir());

        // Instead of running jpackage command through configured
        // JPackageCommand instance, run vanilla jpackage command with @ file.
        getJPackageToolProvider()
                .addArgument(String.format("@%s", optionsFile))
                .execute().assertExitCodeIsZero();

        // Verify output of jpackage command.
        cmd.assertImageCreated();
        HelloApp.executeLauncherAndVerifyOutput(cmd);
    }

    @Parameter("Hello")
    @Parameter("com.foo/com.foo.main.Aloha")
    @Test
    public void testJLinkRuntime(String javaAppDesc) {
        JPackageCommand cmd = JPackageCommand.helloAppImage(javaAppDesc);

        // If `--module` parameter was set on jpackage command line, get its
        // value and extract module name.
        // E.g.: foo.bar2/foo.bar.Buz -> foo.bar2
        // Note: HelloApp class manages `--module` parameter on jpackage command line
        final String moduleName = cmd.getArgumentValue("--module", () -> null,
                (v) -> v.split("/", 2)[0]);

        if (moduleName != null) {
            // Build module jar.
            cmd.executePrerequisiteActions();
        }

        TKit.withTempDirectory("runtime", tempDir -> {
            final Path runtimeDir = tempDir.resolve("data");

            // List of modules required for test app.
            final var modules = new String[] {
                "java.base",
                "java.desktop"
            };

            Executor jlink = getToolProvider(JavaTool.JLINK)
            .saveOutput(false)
            .addArguments(
                    "--add-modules", String.join(",", modules),
                    "--output", runtimeDir.toString(),
                    "--strip-debug",
                    "--no-header-files",
                    "--no-man-pages");

            if (moduleName != null) {
                jlink.addArguments("--add-modules", moduleName, "--module-path",
                        Path.of(cmd.getArgumentValue("--module-path")).resolve(
                                "hello.jar").toString());
            }

            jlink.execute().assertExitCodeIsZero();

            cmd.addArguments("--runtime-image", runtimeDir);
            cmd.executeAndAssertHelloAppImageCreated();
        });
    }

    private static Executor getJPackageToolProvider() {
        return getToolProvider(JavaTool.JPACKAGE);
    }

    private static Executor getToolProvider(JavaTool tool) {
        return new Executor().dumpOutput().saveOutput().setToolProvider(tool);
    }
}
