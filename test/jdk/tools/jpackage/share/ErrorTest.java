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


import static java.util.stream.Collectors.toMap;
import static jdk.internal.util.OperatingSystem.LINUX;
import static jdk.internal.util.OperatingSystem.MACOS;
import static jdk.internal.util.OperatingSystem.WINDOWS;
import static jdk.jpackage.test.CannedFormattedString.cannedAbsolutePath;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.TokenReplace;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Test jpackage output for erroneous input
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror ErrorTest.java
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ErrorTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useExecutableByDefault
 */

/*
 * @test
 * @summary Test jpackage output for erroneous input
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror ErrorTest.java
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ErrorTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useToolProviderByDefault
 */

public final class ErrorTest {

    enum Token {
        JAVA_HOME(cmd -> {
            return System.getProperty("java.home");
        }),
        APP_IMAGE(cmd -> {
            final var appImageRoot = TKit.createTempDirectory("appimage");

            final var appImageCmd = JPackageCommand.helloAppImage()
                    .setFakeRuntime().setArgumentValue("--dest", appImageRoot);

            appImageCmd.execute();

            return appImageCmd.outputBundle().toString();
        }),
        ADD_LAUNCHER_PROPERTY_FILE;

        private Token() {
            this.valueSupplier = Optional.empty();
        }

        private Token(Function<JPackageCommand, Object> valueSupplier) {
            this.valueSupplier = Optional.of(valueSupplier);
        }

        String token() {
            return makeToken(name());
        }

        TokenReplace asTokenReplace() {
            return tokenReplace;
        }

        Optional<Object> expand(JPackageCommand cmd) {
            return valueSupplier.map(func -> func.apply(cmd));
        }

        private static String makeToken(String v) {
            Objects.requireNonNull(v);
            return String.format("@@%s@@", v);
        }

        private final Optional<Function<JPackageCommand, Object>> valueSupplier;
        private final TokenReplace tokenReplace = new TokenReplace(token());
    }

    record PackageTypeSpec(Optional<PackageType> type, boolean anyNativeType) implements CannedFormattedString.CannedArgument {
        PackageTypeSpec {
            Objects.requireNonNull(type);
            if (type.isPresent() && anyNativeType) {
                throw new IllegalArgumentException();
            }
        }

        PackageTypeSpec(PackageType type) {
            this(Optional.of(type), false);
        }

        boolean isSupported() {
            if (anyNativeType) {
                return NATIVE_TYPE.isPresent();
            } else {
                return type.orElseThrow().isSupported();
            }
        }

        PackageType resolvedType() {
            return type.or(() -> NATIVE_TYPE).orElseThrow(PackageType::throwSkippedExceptionIfNativePackagingUnavailable);
        }

        @Override
        public String value() {
            return resolvedType().getType();
        }

        @Override
        public final String toString() {
            if (anyNativeType) {
                return "NATIVE";
            } else {
                return type.orElseThrow().toString();
            }
        }

        private static Optional<PackageType> defaultNativeType() {
            final Collection<PackageType> nativeTypes;
            if (TKit.isLinux()) {
                nativeTypes = PackageType.LINUX;
            } else if (TKit.isOSX()) {
                nativeTypes = PackageType.MAC;
            } else if (TKit.isWindows()) {
                nativeTypes = List.of(PackageType.WIN_MSI);
            } else {
                throw TKit.throwUnknownPlatformError();
            }

            return nativeTypes.stream().filter(PackageType::isSupported).findFirst();
        }

        static final PackageTypeSpec NATIVE = new PackageTypeSpec(Optional.empty(), true);

        private static final Optional<PackageType> NATIVE_TYPE = defaultNativeType();
    }

    public record TestSpec(Optional<PackageTypeSpec> type, Optional<String> appDesc, List<String> addArgs,
            List<String> removeArgs, List<CannedFormattedString> expectedErrors) {

        static final class Builder {

            Builder type(PackageType v) {
                type = Optional.ofNullable(v).map(PackageTypeSpec::new).orElse(null);
                return this;
            }

            Builder notype() {
                return type(null);
            }

            Builder nativeType() {
                type = PackageTypeSpec.NATIVE;
                return this;
            }

            Builder appDesc(String v) {
                appDesc = v;
                return this;
            }

            Builder noAppDesc() {
                return appDesc(null);
            }

            Builder setAddArgs(List<String> v) {
                addArgs.clear();
                addArgs.addAll(v);
                return this;
            }

            Builder setAddArgs(String... v) {
                return setAddArgs(List.of(v));
            }

            Builder addArgs(List<String> v) {
                addArgs.addAll(v);
                return this;
            }

            Builder addArgs(String... v) {
                return addArgs(List.of(v));
            }

            Builder setRemoveArgs(List<String> v) {
                removeArgs.clear();
                removeArgs.addAll(v);
                return this;
            }

            Builder setRemoveArgs(String... v) {
                return setRemoveArgs(List.of(v));
            }

            Builder removeArgs(List<String> v) {
                removeArgs.addAll(v);
                return this;
            }

            Builder removeArgs(String... v) {
                return removeArgs(List.of(v));
            }

            Builder setErrors(List<CannedFormattedString> v) {
                expectedErrors = v;
                return this;
            }

            Builder setErrors(CannedFormattedString... v) {
                return setErrors(List.of(v));
            }

            Builder errors(List<CannedFormattedString> v) {
                expectedErrors.addAll(v);
                return this;
            }

            Builder errors(CannedFormattedString... v) {
                return errors(List.of(v));
            }

            Builder error(String key, Object ... args) {
                return errors(JPackageStringBundle.MAIN.cannedFormattedString(key, args));
            }

            Builder invalidTypeArg(String arg, String... otherArgs) {
                Objects.requireNonNull(type);
                return addArgs(arg).addArgs(otherArgs).error("ERR_InvalidTypeOption", arg, type);
            }

            Builder unsupportedPlatformOption(String arg, String ... otherArgs) {
                return addArgs(arg).addArgs(otherArgs).error("ERR_UnsupportedOption", arg);
            }

            TestSpec create() {
                return new TestSpec(Optional.ofNullable(type), Optional.ofNullable(appDesc),
                        List.copyOf(addArgs), List.copyOf(removeArgs), List.copyOf(expectedErrors));
            }

            private PackageTypeSpec type = new PackageTypeSpec(PackageType.IMAGE);
            private String appDesc = DEFAULT_APP_DESC;
            private List<String> addArgs = new ArrayList<>();
            private List<String> removeArgs = new ArrayList<>();
            private List<CannedFormattedString> expectedErrors = new ArrayList<>();
        }

        public TestSpec {
            Objects.requireNonNull(type);
            Objects.requireNonNull(appDesc);
            Objects.requireNonNull(addArgs);
            addArgs.forEach(Objects::requireNonNull);
            Objects.requireNonNull(removeArgs);
            removeArgs.forEach(Objects::requireNonNull);
            if (expectedErrors.isEmpty()) {
                throw new IllegalArgumentException("The list of expected errors must be non-empty");
            }
        }

        void test() {
            test(Map.of());
        }

        boolean isSupported() {
            return type.map(PackageTypeSpec::isSupported).orElse(true);
        }

        void test(Map<Token, Function<JPackageCommand, Object>> tokenValueSuppliers) {
            final var cmd = appDesc.map(JPackageCommand::helloAppImage).orElseGet(JPackageCommand::new);
            type.map(PackageTypeSpec::resolvedType).ifPresent(cmd::setPackageType);

            removeArgs.forEach(cmd::removeArgumentWithValue);
            cmd.addArguments(addArgs);

            final var tokenValueSupplier = TokenReplace.createCachingTokenValueSupplier(Stream.of(Token.values()).collect(toMap(Token::token, token -> {
                return () -> {
                    return token.expand(cmd).orElseGet(() -> {
                        final var tvs = Objects.requireNonNull(tokenValueSuppliers.get(token), () -> {
                            return String.format("No token value supplier for token [%s]", token);
                        });
                        return tvs.apply(cmd);
                    });
                };
            })));

            for (final var token : Token.values()) {
                final var newArgs = cmd.getAllArguments().stream().map(arg -> {
                    return token.asTokenReplace().applyTo(arg, tokenValueSupplier);
                }).toList();
                cmd.clearArguments().addArguments(newArgs);
            }

            defaultInit(cmd, expectedErrors);
            cmd.execute(1);
        }

        @Override
        public final String toString() {
            final var sb = new StringBuilder();
            type.ifPresent(v -> {
                sb.append(v).append("; ");
            });
            appDesc.ifPresent(v -> {
                sb.append("app-desc=").append(v).append("; ");
            });
            if (!addArgs.isEmpty()) {
                sb.append("args-add=").append(addArgs).append("; ");
            }
            if (!removeArgs.isEmpty()) {
                sb.append("args-del=").append(removeArgs).append("; ");
            }
            sb.append("errors=").append(expectedErrors);
            return sb.toString();
        }

        private static final String DEFAULT_APP_DESC = "Hello";
    }

    private static TestSpec.Builder testSpec() {
        return new TestSpec.Builder();
    }

    public static Collection<Object[]> basic() {
        final List<TestSpec> testCases = new ArrayList<>();

        testCases.addAll(Stream.of(
            // non-existent arg
            testSpec().addArgs("--no-such-argument")
                    .error("ERR_InvalidOption", "--no-such-argument"),
            // no main jar
            testSpec().removeArgs("--main-jar").error("ERR_NoEntryPoint"),
            // no main-class
            testSpec().removeArgs("--main-class")
                    .error("error.no-main-class-with-main-jar", "hello.jar")
                    .error("error.no-main-class-with-main-jar.advice", "hello.jar"),
            // non-existent main jar
            testSpec().addArgs("--main-jar", "non-existent.jar")
                    .error("error.main-jar-does-not-exist", "non-existent.jar"),
            // non-existent runtime
            testSpec().addArgs("--runtime-image", "non-existent.runtime")
                    .error("message.runtime-image-dir-does-not-exist", "runtime-image", "non-existent.runtime"),
            // non-existent app image
            testSpec().noAppDesc().nativeType().addArgs("--name", "foo", "--app-image", "non-existent.appimage")
                    .error("ERR_AppImageNotExist", "non-existent.appimage"),
            // non-existent resource-dir
            testSpec().addArgs("--resource-dir", "non-existent.dir")
                    .error("message.resource-dir-does-not-exist", "resource-dir", "non-existent.dir"),
            // non-existent icon
            testSpec().addArgs("--icon", "non-existent.icon")
                    .error("ERR_IconFileNotExit", cannedAbsolutePath("non-existent.icon")),
            // non-existent license file
            testSpec().nativeType().addArgs("--license-file", "non-existent.license")
                    .error("ERR_LicenseFileNotExit"),
            // invalid type
            testSpec().addArgs("--type", "invalid-type")
                    .error("ERR_InvalidInstallerType", "invalid-type"),
            // no --input for non-mudular app
            testSpec().removeArgs("--input").error("error.no-input-parameter"),
            // no --module-path
            testSpec().appDesc("com.other/com.other.Hello").removeArgs("--module-path")
                    .error("ERR_MissingArgument", "--runtime-image or --module-path"),
            // no main class in module path
            testSpec().noAppDesc().addArgs("--module", "java.base", "--runtime-image", Token.JAVA_HOME.token())
                    .error("ERR_NoMainClass"),
            // no module in module path
            testSpec().noAppDesc().addArgs("--module", "com.foo.bar", "--runtime-image", Token.JAVA_HOME.token())
                    .error("error.no-module-in-path", "com.foo.bar"),
            // --main-jar and --module-name
            testSpec().noAppDesc().addArgs("--main-jar", "foo.jar", "--module", "foo.bar")
                    .error("ERR_BothMainJarAndModule"),
            // non-existing argument file
            testSpec().noAppDesc().notype().addArgs("@foo")
                    .error("ERR_CannotParseOptions", "foo"),
            // invalid jlink option
            testSpec().addArgs("--jlink-options", "--foo")
                    .error("error.jlink.failed", "Error: unknown option: --foo")
        ).map(TestSpec.Builder::create).toList());

        // forbidden jlink options
        testCases.addAll(Stream.of("--output", "--add-modules", "--module-path").map(opt -> {
            return testSpec().addArgs("--jlink-options", opt).error("error.blocked.option", opt);
        }).map(TestSpec.Builder::create).toList());

        // --runtime-image and --app-image are mutually-exclusive
        testCases.addAll(createRuntimeMutuallyExclusive("--app-image", "app-image"));
        // --runtime-image and --app-modules are mutually-exclusive
        testCases.addAll(createRuntimeMutuallyExclusive("--add-modules", "foo.bar", "--module", "foo.bar"));
        // --runtime-image and --jlink-options are mutually-exclusive
        testCases.addAll(createRuntimeMutuallyExclusive("--jlink-options", "--bind-services", "--module", "foo.bar"));

        return toTestArgs(testCases.stream());
    }

    record ArgumentGroup(String arg, String... otherArgs) {
        ArgumentGroup {
            Objects.requireNonNull(arg);
            List.of(otherArgs).forEach(Objects::requireNonNull);
        }

        String[] asArray() {
            return Stream.concat(Stream.of(arg), Stream.of(otherArgs)).toArray(String[]::new);
        }
    }

    private static List<TestSpec> createRuntimeMutuallyExclusive(String arg, String... otherArgs) {
        return createMutuallyExclusive(
                new ArgumentGroup("--runtime-image", Token.JAVA_HOME.token()),
                new ArgumentGroup(arg, otherArgs)
        ).map(TestSpec.Builder::noAppDesc).map(TestSpec.Builder::nativeType).map(TestSpec.Builder::create).toList();
    }

    private static Stream<TestSpec.Builder> createMutuallyExclusive(ArgumentGroup firstGroup, ArgumentGroup secondGroup) {
        final Supplier<TestSpec.Builder> createBuilder = () -> {
            return testSpec().error("ERR_MutuallyExclusiveOptions", firstGroup.arg(), secondGroup.arg());
        };
        return Stream.of(
                createBuilder.get().addArgs(firstGroup.asArray()).addArgs(secondGroup.asArray()),
                createBuilder.get().addArgs(secondGroup.asArray()).addArgs(firstGroup.asArray()));
    }

    public static Collection<Object[]> invalidAppVersion() {
        return fromTestSpecBuilders(Stream.of(
                // Invalid app version. Just cover all different error messages.
                // Extensive testing of invalid version strings is done in DottedVersionTest unit test.
                testSpec().addArgs("--app-version", "").error("error.version-string-empty"),
                testSpec().addArgs("--app-version", "1.").error("error.version-string-zero-length-component", "1."),
                testSpec().addArgs("--app-version", "1.b.3").error("error.version-string-invalid-component", "1.b.3", "b.3")
        ));
    }

    @Test
    @ParameterSupplier("basic")
    @ParameterSupplier("testRuntimeInstallerInvalidOptions")
    @ParameterSupplier(value="testWindows", ifOS = WINDOWS)
    @ParameterSupplier(value="testMac", ifOS = MACOS)
    @ParameterSupplier(value="testLinux", ifOS = LINUX)
    @ParameterSupplier(value="winOption", ifNotOS = WINDOWS)
    @ParameterSupplier(value="linuxOption", ifNotOS = LINUX)
    @ParameterSupplier(value="macOption", ifNotOS = MACOS)
    @ParameterSupplier(value="invalidAppVersion", ifOS = {WINDOWS,MACOS})
    public static void test(TestSpec spec) {
        spec.test();
    }

    public static Collection<Object[]> testRuntimeInstallerInvalidOptions() {
        Stream<List<String>> argsStream = Stream.of(
                List.of("--input", "foo"),
                List.of("--module-path", "dir"),
                List.of("--add-modules", "java.base"),
                List.of("--main-class", "Hello"),
                List.of("--arguments", "foo"),
                List.of("--java-options", "-Dfoo.bar=10"),
                List.of("--add-launcher", "foo=foo.properties"),
                List.of("--app-content", "dir"));

        if (TKit.isWindows()) {
            argsStream = Stream.concat(argsStream, Stream.of(List.of("--win-console")));
        }

        return fromTestSpecBuilders(argsStream.map(args -> {
            return testSpec().noAppDesc().nativeType()
                    .addArgs("--runtime-image", Token.JAVA_HOME.token())
                    .addArgs(args)
                    .error("ERR_NoInstallerEntryPoint", args.getFirst());
        }));
    }

    @Test
    @ParameterSupplier
    public static void testAdditionLaunchers(TestSpec spec) {
        final Path propsFile = TKit.createTempFile("add-launcher.properties");
        TKit.createPropertiesFile(propsFile, Map.of());
        spec.test(Map.of(Token.ADD_LAUNCHER_PROPERTY_FILE, cmd -> propsFile));
    }

    public static Collection<Object[]> testAdditionLaunchers() {
        return fromTestSpecBuilders(Stream.of(
            testSpec().addArgs("--add-launcher", Token.ADD_LAUNCHER_PROPERTY_FILE.token())
                    .error("ERR_NoAddLauncherName"),
            testSpec().removeArgs("--name").addArgs("--name", "foo", "--add-launcher", "foo=" + Token.ADD_LAUNCHER_PROPERTY_FILE.token())
                    .error("ERR_NoUniqueName")
        ));
    }

    @Test
    @ParameterSupplier("invalidNames")
    public static void testInvalidAppName(String name) {
        testSpec().removeArgs("--name").addArgs("--name", name)
                .error("ERR_InvalidAppName", adjustTextStreamVerifierArg(name)).create().test();
    }

    @Test
    @ParameterSupplier("invalidNames")
    public static void testInvalidAddLauncherName(String name) {
        testAdditionLaunchers(testSpec()
                .addArgs("--add-launcher", name + "=" + Token.ADD_LAUNCHER_PROPERTY_FILE.token())
                .error("ERR_InvalidSLName", adjustTextStreamVerifierArg(name))
                .create());
    }

    public static Collection<Object[]> invalidNames() {
        final List<String> data = new ArrayList<>();
        data.addAll(List.of("", "foo/bar", "foo\tbar", "foo\rbar", "foo\nbar"));
        if (TKit.isWindows()) {
            data.add("foo\\bar");
        }
        return toTestArgs(data.stream());
    }

    public static Collection<Object[]> testWindows() {
        final List<TestSpec> testCases = new ArrayList<>();

        testCases.addAll(PackageType.WINDOWS.stream().map(type -> {
            return Stream.of(
                    testSpec().type(type).addArgs("--launcher-as-service")
                            .error("error.missing-service-installer")
                            .error("error.missing-service-installer.advice"),
                    // The below version strings are invalid for msi and exe packaging.
                    // They are valid for app image packaging.
                    testSpec().type(type).addArgs("--app-version", "1234")
                            .error("error.msi-product-version-components", "1234")
                            .error("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "1.2.3.4.5")
                            .error("error.msi-product-version-components", "1.2.3.4.5")
                            .error("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "256.1")
                            .error("error.msi-product-version-major-out-of-range", "256.1")
                            .error("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "1.256")
                            .error("error.msi-product-version-minor-out-of-range", "1.256")
                            .error("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "1.2.65536")
                            .error("error.msi-product-version-build-out-of-range", "1.2.65536")
                            .error("error.version-string-wrong-format.advice")
            );
        }).flatMap(x -> x).map(TestSpec.Builder::create).toList());

        return toTestArgs(testCases.stream());
    }

    public static Collection<Object[]> testMac() {
        final List<TestSpec> testCases = new ArrayList<>();

        testCases.addAll(Stream.of(
                testSpec().addArgs("--app-version", "0.2")
                        .error("message.version-string-first-number-not-zero")
                        .error("error.invalid-cfbundle-version.advice"),
                testSpec().addArgs("--app-version", "1.2.3.4")
                        .error("message.version-string-too-many-components")
                        .error("error.invalid-cfbundle-version.advice"),
                testSpec().invalidTypeArg("--mac-installer-sign-identity", "foo"),
                testSpec().type(PackageType.MAC_DMG).invalidTypeArg("--mac-installer-sign-identity", "foo"),
                testSpec().invalidTypeArg("--mac-dmg-content", "foo"),
                testSpec().type(PackageType.MAC_PKG).invalidTypeArg("--mac-dmg-content", "foo"),
                testSpec().noAppDesc().addArgs("--app-image", Token.APP_IMAGE.token())
                        .error("error.app-image.mac-sign.required"),
                testSpec().type(PackageType.MAC_PKG).addArgs("--mac-package-identifier", "#1")
                        .error("message.invalid-identifier", "#1"),
                // Bundle for mac app store should not have runtime commands
                testSpec().nativeType().addArgs("--mac-app-store", "--jlink-options", "--bind-services")
                        .error("ERR_MissingJLinkOptMacAppStore", "--strip-native-commands"),
                testSpec().nativeType().addArgs("--mac-app-store", "--runtime-image", Token.JAVA_HOME.token())
                        .error("ERR_MacAppStoreRuntimeBinExists", JPackageCommand.cannedArgument(cmd -> {
                            return Path.of(cmd.getArgumentValue("--runtime-image")).toAbsolutePath();
                        }, Token.JAVA_HOME.token()))
        ).map(TestSpec.Builder::create).toList());

        // Test a few app-image options that should not be used when signing external app image
        testCases.addAll(Stream.of(
                new ArgumentGroup("--app-version", "2.0"),
                new ArgumentGroup("--name", "foo"),
                new ArgumentGroup("--mac-app-store")
        ).map(argGroup -> {
            return testSpec().noAppDesc().addArgs(argGroup.asArray()).addArgs("--app-image", Token.APP_IMAGE.token())
                    .error("ERR_InvalidOptionWithAppImageSigning", argGroup.arg());
         // It should bail out with the same error message regardless of `--mac-sign` option.
        }).mapMulti(ErrorTest::duplicateForMacSign).toList());

        testCases.addAll(createMutuallyExclusive(
                new ArgumentGroup("--mac-signing-key-user-name", "foo"),
                new ArgumentGroup("--mac-app-image-sign-identity", "bar")
        ).mapMulti(ErrorTest::duplicateForMacSign).toList());

        testCases.addAll(createMutuallyExclusive(
                new ArgumentGroup("--mac-signing-key-user-name", "foo"),
                new ArgumentGroup("--mac-installer-sign-identity", "bar")
        ).map(TestSpec.Builder::nativeType).mapMulti(ErrorTest::duplicateForMacSign).toList());

        return toTestArgs(testCases.stream());
    }

    public static Collection<Object[]> testLinux() {
        final List<TestSpec> testCases = new ArrayList<>();

        testCases.addAll(Stream.of(
                testSpec().type(PackageType.LINUX_DEB).addArgs("--linux-package-name", "#")
                        .error("error.deb-invalid-value-for-package-name", "#")
                        .error("error.deb-invalid-value-for-package-name.advice"),
                testSpec().type(PackageType.LINUX_RPM).addArgs("--linux-package-name", "#")
                        .error("error.rpm-invalid-value-for-package-name", "#")
                        .error("error.rpm-invalid-value-for-package-name.advice")
        ).map(TestSpec.Builder::create).toList());

        return toTestArgs(testCases.stream());
    }

    @Test(ifOS = MACOS)
    @Parameter({"MAC_PKG", "--mac-signing-key-user-name", "false"})
    @Parameter({"MAC_DMG", "--mac-signing-key-user-name", "false"})
    @Parameter({"IMAGE", "--mac-signing-key-user-name", "false"})
    @Parameter({"MAC_PKG", "--mac-app-image-sign-identity", "true"})
    @Parameter({"MAC_DMG", "--mac-app-image-sign-identity", "true"})
    @Parameter({"IMAGE", "--mac-app-image-sign-identity", "true"})
    @Parameter({"MAC_PKG", "--mac-installer-sign-identity", "true"})
    public static void testMacSigningIdentityValidation(PackageType type, String option, boolean passThroughOption) {

        final var signingId = "foo";

        final List<CannedFormattedString> errorMessages = new ArrayList<>();
        errorMessages.add(JPackageStringBundle.MAIN.cannedFormattedString(
                "error.cert.not.found", "Developer ID Application: " + signingId, ""));
        errorMessages.addAll(Stream.of(
                "error.explicit-sign-no-cert",
                "error.explicit-sign-no-cert.advice"
        ).map(JPackageStringBundle.MAIN::cannedFormattedString).toList());

        final var cmd = JPackageCommand.helloAppImage()
                .ignoreDefaultVerbose(true)
                .addArguments("--mac-sign")
                .addArguments(option, signingId)
                .setPackageType(type);

        if (passThroughOption) {
            errorMessages.stream()
                    .map(CannedFormattedString::getValue)
                    .map(TKit::assertTextStream)
                    .map(TKit.TextStreamVerifier::negate).forEach(cmd::validateOutput);
        } else {
            cmd.validateOutput(errorMessages.toArray(CannedFormattedString[]::new));
        }

        cmd.execute(1);
    }

    private static void duplicate(TestSpec.Builder builder, Consumer<TestSpec> accumulator, Consumer<TestSpec.Builder> mutator) {
        accumulator.accept(builder.create());
        mutator.accept(builder);
        accumulator.accept(builder.create());
    }

    private static void duplicateAddArgs(TestSpec.Builder builder, Consumer<TestSpec> accumulator, String...args) {
        duplicate(builder, accumulator, b -> b.addArgs(args));
    }

    private static void duplicateForMacSign(TestSpec.Builder builder, Consumer<TestSpec> accumulator) {
        duplicateAddArgs(builder, accumulator, "--mac-sign");
    }

    private record UnsupportedPlatformOption(String name, Optional<String> value) {
        UnsupportedPlatformOption {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }

        UnsupportedPlatformOption(String name) {
            this(name, Optional.empty());
        }

        UnsupportedPlatformOption(String name, String value) {
            this(name, Optional.of(value));
        }

        TestSpec toTestSpec() {
            return value.map(v -> testSpec().unsupportedPlatformOption(name, v)).orElseGet(
                    () -> testSpec().unsupportedPlatformOption(name)).create();
        }

        static Collection<Object[]> createTestArgs(UnsupportedPlatformOption... options) {
            return toTestArgs(Stream.of(options).map(UnsupportedPlatformOption::toTestSpec));
        }
    }

    public static Collection<Object[]> winOption() {
        return UnsupportedPlatformOption.createTestArgs(
                new UnsupportedPlatformOption("--win-console"),
                new UnsupportedPlatformOption("--win-dir-chooser"),
                new UnsupportedPlatformOption("--win-help-url", "url"),
                new UnsupportedPlatformOption("--win-menu"),
                new UnsupportedPlatformOption("--win-menu-group", "name"),
                new UnsupportedPlatformOption("--win-per-user-install"),
                new UnsupportedPlatformOption("--win-shortcut"),
                new UnsupportedPlatformOption("--win-shortcut-prompt"),
                new UnsupportedPlatformOption("--win-update-url", "url"),
                new UnsupportedPlatformOption("--win-upgrade-uuid", "uuid")
        );
    }

    public static Collection<Object[]> linuxOption() {
        return UnsupportedPlatformOption.createTestArgs(
                new UnsupportedPlatformOption("--linux-package-name", "name"),
                new UnsupportedPlatformOption("--linux-deb-maintainer", "email-address"),
                new UnsupportedPlatformOption("--linux-menu-group", "menu-group-name"),
                new UnsupportedPlatformOption("--linux-package-deps", "deps"),
                new UnsupportedPlatformOption("--linux-rpm-license-type", "type"),
                new UnsupportedPlatformOption("--linux-app-release", "release"),
                new UnsupportedPlatformOption("--linux-app-category", "category-value"),
                new UnsupportedPlatformOption("--linux-shortcut")
        );
    }

    public static Collection<Object[]> macOption() {
        return UnsupportedPlatformOption.createTestArgs(
                new UnsupportedPlatformOption("--mac-package-identifier", "identifier"),
                new UnsupportedPlatformOption("--mac-package-name", "name"),
                new UnsupportedPlatformOption("--mac-package-signing-prefix", "prefix"),
                new UnsupportedPlatformOption("--mac-sign"),
                new UnsupportedPlatformOption("--mac-signing-keychain", "keychain-name"),
                new UnsupportedPlatformOption("--mac-signing-key-user-name", "name"),
                new UnsupportedPlatformOption("--mac-app-store"),
                new UnsupportedPlatformOption("--mac-entitlements", "path"),
                new UnsupportedPlatformOption("--mac-app-category", "category"),
                new UnsupportedPlatformOption("--mac-dmg-content", "additional-content")
        );
    }

    private static void defaultInit(JPackageCommand cmd, List<CannedFormattedString> expectedErrors) {

        // Disable default logic adding `--verbose` option
        // to jpackage command line.
        // It will affect jpackage error messages if the command line is malformed.
        cmd.ignoreDefaultVerbose(true);

        // Ignore external runtime as it will interfere
        // with jpackage arguments in this test.
        cmd.ignoreDefaultRuntime(true);

        cmd.validateOutput(expectedErrors.toArray(CannedFormattedString[]::new));
    }

    private static <T> Collection<Object[]> toTestArgs(Stream<T> stream) {
        return stream.filter(v -> {
            if (v instanceof TestSpec ts) {
                return ts.isSupported();
            } else {
                return true;
            }
        }).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    private static Collection<Object[]> fromTestSpecBuilders(Stream<TestSpec.Builder> stream) {
        return toTestArgs(stream.map(TestSpec.Builder::create));
    }

    private static String adjustTextStreamVerifierArg(String str) {
        return LINE_SEP_REGEXP.split(str)[0];
    }

    private static final Pattern LINE_SEP_REGEXP = Pattern.compile("\\R");
}
