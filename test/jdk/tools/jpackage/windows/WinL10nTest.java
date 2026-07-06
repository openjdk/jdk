/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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

public record WinL10nTest(
        Collection<WixFileInitializer> wxlFileInitializers,
        Collection<String> expectedCultures,
        Optional<Locale> locale,
        boolean enableWixUIExtension) {

    public WinL10nTest {
        Objects.requireNonNull(wxlFileInitializers);
        Objects.requireNonNull(expectedCultures);
        Objects.requireNonNull(locale);
    }

    public WinL10nTest(Collection<String> expectedCultures, Locale locale, boolean enableWixUIExtension) {
        this(List.of(), expectedCultures, Optional.of(locale), enableWixUIExtension);
    }

    public WinL10nTest(Collection<WixFileInitializer> wxlFileInitializers, Collection<String> expectedCultures) {
        this(wxlFileInitializers, expectedCultures, Optional.empty(), false);
    }

    public WinL10nTest(WinL10nTest other) {
        this(other.wxlFileInitializers, other.expectedCultures, other.locale, other.enableWixUIExtension);
    }

    @Override
    public String toString() {
        var tokens = new ArrayList<String>();
        if (!wxlFileInitializers.isEmpty()) {
            tokens.add(String.format("wxlFileInitializers=%s", wxlFileInitializers));
        }
        if (!expectedCultures.isEmpty()) {
            tokens.add(String.format("expectedCultures=%s", expectedCultures));
        }
        locale.ifPresent(l -> {
            tokens.add(String.format("locale=%s", l));
        });
        if (enableWixUIExtension) {
            tokens.add("enableWixUIExtension=true");
        }
        return String.join(", ", tokens);
    }

    @Parameters
    public static List<Object[]> data() {

        List<WinL10nTest> testCases = new ArrayList<>();

        testCases.add(new WinL10nTest(List.of(), List.of("en-us"), Optional.empty(), false));
        for (var enableWixUIExtension : List.of(true, false)) {
            testCases.add(new WinL10nTest(List.of("en-us"), Locale.of("en", "US"), enableWixUIExtension));
            testCases.add(new WinL10nTest(List.of("de-de"), Locale.of("de", "DE"), enableWixUIExtension));
            testCases.add(new WinL10nTest(List.of("ja-jp"), Locale.of("ja", "JP"), enableWixUIExtension));
            testCases.add(new WinL10nTest(List.of("zh-cn"), Locale.of("zh", "CN"), enableWixUIExtension));
        }

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("a.wxl", "en-us")
        ), List.of("en-us")));

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("a.wxl", "fr")
        ), List.of("fr", "en-us")));

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("a.wxl", "fr"),
                WixFileInitializer.create("b.wxl", "fr")
        ), List.of("fr", "en-us")));

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("a.wxl", "it"),
                WixFileInitializer.create("b.wxl", "fr")
        ), List.of("it", "fr", "en-us")));

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("c.wxl", "it"),
                WixFileInitializer.create("b.wxl", "fr")
        ), List.of("fr", "it", "en-us")));

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("a.wxl", "fr"),
                WixFileInitializer.create("b.wxl", "it"),
                WixFileInitializer.create("c.wxl", "fr"),
                WixFileInitializer.create("d.wxl", "it")
        ), List.of("fr", "it", "en-us")));

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("c.wxl", "it"),
                WixFileInitializer.createMalformed("b.wxl")
        ), List.of()));

        testCases.add(new WinL10nTest(List.of(
                WixFileInitializer.create("MsiInstallerStrings_de.wxl", "de")
        ), List.of("en-us")));

        return testCases.stream().map(testCase -> {
            return new Object[] { testCase };
        }).toList();
    }

    private record OutputAnalizer(Executor.Result result, WixType wixType, Optional<String> wixBuildCommandLine) {

        OutputAnalizer {
            Objects.requireNonNull(result);
            Objects.requireNonNull(wixType);
            Objects.requireNonNull(wixBuildCommandLine);
        }

        OutputAnalizer(Locale locale, Executor.Result result) {
            this(result, getWixTypeFromVerboseJPackageOutput(locale, result));
        }

        OutputAnalizer(Executor.Result result, WixType wixType) {
            this(result, wixType, findWixBuildCommandLine(result, wixType));
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

            var expected = switch (wixType) {
                case WIX3 -> {
                    yield "-cultures:" + String.join(";", cultures);
                }
                case WIX4 -> {
                    yield Stream.of(cultures).map(culture -> {
                        return String.join(" ", "-culture", culture);
                    }).collect(Collectors.joining(" "));
                }
            };
            TKit.assertTextStream(expected).label("WiX build command line").apply(List.of(cmdline));
        }

        private static Optional<String> findWixBuildCommandLine(Executor.Result result, WixType wixType) {
            Objects.requireNonNull(result);
            Objects.requireNonNull(wixType);
            return result.stdout().stream()
                    .filter(JPackageCommand::withTimestamp)
                    .map(JPackageCommand::stripTimestamp)
                    .map(String::stripLeading)
                    .filter(createToolCommandLinePredicate(wixType.buildTool()))
                    .findFirst();
        }

        private static final Predicate<String> createToolCommandLinePredicate(String wixToolFileName) {
            Objects.requireNonNull(wixToolFileName);
            return s -> {
                if (!s.startsWith("Running ")) {
                    return false;
                }

                // Accommodate for:
                //     'C:\Program Files (x86)\WiX Toolset v3.14\bin\light.exe' ...
                //     light.exe ...
                return Stream.of("Running %s ", "\\%s ", "\\%s' ").map(format -> {
                    return String.format(format, wixToolFileName);
                }).anyMatch(s::contains) && s.contains(" -out ");
            };
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

        final boolean allWxlFilesValid = wxlFileInitializers.stream().allMatch(WixFileInitializer::isValid);

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
            locale.ifPresent(l -> {
                cmd.addArguments("-J-Duser.language=" + l.getLanguage());
                cmd.addArguments("-J-Duser.country=" + l.getCountry());
                // Force UTF8 encoding of the output of jpackage command.
                // This is the default encoding of the output for the command executor.
                // This is needed to properly handle JP and CN l10n-s.
                cmd.addArguments("-J-Dstdout.encoding=UTF-8");
                cmd.addArguments("-J-Dstderr.encoding=UTF-8");
                cmd.addArguments("-J-Dfile.encoding=UTF-8");
                // Use jpackage as a command to allow "-J" options come through
                cmd.useToolProvider(false);
            });

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

            var outputAnalizer = new OutputAnalizer(locale.orElseGet(Locale::getDefault), result);

            if (!expectedCultures.isEmpty()) {
                outputAnalizer.verifyCulturesInCmdline(expectedCultures.toArray(String[]::new));
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
                    wxlFileInitializers.stream()
                            .filter(Predicate.not(WixFileInitializer::isValid))
                            .forEach(v -> v.createCmdOutputVerifier(
                                    wixSrcDir).apply(result.getOutput()));
                    TKit.assertTrue(outputAnalizer.wixBuildCommandLine().isEmpty(),
                            String.format("Check %s was not invoked", outputAnalizer.wixType.buildTool()));
                }
            }
        });

        if (!wxlFileInitializers.isEmpty()) {
            test.addInitializer(cmd -> {
                var resourceDir = TKit.createTempDirectory("resources");

                cmd.addArguments("--resource-dir", resourceDir);

                for (var v : wxlFileInitializers) {
                    v.apply(resourceDir);
                }
            });
        }

        if (!allWxlFilesValid) {
            test.setExpectedExitCode(1);
        }

        test.run();
    }

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
