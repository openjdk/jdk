/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.WindowsHelper.getWixTypeFromVerboseJPackageOutput;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageCommand.MessageCategory;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.WindowsHelper.WixType;

/*
 * @test
 * @summary Custom l10n of msi installers in jpackage
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @requires (jpackage.test.SQETest == null)
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @compile -Xlint:all -Werror WinL10nTest.java
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinL10nTest
 */

public class WinL10nTest {

    public WinL10nTest(WixFileInitializer wxlFileInitializers[],
            String[] expectedCultures, String expectedErrorMessage,
            String userLanguage, String userCountry,
            boolean enableWixUIExtension) {
        this.wxlFileInitializers = wxlFileInitializers;
        this.expectedCultures = expectedCultures;
        this.expectedErrorMessage = expectedErrorMessage;
        this.userLanguage = userLanguage;
        this.userCountry = userCountry;
        this.enableWixUIExtension = enableWixUIExtension;
    }

    @Parameters
    public static List<Object[]> data() {
        return List.of(new Object[][]{
            {null, new String[] {"en-us"}, null, null, null, false},
            {null, new String[] {"en-us"}, null, "en", "US", false},
            {null, new String[] {"en-us"}, null, "en", "US", true},
            {null, new String[] {"de-de"}, null, "de", "DE", false},
            {null, new String[] {"de-de"}, null, "de", "DE", true},
            {null, new String[] {"ja-jp"}, null, "ja", "JP", false},
            {null, new String[] {"ja-jp"}, null, "ja", "JP", true},
            {null, new String[] {"zh-cn"}, null, "zh", "CN", false},
            {null, new String[] {"zh-cn"}, null, "zh", "CN", true},
            {new WixFileInitializer[] {
                WixFileInitializer.create("a.wxl", "en-us")
            }, new String[] {"en-us"}, null, null, null, false},
            {new WixFileInitializer[] {
                WixFileInitializer.create("a.wxl", "fr")
            }, new String[] {"fr", "en-us"}, null, null, null, false},
            {new WixFileInitializer[] {
                WixFileInitializer.create("a.wxl", "fr"),
                WixFileInitializer.create("b.wxl", "fr")
            }, new String[] {"fr", "en-us"}, null, null, null, false},
            {new WixFileInitializer[] {
                WixFileInitializer.create("a.wxl", "it"),
                WixFileInitializer.create("b.wxl", "fr")
            }, new String[] {"it", "fr", "en-us"}, null, null, null, false},
            {new WixFileInitializer[] {
                WixFileInitializer.create("c.wxl", "it"),
                WixFileInitializer.create("b.wxl", "fr")
            }, new String[] {"fr", "it", "en-us"}, null, null, null, false},
            {new WixFileInitializer[] {
                WixFileInitializer.create("a.wxl", "fr"),
                WixFileInitializer.create("b.wxl", "it"),
                WixFileInitializer.create("c.wxl", "fr"),
                WixFileInitializer.create("d.wxl", "it")
            }, new String[] {"fr", "it", "en-us"}, null, null, null, false},
            {new WixFileInitializer[] {
                WixFileInitializer.create("c.wxl", "it"),
                WixFileInitializer.createMalformed("b.wxl")
            }, null, null, null, null, false},
            {new WixFileInitializer[] {
                WixFileInitializer.create("MsiInstallerStrings_de.wxl", "de")
            }, new String[] {"en-us"}, null, null, null, false}
        });
    }

    private record OutputAnalizer(Executor.Result result, WixType wixType, Optional<String> wixBuildCommandLine) {

        OutputAnalizer {
            Objects.requireNonNull(result);
            Objects.requireNonNull(wixType);
            Objects.requireNonNull(wixBuildCommandLine);
        }

        OutputAnalizer(Executor.Result result) {
            this(result, getWixTypeFromVerboseJPackageOutput(result));
        }

        OutputAnalizer(Executor.Result result, WixType wixType) {
            this(result, wixType, findWixBuildCommandLine(result, wixType));
        }

        private static Optional<String> findWixBuildCommandLine(Executor.Result result, WixType wixType) {
            Objects.requireNonNull(result);
            Objects.requireNonNull(wixType);
            return result.stdout().stream()
                    .filter(JPackageCommand::withTimestamp)
                    .map(JPackageCommand::stripTimestamp)
                    .map(String::stripLeading)
                    .filter(line -> {
                        return line.startsWith("Running ") && line.contains(wixType.buildTool()) && line.contains(" -out ");
                    }).findFirst();
        }

        String getWixBuildCommandLine() {
            return wixBuildCommandLine.orElseThrow(() -> {
                return new IllegalStateException("WiX build command line not found");
            });
        }

        void verifyCulturesInCmdline(String... cultures) {
            if (cultures.length == 0) {
                throw new IllegalArgumentException("Cultures list must be non-empty");
            }

            var cmdline = getWixBuildCommandLine();

            String expected;
            switch (wixType) {
                case WIX3 -> {
                    expected = "-cultures:" + String.join(";", cultures);
                }
                case WIX4 -> {
                    expected = Stream.of(cultures).map(culture -> {
                        return String.join(" ", "-culture", culture);
                    }).collect(Collectors.joining(" "));
                }
                default -> {
                    throw new AssertionError();
                }
            }
            TKit.assertTextStream(expected).label("WiX build command line").apply(List.of(cmdline));
        }
    }

    private static List<TKit.TextStreamVerifier> createDefaultL10nFilesLocVerifiers(Path wixSrcDir) {
        return Arrays.stream(DEFAULT_L10N_FILES).map(loc ->
                TKit.assertTextStream("-loc " + wixSrcDir.resolve(
                        String.format("MsiInstallerStrings_%s.wxl", loc))))
                .toList();
    }

    @Test
    public void test() throws IOException {
        final Path tempRoot = TKit.createTempDirectory("tmp");

        final boolean allWxlFilesValid;
        if (wxlFileInitializers != null) {
            allWxlFilesValid = Stream.of(wxlFileInitializers).allMatch(
                    WixFileInitializer::isValid);
        } else {
            allWxlFilesValid = true;
        }

        PackageTest test = new PackageTest()
        .forTypes(PackageType.WINDOWS)
        .configureHelloApp()
        .addInitializer(cmd -> {
            // 1. Set fake run time to save time by skipping jlink step of jpackage.
            // 2. Instruct test to save jpackage output.
            cmd.setFakeRuntime().saveConsoleOutput(true);

            // Need summary to pick WiX version.
            // Need errors to pick up errors.
            // Need tools to pick up tool command lines jpackage invokes
            // Suppress trace as it interfers with output validators.
            cmd.setEnabledMessageCategories(
                    MessageCategory.SUMMARY,
                    MessageCategory.ERRORS,
                    MessageCategory.TOOLS
            ).setDisabledMessageCategories(MessageCategory.TRACE);

            boolean withJavaOptions = false;

            // Set JVM default locale that is used to select primary l10n file.
            if (userLanguage != null) {
                withJavaOptions = true;
                cmd.addArguments("-J-Duser.language=" + userLanguage);
            }
            if (userCountry != null) {
                withJavaOptions = true;
                cmd.addArguments("-J-Duser.country=" + userCountry);
            }

            if (withJavaOptions) {
                // Use jpackage as a command to allow "-J" options come through
                cmd.useToolProvider(false);
            }

            // Cultures handling is affected by the WiX extensions used.
            // By default only WixUtilExtension is used, this flag
            // additionally enables WixUIExtension.
            if (enableWixUIExtension) {
                cmd.addArgument("--win-dir-chooser");
            }

            // Preserve config dir to check the set of copied l10n files.
            Path tempDir = tempRoot.resolve(cmd.packageType().name());
            cmd.addArguments("--temp", tempDir);
        })
        .addBundleVerifier((cmd, result) -> {

            var outputAnalizer = new OutputAnalizer(result);

            if (expectedCultures != null) {
                outputAnalizer.verifyCulturesInCmdline(expectedCultures);
            }

            if (expectedErrorMessage != null) {
                TKit.assertTextStream(expectedErrorMessage).apply(result.stderr());
            }

            if (wxlFileInitializers != null) {
                var wixSrcDir = Path.of(cmd.getArgumentValue("--temp")).resolve(
                        "config").normalize().toAbsolutePath();

                if (allWxlFilesValid) {
                    for (var v : wxlFileInitializers) {
                        if (!v.name.startsWith("MsiInstallerStrings_")) {
                            v.createCmdOutputVerifier(wixSrcDir).apply(List.of(outputAnalizer.getWixBuildCommandLine()));
                        }
                    }

                    for (var v : createDefaultL10nFilesLocVerifiers(wixSrcDir)) {
                        v.apply(List.of(outputAnalizer.getWixBuildCommandLine()));
                    }
                } else {
                    Stream.of(wxlFileInitializers)
                            .filter(Predicate.not(WixFileInitializer::isValid))
                            .forEach(v -> v.createCmdOutputVerifier(
                                    wixSrcDir).apply(result.getOutput()));
                    TKit.assertTrue(outputAnalizer.wixBuildCommandLine().isEmpty(),
                            String.format("Check %s was not invoked", outputAnalizer.wixType.buildTool()));
                }
            }
        });

        if (wxlFileInitializers != null) {
            test.addInitializer(cmd -> {
                resourceDir = TKit.createTempDirectory("resources");

                cmd.addArguments("--resource-dir", resourceDir);

                for (var v : wxlFileInitializers) {
                    v.apply(resourceDir);
                }
            });
        }

        if (expectedErrorMessage != null || !allWxlFilesValid) {
            test.setExpectedExitCode(1);
        }

        test.run();
    }

    private final WixFileInitializer[] wxlFileInitializers;
    private final String[] expectedCultures;
    private final String expectedErrorMessage;
    private final String userLanguage;
    private final String userCountry;
    private final boolean enableWixUIExtension;
    private Path resourceDir;

    private static class WixFileInitializer {
        static WixFileInitializer create(String name, String culture) {
            return new WixFileInitializer(name, culture);
        }

        static WixFileInitializer createMalformed(String name) {
            return new WixFileInitializer(name, null) {
                @Override
                public void apply(Path root) throws IOException {
                    TKit.createTextFile(root.resolve(name), List.of(
                            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                            "<WixLocalization>"));
                }

                @Override
                public String toString() {
                    return String.format("name=%s; malformed xml", name);
                }

                @Override
                boolean isValid() {
                    return false;
                }

                @Override
                TKit.TextStreamVerifier createCmdOutputVerifier(Path wixSrcDir) {
                    return TKit.assertTextStream(String.format(
                            "Failed to parse %s file", wixSrcDir.resolve("b.wxl")));
                }
            };
        }

        private WixFileInitializer(String name, String culture) {
            this.name = name;
            this.culture = culture;
        }

        void apply(Path root) throws IOException {
            TKit.createTextFile(root.resolve(name), List.of(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                    culture == null ? "<WixLocalization/>" : "<WixLocalization Culture=\""
                            + culture
                            + "\" xmlns=\"http://schemas.microsoft.com/wix/2006/localization\" Codepage=\"1252\"/>"));
        }

        TKit.TextStreamVerifier createCmdOutputVerifier(Path wixSrcDir) {
            return TKit.assertTextStream("-loc " + wixSrcDir.resolve(name));
        }

        boolean isValid() {
            return true;
        }

        @Override
        public String toString() {
            return String.format("name=%s; culture=%s", name, culture);
        }

        private final String name;
        private final String culture;
    }

    private static final String[] DEFAULT_L10N_FILES = { "de", "en", "ja", "zh_CN" };
}
