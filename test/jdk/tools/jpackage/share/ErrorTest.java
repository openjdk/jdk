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


import static java.util.stream.Collectors.toMap;
import static jdk.internal.util.OperatingSystem.LINUX;
import static jdk.internal.util.OperatingSystem.MACOS;
import static jdk.internal.util.OperatingSystem.WINDOWS;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.test.JPackageCommand.makeAdvice;
import static jdk.jpackage.test.JPackageCommand.makeError;

import java.nio.file.Files;
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
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.TokenReplace;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedArgument;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageOutputValidator;
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
        INVALID_MAC_RUNTIME_BUNDLE(toFunction(cmd -> {
            // Has "Contents/MacOS/libjli.dylib", but missing "Contents/Home/lib/libjli.dylib".
            final Path root = TKit.createTempDirectory("mac-invalid-runtime-bundle");
            Files.createDirectories(root.resolve("Contents/Home"));
            Files.createFile(root.resolve("Contents/Info.plist"));
            Files.createDirectories(root.resolve("Contents/MacOS"));
            Files.createFile(root.resolve("Contents/MacOS/libjli.dylib"));
            return root.toString();
        })),
        INVALID_MAC_RUNTIME_IMAGE(toFunction(cmd -> {
            // Has some files in the "lib" subdirectory, but doesn't have the "lib/libjli.dylib" file.
            final Path root = TKit.createTempDirectory("mac-invalid-runtime-image");
            Files.createDirectories(root.resolve("lib"));
            Files.createFile(root.resolve("lib/foo"));
            return root.toString();
        })),
        EMPTY_DIR(toFunction(cmd -> {
            return TKit.createTempDirectory("empty-dir");
        })),
        ADD_LAUNCHER_PROPERTY_FILE,
        ;

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

    record PackageTypeSpec(Optional<PackageType> type, boolean anyNativeType) implements CannedArgument {
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
        public String getValue() {
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

    public record TestSpec(
            Optional<PackageTypeSpec> type,
            Optional<String> appDesc,
            List<String> addArgs,
            List<String> removeArgs,
            List<CannedFormattedString> expectedMessages,
            boolean match) {

        static final class Builder {

            Builder() {
                type = new PackageTypeSpec(PackageType.IMAGE);
                appDesc = DEFAULT_APP_DESC;
                match = true;
            }

            Builder(Builder other) {
                type = other.type;
                appDesc = other.appDesc;
                match = other.match;
                addArgs.addAll(other.addArgs);
                removeArgs.addAll(other.removeArgs);
                expectedMessages.addAll(other.expectedMessages);
            }

            Builder copy() {
                return new Builder(this);
            }

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

            Builder match(boolean v) {
                match = v;
                return this;
            }

            Builder match() {
                return match(true);
            }

            Builder find() {
                return match(false);
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

            Builder setMessages(List<CannedFormattedString> v) {
                expectedMessages.clear();
                expectedMessages.addAll(v);
                return this;
            }

            Builder setMessages(CannedFormattedString... v) {
                return setMessages(List.of(v));
            }

            Builder messages(List<CannedFormattedString> v) {
                expectedMessages.addAll(v);
                return this;
            }

            Builder messages(CannedFormattedString... v) {
                return messages(List.of(v));
            }

            Builder error(String key, Object ... args) {
                return messages(makeError(key, args));
            }

            Builder advice(String key, Object ... args) {
                return messages(makeAdvice(key, args));
            }

            Builder invalidTypeArg(String arg, String... otherArgs) {
                Objects.requireNonNull(type);
                return addArgs(arg).addArgs(otherArgs).error("ERR_InvalidTypeOption", arg, type);
            }

            Builder unsupportedPlatformOption(String arg, String ... otherArgs) {
                return addArgs(arg).addArgs(otherArgs).error("ERR_UnsupportedOption", arg);
            }

            TestSpec create() {
                return new TestSpec(
                        Optional.ofNullable(type),
                        Optional.ofNullable(appDesc),
                        List.copyOf(addArgs),
                        List.copyOf(removeArgs),
                        List.copyOf(expectedMessages),
                        match);
            }

            private PackageTypeSpec type;
            private String appDesc;
            private boolean match;
            private final List<String> addArgs = new ArrayList<>();
            private final List<String> removeArgs = new ArrayList<>();
            private final List<CannedFormattedString> expectedMessages = new ArrayList<>();
        }

        public TestSpec {
            Objects.requireNonNull(type);
            Objects.requireNonNull(appDesc);
            Objects.requireNonNull(addArgs);
            addArgs.forEach(Objects::requireNonNull);
            Objects.requireNonNull(removeArgs);
            removeArgs.forEach(Objects::requireNonNull);
            if (expectedMessages.isEmpty()) {
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

            // Disable default logic adding `--verbose` option
            // to jpackage command line.
            // It will affect jpackage error messages if the command line is malformed.
            cmd.ignoreDefaultVerbose(true);

            // Ignore external runtime as it will interfere
            // with jpackage arguments in this test.
            cmd.ignoreDefaultRuntime(true);

            var validator = new JPackageOutputValidator().stderr().expectMatchingStrings(expectedMessages).match(match);
            if (match) {
                new JPackageOutputValidator().stdout().validateEndOfStream().applyTo(cmd);
            }

            cmd.mutate(validator::applyTo).execute(1);
        }

        TestSpec mapExpectedMessages(UnaryOperator<CannedFormattedString> mapper) {
            return new TestSpec(type, appDesc, addArgs, removeArgs, expectedMessages.stream().map(mapper).toList(), match);
        }

        TestSpec copyWithExpectedMessages(List<CannedFormattedString> expectedMessages) {
            return new TestSpec(type, appDesc, addArgs, removeArgs, expectedMessages, match);
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
            if (!match) {
                sb.append("find; ");
            }
            sb.append("errors=").append(expectedMessages);
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
                    .advice("error.no-main-class-with-main-jar.advice", "hello.jar"),
            // non-existent main jar
            testSpec().addArgs("--main-jar", "non-existent.jar").find()
                    .error("error.main-jar-does-not-exist", "non-existent.jar"),
            // non-existent runtime
            testSpec().addArgs("--runtime-image", "non-existent.runtime")
                    .error("error.parameter-not-directory", "non-existent.runtime", "--runtime-image"),
            // non-existent app image
            testSpec().noAppDesc().nativeType().addArgs("--name", "foo", "--app-image", "non-existent.appimage")
                    .error("error.parameter-not-directory", "non-existent.appimage", "--app-image"),
            // non-existent resource-dir
            testSpec().addArgs("--resource-dir", "non-existent.dir")
                    .error("error.parameter-not-directory", "non-existent.dir", "--resource-dir"),
            // non-existent icon
            testSpec().addArgs("--icon", "non-existent.icon")
                    .error("error.parameter-not-file", "non-existent.icon", "--icon"),
            // non-existent license file
            testSpec().nativeType().addArgs("--license-file", "non-existent.license")
                    .error("error.parameter-not-file", "non-existent.license", "--license-file"),
            // invalid type
            testSpec().addArgs("--type", "invalid-type")
                    .error("ERR_InvalidInstallerType", "invalid-type"),
            // no --input for non-mudular app
            testSpec().removeArgs("--input").error("error.no-input-parameter"),
            // no --module-path
            testSpec().appDesc("com.other/com.other.Hello").removeArgs("--module-path")
                    .error("ERR_MissingArgument2", "--runtime-image", "--module-path"),
            // no main class in module path
            testSpec().noAppDesc().addArgs("--module", "java.base", "--runtime-image", Token.JAVA_HOME.token())
                    .error("ERR_NoMainClass"),
            // no module in module path
            testSpec().noAppDesc().addArgs("--module", "com.foo.bar", "--runtime-image", Token.JAVA_HOME.token())
                    .error("error.no-module-in-path", "com.foo.bar"),
            // non-existing argument file
            testSpec().noAppDesc().notype().addArgs("@foo")
                    .error("ERR_CannotParseOptions", "foo")
        ).map(TestSpec.Builder::create).toList());

        // --main-jar and --module-name
        createMutuallyExclusive(
                new ArgumentGroup("--module", "foo.bar"),
                new ArgumentGroup("--main-jar", "foo.jar")
        ).map(TestSpec.Builder::noAppDesc).map(TestSpec.Builder::find).map(TestSpec.Builder::create).forEach(testCases::add);

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
        ).map(builder -> {
            if (TKit.isOSX()) {
                builder.advice("error.invalid-cfbundle-version.advice");
            };
            return builder;
        }));
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
            var builder = testSpec().noAppDesc().nativeType()
                    .addArgs("--runtime-image", Token.JAVA_HOME.token())
                    .addArgs(args);
            if (args.contains("--add-modules")) {
                builder.error("ERR_MutuallyExclusiveOptions", "--runtime-image", "--add-modules");
            }
            return builder.error("ERR_NoInstallerEntryPoint", args.getFirst());
        }));
    }

    @Test
    @ParameterSupplier
    public static void testAdditionLaunchers(TestSpec spec) {
        final Path propsFile = TKit.createTempFile("add-launcher.properties");
        TKit.createPropertiesFile(propsFile, Map.of());
        spec.mapExpectedMessages(cannedStr -> {
            return cannedStr.mapArgs(arg -> {
                if (arg == Token.ADD_LAUNCHER_PROPERTY_FILE) {
                    return propsFile;
                } else {
                    return arg;
                }
            });
        }).test(Map.of(Token.ADD_LAUNCHER_PROPERTY_FILE, cmd -> propsFile));
    }

    public static Collection<Object[]> testAdditionLaunchers() {
        return fromTestSpecBuilders(Stream.of(
            testSpec().addArgs("--add-launcher", Token.ADD_LAUNCHER_PROPERTY_FILE.token())
                    .error("error.parameter-add-launcher-malformed", Token.ADD_LAUNCHER_PROPERTY_FILE, "--add-launcher"),
            testSpec().removeArgs("--name").addArgs("--name", "foo", "--add-launcher", "foo=" + Token.ADD_LAUNCHER_PROPERTY_FILE.token())
                    .error("error.launcher-duplicate-name", "foo")
        ));
    }

    @Test
    @ParameterSupplier("invalidNames")
    public static void testInvalidAppName(InvalidName name) {
        testSpec().removeArgs("--name").addArgs("--name", name.value())
                .error("ERR_InvalidAppName", adjustTextStreamVerifierArg(name.value()))
                .match(!name.isMessingUpConsoleOutput())
                .create()
                .test();
    }

    @Test
    @ParameterSupplier("invalidNames")
    public static void testInvalidAddLauncherName(InvalidName name) {
        testAdditionLaunchers(testSpec()
                .addArgs("--add-launcher", name + "=" + Token.ADD_LAUNCHER_PROPERTY_FILE.token())
                .error("ERR_InvalidSLName", adjustTextStreamVerifierArg(name.value()))
                .match(!name.isMessingUpConsoleOutput())
                .create());
    }

    public static Collection<Object[]> invalidNames() {
        final List<String> data = new ArrayList<>();
        data.addAll(List.of("", "foo/bar", "foo\tbar", "foo\rbar", "foo\nbar"));
        if (TKit.isWindows()) {
            data.add("foo\\bar");
        }
        return toTestArgs(data.stream().map(InvalidName::new));
    }

    record InvalidName(String value) {
        InvalidName {
            Objects.requireNonNull(value);
        }

        boolean isMessingUpConsoleOutput() {
            var controlChars = "\r\n\t".codePoints().toArray();
            return value.codePoints().anyMatch(cp -> {
                return IntStream.of(controlChars).anyMatch(v -> {
                    return v == cp;
                });
            });
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static Collection<Object[]> testWindows() {
        final List<TestSpec> testCases = new ArrayList<>();

        testCases.addAll(PackageType.WINDOWS.stream().map(type -> {
            return Stream.of(
                    testSpec().type(type).addArgs("--launcher-as-service")
                            .error("error.missing-service-installer")
                            .advice("error.missing-service-installer.advice"),
                    // The below version strings are invalid for msi and exe packaging.
                    // They are valid for app image packaging.
                    testSpec().type(type).addArgs("--app-version", "1234")
                            .error("error.msi-product-version-components", "1234")
                            .advice("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "1.2.3.4.5")
                            .error("error.msi-product-version-components", "1.2.3.4.5")
                            .advice("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "256.1")
                            .error("error.msi-product-version-major-out-of-range", "256.1")
                            .advice("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "1.256")
                            .error("error.msi-product-version-minor-out-of-range", "1.256")
                            .advice("error.version-string-wrong-format.advice"),
                    testSpec().type(type).addArgs("--app-version", "1.2.65536")
                            .error("error.msi-product-version-build-out-of-range", "1.2.65536")
                            .advice("error.version-string-wrong-format.advice")
            );
        }).flatMap(x -> x).map(TestSpec.Builder::create).toList());

        invalidShortcut(testCases::add, "--win-menu");
        invalidShortcut(testCases::add, "--win-shortcut");

        return toTestArgs(testCases.stream());
    }

    public static Collection<Object[]> testMac() {
        final List<TestSpec> testCases = new ArrayList<>();

        testCases.addAll(Stream.of(
                testSpec().addArgs("--app-version", "0.2")
                        .error("message.version-string-first-number-not-zero")
                        .advice("error.invalid-cfbundle-version.advice"),
                testSpec().addArgs("--app-version", "1.2.3.4")
                        .error("message.version-string-too-many-components")
                        .advice("error.invalid-cfbundle-version.advice"),
                testSpec().invalidTypeArg("--mac-installer-sign-identity", "foo"),
                testSpec().type(PackageType.MAC_DMG).invalidTypeArg("--mac-installer-sign-identity", "foo"),
                testSpec().invalidTypeArg("--mac-dmg-content", "foo"),
                testSpec().type(PackageType.MAC_PKG).invalidTypeArg("--mac-dmg-content", "foo"),
                testSpec().noAppDesc().addArgs("--app-image", Token.APP_IMAGE.token())
                        .error("error.app-image.mac-sign.required"),
                testSpec().type(PackageType.MAC_PKG).addArgs("--mac-package-identifier", "#1")
                        .error("message.invalid-identifier", "#1")
                        .advice("message.invalid-identifier.advice"),
                // Bundle for mac app store should not have runtime commands
                testSpec().nativeType().addArgs("--mac-app-store", "--jlink-options", "--bind-services")
                        .error("ERR_MissingJLinkOptMacAppStore", "--strip-native-commands"),
                // Predefined app image must be a valid macOS bundle.
                testSpec().noAppDesc().nativeType().addArgs("--app-image", Token.EMPTY_DIR.token())
                        .error("error.parameter-not-mac-bundle", JPackageCommand.cannedArgument(cmd -> {
                            return Path.of(cmd.getArgumentValue("--app-image"));
                        }, Token.EMPTY_DIR.token()), "--app-image")
        ).map(TestSpec.Builder::create).toList());

        macInvalidRuntime(testCases::add);

        // Test a few app-image options that should not be used when signing external app image
        testCases.addAll(Stream.of(
                new ArgumentGroup("--app-version", "2.0"),
                new ArgumentGroup("--name", "foo"),
                new ArgumentGroup("--mac-app-store")
        ).flatMap(argGroup -> {
            var withoutSign = testSpec()
                    .noAppDesc()
                    .addArgs(argGroup.asArray())
                    .addArgs("--app-image", Token.APP_IMAGE.token());

            var withSign = withoutSign.copy().addArgs("--mac-sign");

            withoutSign.error("error.app-image.mac-sign.required");

            // It should bail out with the same error message regardless of `--mac-sign` option.
            return Stream.of(withoutSign, withSign).map(builder -> {
                return builder.error("ERR_InvalidOptionWithAppImageSigning", argGroup.arg());
            });

        }).map(TestSpec.Builder::create).toList());

        testCases.addAll(createMutuallyExclusive(
                new ArgumentGroup("--mac-signing-key-user-name", "foo"),
                new ArgumentGroup("--mac-app-image-sign-identity", "bar")
        ).mapMulti(ErrorTest::duplicateForMacSign).toList());

        for (var packageType : PackageType.MAC) {
            testCases.addAll(createMutuallyExclusive(
                    new ArgumentGroup("--mac-signing-key-user-name", "foo"),
                    new ArgumentGroup("--mac-installer-sign-identity", "bar")
            ).map(builder -> {
                return builder.type(packageType);
            }).mapMulti(ErrorTest::duplicateForMacSign).map(testCase -> {
                if (packageType != PackageType.MAC_PKG) {
                    /*
                     * This is a bit tricky.
                     * The error output should also contain
                     *
                     *  Error: Option [--mac-installer-sign-identity] is not valid with type [dmg]" error message.
                     *
                     * The order of errors is defined by the order of options on the command line causing them.
                     * If "--mac-installer-sign-identity" goes before "--mac-signing-key-user-name", the error output will be:
                     *
                     *  Error: Option [--mac-installer-sign-identity] is not valid with type [dmg]
                     *  Error: Mutually exclusive options [--mac-signing-key-user-name] and [--mac-installer-sign-identity]
                     *
                     * otherwise errors in the output will be in reverse order.
                     */
                    var expectedMessages = new ArrayList<>(testCase.expectedMessages());
                    var invalidTypeOption = makeError("ERR_InvalidTypeOption", "--mac-installer-sign-identity", packageType.getType());
                    if (testCase.addArgs().indexOf("--mac-installer-sign-identity") < testCase.addArgs().indexOf("--mac-signing-key-user-name")) {
                        expectedMessages.addFirst(invalidTypeOption);
                    } else {
                        expectedMessages.add(invalidTypeOption);
                    }
                    testCase = testCase.copyWithExpectedMessages(expectedMessages);
                }
                return testCase;
            }).toList());
        }

        return toTestArgs(testCases.stream());
    }

    public static Collection<Object[]> testLinux() {
        final List<TestSpec> testCases = new ArrayList<>();

        testCases.addAll(Stream.of(
                testSpec().type(PackageType.LINUX_DEB).addArgs("--linux-package-name", "#")
                        .error("error.deb-invalid-value-for-package-name", "#")
                        .advice("error.deb-invalid-value-for-package-name.advice"),
                testSpec().type(PackageType.LINUX_RPM).addArgs("--linux-package-name", "#")
                        .error("error.rpm-invalid-value-for-package-name", "#")
                        .advice("error.rpm-invalid-value-for-package-name.advice")
        ).map(TestSpec.Builder::create).toList());

        invalidShortcut(testCases::add, "--linux-shortcut");

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
        errorMessages.add(makeError("error.cert.not.found", "Developer ID Application: " + signingId, ""));

        final var cmd = JPackageCommand.helloAppImage()
                .ignoreDefaultVerbose(true)
                .addArguments("--mac-sign")
                .addArguments(option, signingId)
                .setPackageType(type);

        if (passThroughOption) {
            errorMessages.stream()
                    .map(CannedFormattedString::getValue)
                    .map(TKit::assertTextStream)
                    .map(TKit.TextStreamVerifier::negate).forEach(cmd::validateErr);
        } else {
            cmd.validateErr(errorMessages.toArray(CannedFormattedString[]::new));
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

    private static void invalidShortcut(Consumer<TestSpec> accumulator, String shortcutOption) {
        Objects.requireNonNull(shortcutOption);
        Stream.of("true", "false", "").map(value -> {
            return testSpec().nativeType().addArgs(shortcutOption, value).error("error.parameter-not-launcher-shortcut-dir", value, shortcutOption).create();
        }).forEach(accumulator);
    }

    private static void macInvalidRuntime(Consumer<TestSpec> accumulator) {
        var runtimeWithBinDirErr = makeError(
                "error.invalid-runtime-image-bin-dir", JPackageCommand.cannedArgument(cmd -> {
                    return Path.of(cmd.getArgumentValue("--runtime-image"));
                }, Token.JAVA_HOME.token()));
        var runtimeWithBinDirErrAdvice = makeAdvice(
                "error.invalid-runtime-image-bin-dir.advice", "--mac-app-store");

        Stream.of(
                testSpec().nativeType().addArgs("--mac-app-store", "--runtime-image", Token.JAVA_HOME.token())
                        .messages(runtimeWithBinDirErr, runtimeWithBinDirErrAdvice)
        ).map(TestSpec.Builder::create).forEach(accumulator);

        Stream.of(
                Token.INVALID_MAC_RUNTIME_BUNDLE,
                Token.EMPTY_DIR,
                Token.INVALID_MAC_RUNTIME_IMAGE
        ).map(MissingRuntimeFileError::missingLibjli).forEach(mapper -> {
            Stream.of(
                    testSpec(),
                    testSpec().nativeType(),
                    testSpec().nativeType().noAppDesc()
            ).map(mapper::applyTo).map(TestSpec.Builder::create).forEach(accumulator);
        });
    }

    private record MissingRuntimeFileError(Token runtimeDir, String missingFile) {

        MissingRuntimeFileError {
            Objects.requireNonNull(runtimeDir);
            Objects.requireNonNull(missingFile);
        }

        static MissingRuntimeFileError missingLibjli(Token runtimeDir) {
            if (runtimeDir == Token.INVALID_MAC_RUNTIME_BUNDLE) {
                return new MissingRuntimeFileError(runtimeDir, "Contents/Home/lib/**/libjli.dylib");
            } else {
                return new MissingRuntimeFileError(runtimeDir, "lib/**/libjli.dylib");
            }
        }

        TestSpec.Builder applyTo(TestSpec.Builder builder) {
            return builder.addArgs("--runtime-image", runtimeDir.token()).messages(expectedErrorMsg());
        }

        private CannedFormattedString expectedErrorMsg() {
            return makeError(
                    "error.invalid-runtime-image-missing-file", JPackageCommand.cannedArgument(cmd -> {
                        return Path.of(cmd.getArgumentValue("--runtime-image"));
                    }, runtimeDir.token()), missingFile);
        }
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
