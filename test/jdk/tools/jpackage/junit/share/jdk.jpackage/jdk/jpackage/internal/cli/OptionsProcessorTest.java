/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static jdk.jpackage.internal.cli.TestUtils.assertExceptionListEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.StandardOption.AddLauncherInvalidPropertyFileException;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.test.JUnitUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public class OptionsProcessorTest {

    @Test
    public void test_processPropertyFile(@TempDir Path workDir) throws IOException {
        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("description=bar", "type=msi"));

        final var result = processPropertyFile(propFile,
                StandardOption.TYPE, StandardOption.DESCRIPTION);

        assertTrue(result.hasValue());

        assertEquals(StandardPackageType.WIN_MSI, StandardOption.TYPE.getFrom(result.orElseThrow()));
        assertEquals("bar", StandardOption.DESCRIPTION.getFrom(result.orElseThrow()));

        assertFalse(StandardOption.INPUT.containsIn(result.orElseThrow()));

        assertEquals(propFile, StandardOption.SOURCE_PROPERY_FILE.getFrom(result.orElseThrow()));
    }

    @ParameterizedTest
    @EnumSource(BooleanProperty.class)
    public void test_processPropertyFile_Boolean(BooleanProperty value, @TempDir Path workDir) throws IOException {
        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("launcher-as-service=" + value.propertyValue()));

        final var props = processPropertyFile(propFile,
                StandardOption.LAUNCHER_AS_SERVICE).orElseThrow();

        assertEquals(value.expectedValue(), StandardOption.LAUNCHER_AS_SERVICE.getFrom(props));
    }

    @Test
    public void testMandatoryOptionsPresent(@TempDir Path workDir) {
        build().createAppImageByDefault().withMockupMainJar(workDir).createBundleCallback(cmdline -> {
            assertEquals(List.of(), StandardOption.ADDITIONAL_LAUNCHERS.getFrom(cmdline));
            assertEquals(List.of(), StandardOption.FILE_ASSOCIATIONS.getFrom(cmdline));
        }).create().execute();
    }

    @Test
    public void testAdditionLauncher(@TempDir Path workDir) throws IOException {

        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("arguments=a b c"));

        build().createAppImageByDefault().withMockupMainJar(workDir).createBundleCallback(cmdline -> {
            assertFalse(StandardOption.ADD_LAUNCHER_INTERNAL.containsIn(cmdline));

            var addLaunchers = StandardOption.ADDITIONAL_LAUNCHERS.getFrom(cmdline);
            assertEquals(1, addLaunchers.size());

            var addLauncher = addLaunchers.getFirst();

            assertEquals("foo", StandardOption.NAME.getFrom(addLauncher));
            assertEquals(propFile, StandardOption.SOURCE_PROPERY_FILE.getFrom(addLauncher));
            assertEquals(List.of("a", "b", "c"), StandardOption.ARGUMENTS.getFrom(addLauncher));

        }).create("--add-launcher", "foo=" + propFile).execute();
    }

    @Test
    public void testFa(@TempDir Path workDir) throws IOException {

        final var propFile = workDir.resolve("fa.properties");
        Files.write(propFile, List.of("description=Hello"));

        build().createAppImageByDefault().withMockupMainJar(workDir).createBundleCallback(cmdline -> {
            assertFalse(StandardOption.FILE_ASSOCIATIONS_INTERNAL.containsIn(cmdline));

            var fas = StandardOption.FILE_ASSOCIATIONS.getFrom(cmdline);
            assertEquals(1, fas.size());

            var fa = fas.getFirst();

            assertEquals("Hello", StandardFaOption.DESCRIPTION.getFrom(fa));
            assertEquals(propFile, StandardOption.SOURCE_PROPERY_FILE.getFrom(fa));

        }).create("--file-associations=" + propFile).execute();
    }

    /**
     * Test that it fails if there are "non-option" arguments on the command line.
     */
    @Test
    public void testNonOptionArgumntsError(@TempDir Path workDir) {

        build()
        .withDefaultBundlingOperation(true)
        .createAppImageBundlingOperation()
        .expectValidationErrors(
                new JPackageException(I18N.format("error.non-option-arguments", 1)),
                new JPackageException(I18N.format("ERR_NoEntryPoint"))
        ).create("foo").validate();

        build()
        .createAppImageBundlingOperation()
        .expectValidationErrors(
                new JPackageException(I18N.format("error.non-option-arguments", 3)),
                new JPackageException(I18N.format("ERR_NoEntryPoint"))
        ).create("some", "-t", "app-image", "foo", "bar", "--dest", "dir").validate();
    }

    /**
     * Test that it fails as expected when `--type` option is missing and the
     * bundling environment doesn't have the default bundling operation.
     */
    @Test
    public void testNoDefaultBundlingOperation() {
        build().expectValidationErrors(
                new ConfigException(
                        I18N.format("error.undefined-default-bundling-operation"),
                        I18N.format("error.undefined-default-bundling-operation.advice", "--type")
                )
        ).create().validate();
    }

    /**
     * Test that it fails as expected when `--type` option is missing and the
     * default bundling operation of the bundling environment is unrecognizable.
     */
    @Test
    public void testUnknownDefaultBundlingOperation() {
        var descriptor = new BundlingOperationDescriptor(OperatingSystem.current(), "foo", "create");

        var err = assertThrowsExactly(AssertionError.class, build().withDefaultBundlingOperation(true)
                .bundlingOperation(descriptor)
                .create()::validate);

        assertEquals(String.format("None of the standard bundling operations match bundling operation descriptor [%s]", descriptor), err.getMessage());
    }

    /**
     * Test that it fails as expected when the value of `--type` option is a valid
     * bundle type but unsupported by the bundling environment.
     */
    @Test
    public void testUnsupportedBundlingOperation() {
        var err = new JPackageException(I18N.format("ERR_InvalidInstallerType", "msi"));
        build().createAppImageByDefault().expectBundlingEnvironmentConfigurationErrors(err)
                .expectValidationErrors(err)
                .create("-t", "msi").validate();
    }

    /**
     * Test that it fails as expected when the value of `--type` option is unrecognizable.
     */
    @Test
    public void testUnknownBundlingOperation() {
        var err = new JPackageException(I18N.format("ERR_InvalidInstallerType", "foo"), new IllegalArgumentException());
        build().createAppImageByDefault().expectBundlingEnvironmentConfigurationErrors(err)
                .expectValidationErrors(err)
                .create("-t", "foo").validate();
    }

    /**
     * Test that the error occurred at the bundling phase is propagated as expected.
     */
    @Test
    public void testBundlingOperationError(@TempDir Path workDir) {
        var validator = build().createAppImageByDefault().createBundleCallback(_ -> {
            throw new RuntimeException("No bundling for you");
        }).withMockupMainJar(workDir).create();

        var validatedOptions = validator.validate().orElseThrow();

        assertExceptionListEquals(List.of(new RuntimeException("No bundling for you")), validator.runBundling(validatedOptions));
    }

    /**
     * Test that internal errors that occur when configuring bundlers in the
     * bundling environment are reported as expected.
     */
    @Test
    public void testBundlingOperationConfigurationErrors(@TempDir Path workDir) {
        build().createAppImageByDefault()
                .expectBundlingEnvironmentConfigurationErrors(
                        new UnsupportedOperationException("Ops"),
                        new Exception("Yikes"),
                        new NoSuchElementException("Goofy")
                )
                .expectValidationErrors(
                        new UnsupportedOperationException("Ops"),
                        new Exception("Yikes"),
                        new NoSuchElementException("Goofy")
                )
                .withMockupMainJar(workDir).create().validate();
    }

    /**
     * Test that the options analyzer can detect and report multiple errors in
     * untyped command line values.
     * <p>
     * Options analyzer examines command line options without accessing their values
     * as converters have not been run on them yet. At this phase the analyzer can
     * detect the type of a bundling operation, test if specific option is on the
     * command line or not and analyze if specific combinations of options are
     * valid.
     */
    @Test
    public void testMultipleCommandLineStructureAnalyzerErrors() {
        build().createAppImageByDefault().expectValidationErrors(
                new JPackageException(I18N.format("ERR_MutuallyExclusiveOptions", "-m", "--main-jar")),
                new JPackageException(I18N.format("ERR_MissingArgument2", "--runtime-image", "--module-path")),
                new JPackageException(I18N.format("error.no-input-parameter"))
        ).validationErrorsOrdered(false).create("-m", "com.foo", "--main-jar", "main.jar").validate();
    }

    /**
     * Test that options analyzer can detect and report multiple errors in typed
     * command line values.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testTypedOptionValuesAnalyzerErrors(boolean expectError, @TempDir Path workDir) {
        var builder = build().platform(OperatingSystem.MACOS).withMockupMainJar(workDir).createAppImageByDefault();

        if (expectError) {
            builder.expectValidationErrors(
                    new JPackageException(I18N.format("ERR_MissingJLinkOptMacAppStore", "--strip-native-commands"))
            )
            .create("--mac-app-store", "--jlink-options=").validate();
        } else {
            builder.create("--mac-app-store", "--jlink-options", "--strip-debug --strip-native-commands").validate();
        }
    }

    /**
     * Test that multiple converter/validator errors are in the same order as
     * erroneous options on the command line.
     */
    @Test
    public void testMultipleOptionValueConverterErrors(@TempDir Path workDir) {
        build().createAppImageByDefault().expectValidationErrors(
                // --i
                new JPackageException(I18N.format("error.parameter-not-directory", workDir.resolve("non-existent"), "-i")),
                // --name
                new JPackageException(I18N.format("ERR_InvalidAppName", "He/llo"))
        )
        .create("--main-jar=foo.jar", "-i", workDir.resolve("non-existent"), "--name=He/llo").validate();

        build().createAppImageByDefault().expectValidationErrors(
                // --name
                new JPackageException(I18N.format("ERR_InvalidAppName", "He/llo")),
                // --i
                new JPackageException(I18N.format("error.parameter-not-directory", workDir.resolve("non-existent"), "-i"))
        )
        .create("--main-jar=foo.jar", "--name=He/llo", "-i", workDir.resolve("non-existent")).validate();
    }

    @Test
    public void testMultipleErrors(@TempDir Path workDir) throws IOException {

        final var invalidPropertyFile = workDir.resolve("invalid.properties");

        try (var writer = Files.newBufferedWriter(invalidPropertyFile)) {
            var props = new Properties();
            props.setProperty("icon", workDir.toString());
            props.store(writer, null);
        }

        build().createAppImageByDefault()
        .expectBundlingEnvironmentConfigurationErrors(
                new UnsupportedOperationException("Ops"),
                new Exception("Yikes"),
                new NoSuchElementException("Goofy")
        )
        .expectValidationErrors(
                new UnsupportedOperationException("Ops"),
                new Exception("Yikes"),
                new NoSuchElementException("Goofy"),
                new JPackageException(I18N.format("error.properties-parameter-not-file", workDir, "icon", invalidPropertyFile)),
                new JPackageException(I18N.format("error.parameter-not-directory", workDir.resolve("non-existent"), "-i")),
                new JPackageException(I18N.format("error.launcher-duplicate-name", "a"))
        )
        .withMockupMainJar(workDir).create("--add-launcher=a=" + invalidPropertyFile, "--name=a", "-i", workDir.resolve("non-existent")).validate();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    public void testMultipleErrors2(int testType, @TempDir Path workDir) throws IOException {

        List<String> badOptions;
        switch (testType) {
            case 0 -> {
                badOptions = List.of("--linux-shortcut", "--win-console");
            }
            case 1 -> {
                badOptions = List.of("--win-console", "--linux-shortcut");
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }

        var builder = build().os(OperatingSystem.WINDOWS)
                .bundlingOperation(StandardBundlingOperation.CREATE_WIN_MSI.descriptor())
                .expectValidationErrors(new JPackageException(I18N.format("ERR_InvalidInstallerType", "dmg")));

        for (var badOption : badOptions) {
            builder.expectValidationErrors(new JPackageException(I18N.format("ERR_UnsupportedOption", badOption)));
        }

        List<Object> args = new ArrayList<>(List.of("-t", "dmg", "-i", workDir.resolve("non-existent")));
        args.addAll(badOptions);
        args.addAll(List.of("--mac-package-name", "foo"));
        builder.create(args).validate();
    }

    @Test
    public void testMultipleErrors3(@TempDir Path workDir) throws IOException {

        build().os(OperatingSystem.WINDOWS)
                .bundlingOperation(StandardBundlingOperation.CREATE_WIN_MSI.descriptor())
                .withDefaultBundlingOperation(true)
                .expectValidationErrors(
                        new JPackageException(I18N.format("ERR_UnsupportedOption", "--linux-shortcut")),
                        new JPackageException(I18N.format("ERR_UnsupportedOption", "--mac-package-name")),
                        new JPackageException(I18N.format("ERR_UnsupportedOption", "--linux-menu-group"))
                )
                .create("-i", workDir.resolve("non-existent"), "--linux-shortcut", "--mac-package-name", "foo", "--linux-menu-group", "grp").validate();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    public void testMultipleErrors4(int testType, @TempDir Path workDir) throws IOException {

        List<String> args;
        List<String> expectedErrors = null;

        switch (testType) {
            case 0 -> {
                args = List.of("--linux-shortcut", "--mac-package-name", "foo", "-p", "m1", "--linux-menu-group", "grp", "-p", "m2", "--app-image", "foo");
                expectedErrors = List.of(
                        I18N.format("ERR_UnsupportedOption", "--linux-shortcut"),
                        I18N.format("ERR_UnsupportedOption", "--mac-package-name"),
                        I18N.format("ERR_InvalidTypeOption", "-p", "msi"),
                        I18N.format("ERR_UnsupportedOption", "--linux-menu-group")
                );
            }
            case 1 -> {
                args = List.of("--linux-shortcut", "--mac-package-name", "foo", "--module-path", "m1", "--linux-menu-group", "grp", "-p", "m2", "--app-image", "foo");
                expectedErrors = List.of(
                        I18N.format("ERR_UnsupportedOption", "--linux-shortcut"),
                        I18N.format("ERR_UnsupportedOption", "--mac-package-name"),
                        I18N.format("ERR_InvalidTypeOption", "--module-path", "msi"),
                        I18N.format("ERR_UnsupportedOption", "--linux-menu-group")
                );
            }
            case 2 -> {
                args = List.of("--linux-shortcut", "--mac-package-name", "foo", "-p", "m1", "--linux-menu-group", "grp", "--module-path", "m2", "--app-image", "foo");
                expectedErrors = List.of(
                        I18N.format("ERR_UnsupportedOption", "--linux-shortcut"),
                        I18N.format("ERR_UnsupportedOption", "--mac-package-name"),
                        I18N.format("ERR_InvalidTypeOption", "--module-path", "msi"),
                        I18N.format("ERR_UnsupportedOption", "--linux-menu-group")
                );
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }

        build().os(OperatingSystem.WINDOWS)
                .withDefaultBundlingOperation(true)
                .bundlingOperation(StandardBundlingOperation.CREATE_WIN_MSI.descriptor())
                .expectValidationErrors(expectedErrors.stream().map(JPackageException::new).toList())
                .create(args.toArray()).validate();
    }


    /**
     * Test that it will collect all errors when processing multiple property files
     * for additional launchers.
     */
    @Test
    @EnabledOnOs(value = OS.WINDOWS, disabledReason = "Can reliably lock a file using FileLock to cuase an IOException on Windows only")
    @SuppressWarnings("try")
    public void testMultipleAddLauncherErrors(@TempDir Path workDir) throws IOException {

        final var lockedPropertyFile = workDir.resolve("locked.properties");
        Files.write(lockedPropertyFile, List.of(""));

        final var invalidPropertyFile = workDir.resolve("invalid.properties");
        Files.write(invalidPropertyFile, List.of("icon=*.png"));

        final var nonExistentPropertyFile = workDir.resolve("non-existent.properties");

        try (var out = new FileOutputStream(lockedPropertyFile.toFile()); var lock = out.getChannel().lock()) {

            final IOException lockedException = assertThrowsExactly(IOException.class, () -> {
                Files.readAllBytes(lockedPropertyFile);
            });

            build().createAppImageByDefault().expectValidationErrors(
                    // --name=Hello*
                    new JPackageException(I18N.format("ERR_InvalidAppName", "Hello*")),

                    // --i "non-existent"
                    new JPackageException(I18N.format("error.parameter-not-directory", workDir.resolve("non-existent"), "-i")),

                    // --main-jar=?.jar
                    new JPackageException(I18N.format("error.parameter-not-path", "?.jar", "--main-jar"), new IllegalArgumentException()),

                    // icon=*.png in "invalid.properties"
                    new JPackageException(I18N.format("error.properties-parameter-not-path", "*.png", "icon", invalidPropertyFile), new IllegalArgumentException()),

                    // "locked.properties"
                    lockedException,

                    // "non-existent.properties"
                    new AddLauncherInvalidPropertyFileException(I18N.format("error.parameter-add-launcher-not-file", nonExistentPropertyFile, "b")),

                    new JPackageException(I18N.format("error.launcher-duplicate-name", "b"))
            )
            .create("--name=Hello*", "-i", workDir.resolve("non-existent"), "--main-jar=?.jar",
                    "--add-launcher", "a=" + invalidPropertyFile,
                    "--add-launcher", "b=" + lockedPropertyFile,
                    "--add-launcher", "b=" + nonExistentPropertyFile).validate();
        }
    }

    @Test
    public void testDuplicateAddLauncherErrors(@TempDir Path workDir) throws IOException {

        final var propertyFile = workDir.resolve("foo.properties");
        Files.write(propertyFile, List.of(""));

        build().createAppImageByDefault().withMockupMainJar(workDir).expectValidationErrors(
                new JPackageException(I18N.format("error.launcher-duplicate-name", "a"))
        )
        .create("--name=a", "--add-launcher=a=" + propertyFile).validate();

        build().createAppImageByDefault().withMockupMainJar(workDir).expectValidationErrors(
                new JPackageException(I18N.format("error.launcher-duplicate-name", "a"))
        )
        .create("--name=a", "--add-launcher=a=" + propertyFile, "--add-launcher=a=" + propertyFile, "--add-launcher=a=" + propertyFile).validate();

        build().createAppImageByDefault().withMockupMainJar(workDir).expectValidationErrors(
                new JPackageException(I18N.format("error.launcher-duplicate-name", "a")),
                new JPackageException(I18N.format("error.launcher-duplicate-name", "b"))
        )
        .create("-name=c",
                "--add-launcher=a=" + propertyFile,
                "--add-launcher=b=" + propertyFile,
                "--add-launcher=a=" + propertyFile,
                "--add-launcher=b=" + propertyFile).validate();

        build().createAppImageByDefault().withMockupMainJar(workDir).expectValidationErrors(
                new JPackageException(I18N.format("error.launcher-duplicate-name", "a")),
                new JPackageException(I18N.format("error.launcher-duplicate-name", "b"))
        )
        .create("-name=c",
                "--add-launcher=b=" + propertyFile,
                "--add-launcher=a=" + propertyFile,
                "--add-launcher=b=" + propertyFile,
                "--add-launcher=a=" + propertyFile).validate();
    }

    enum BooleanProperty {

        TRUE("true", true),
        TRUE_UPPERCASE_FIRST("True", true),
        TRUE_UPPERCASE_ALL("TRUE", true),
        TRUE_UPPERCASE_LAST("truE", true),
        TRUE_UPPERCASE_MIDDLE("tRUe", true),
        FALSE("false", false),
        FALSE_UPPERCASE("FALSE", false),
        EMPTY("", false),
        RANDOM("foo", false),
        TRUELISH("truee", false),
        FALSEISH("fals", false),
        ;

        BooleanProperty(String propertyValue, boolean expectedValue) {
            this.propertyValue = Objects.requireNonNull(propertyValue);
            this.expectedValue = Objects.requireNonNull(expectedValue);
        }

        String propertyValue() {
            return propertyValue;
        }

        boolean expectedValue() {
            return expectedValue;
        }

        private final String propertyValue;
        private final boolean expectedValue;
    }

    private static Result<Options> processPropertyFile(Path propFile, OptionValue<?>... options) {
        return OptionsProcessor.processPropertyFile(propFile,
                Stream.of(options).map(OptionValue::getOption).toList(),
                Optional.of(spec -> {
                    return new StandardOptionContext(OperatingSystem.current()).forFile(propFile).mapOptionSpec(spec);
                }));
    }

    private static OptionsProcessorValidatorBuilder build() {
        return new OptionsProcessorValidatorBuilder();
    }


    private record OptionsProcessorValidator(
            OptionsProcessor optionsProcessor,
            CreateBundleCallbackRecorder recorder,
            Collection<Map<String, Object>> expectedValidationErrors) {

        OptionsProcessorValidator {
            Objects.requireNonNull(optionsProcessor);
            Objects.requireNonNull(recorder);
            Objects.requireNonNull(expectedValidationErrors);
        }

        Result<OptionsProcessor.ValidatedOptions> validate() {
            var curExecutedCounter = recorder.executedCounter();

            var result = optionsProcessor.validate();

            // Assert validation doesn't trigger bundling.
            assertEquals(curExecutedCounter, recorder.executedCounter());

            var errors = result.errors().stream().map(JUnitUtils::exceptionAsPropertyMap).map(propertyMap -> {
                // Don't dive into the cause chain, stop at the first one.
                Optional.ofNullable(propertyMap.get("getCause")).ifPresent(causePropertyMap -> {
                    ((Map<?, ?>)causePropertyMap).remove("getCause");
                    ((Map<?, ?>)causePropertyMap).remove("getMessage");
                });
                return propertyMap;
            }).toList();
            if (expectedValidationErrors instanceof Set<?>) {
                assertEquals(expectedValidationErrors, Set.copyOf(errors));
            } else {
                assertEquals(expectedValidationErrors, errors);
            }

            return result;
        }

        Collection<? extends Exception> runBundling(OptionsProcessor.ValidatedOptions validatedOptions) {
            var curExecutedCounter = recorder.executedCounter();

            var result = optionsProcessor.runBundling(validatedOptions);

            assertEquals(curExecutedCounter + 1, recorder.executedCounter());

            return result;
        }

        void execute() {
            var exceptions = runBundling(validate().orElseThrow());

            for (var ex : exceptions) {
                ex.printStackTrace();
            }

            assertEquals(List.of(), exceptions);
        }
    }


    private static final class CreateBundleCallbackRecorder implements Consumer<Options> {

        CreateBundleCallbackRecorder(Optional<Consumer<Options>> callback) {
            this.callback = Objects.requireNonNull(callback);
        }

        @Override
        public void accept(Options cmdline) {
            executedCounter++;
            callback.ifPresent(c -> c.accept(cmdline));
        }

        int executedCounter() {
            return executedCounter;
        }

        private final Optional<Consumer<Options>> callback;
        private int executedCounter;
    }


    private static final class OptionsProcessorValidatorBuilder {

        OptionsProcessorValidator create(Object... args) {
            return create(List.of(args));
        }

        OptionsProcessorValidator create(Iterable<Object> args) {

            var stringArgs = Stream.concat(
                    baseArgs.stream(),
                    StreamSupport.stream(args.spliterator(), false).map(Object::toString)
            ).toArray(String[]::new);

            var recorder = new CreateBundleCallbackRecorder(createBundleCallback());

            CliBundlingEnvironment bundlingEnv = bundlingEnvironmentBuilder.createBundleCallback(recorder).create();

            var optionsBuilder = Utils.buildParser(os, bundlingEnv).create().apply(stringArgs).orElseThrow();

            var op = new OptionsProcessor(optionsBuilder, bundlingEnv);

            Collection<Map<String, Object>> errors;
            if (expectedValidationErrorsOrdered) {
                errors = expectedValidationErrors.stream().map(JUnitUtils::exceptionAsPropertyMap).toList();
            } else {
                errors = expectedValidationErrors.stream().map(JUnitUtils::exceptionAsPropertyMap).collect(Collectors.toSet());
            }

            return new OptionsProcessorValidator(op, recorder, errors);
        }

        OptionsProcessorValidatorBuilder args(Iterable<Object> v) {
            StreamSupport.stream(v.spliterator(), false).map(Object::toString).forEach(baseArgs::add);
            return this;
        }

        OptionsProcessorValidatorBuilder args(Object... args) {
            return args(List.of(args));
        }

        OptionsProcessorValidatorBuilder os(OperatingSystem v) {
            os = Objects.requireNonNull(v);
            return this;
        }

        OptionsProcessorValidatorBuilder withMockupMainJar(Path inputDir) {

            try {
                Files.write(inputDir.resolve("mockup.jar"), new byte[] {});
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }

            return args("--input", inputDir, "--main-jar", "mockup.jar");
        }

        OptionsProcessorValidatorBuilder withDefaultBundlingOperation(boolean v) {
            withDefaultBundlingOperation = v;
            if (withDefaultBundlingOperation) {
                bundlingEnvironmentBuilder.defaultOperation(bundlingOperation);
            } else {
                bundlingEnvironmentBuilder.defaultOperation(null);
            }
            return this;
        }

        OptionsProcessorValidatorBuilder bundlingOperation(BundlingOperationDescriptor v) {
            bundlingOperation = Objects.requireNonNull(v);
            bundlingEnvironmentBuilder.knownOperation(bundlingOperation);
            withDefaultBundlingOperation(withDefaultBundlingOperation);
            return this;
        }

        OptionsProcessorValidatorBuilder createAppImageBundlingOperation() {
            return bundlingOperation(MockupCliBundlingEnvironment.createAppImageBundlingOperation(os).descriptor());
        }

        OptionsProcessorValidatorBuilder createAppImageByDefault() {
            return createAppImageBundlingOperation().withDefaultBundlingOperation(true);
        }

        OptionsProcessorValidatorBuilder platform(OperatingSystem v) {
            os = Objects.requireNonNull(v);
            return this;
        }

        OptionsProcessorValidatorBuilder createBundleCallback(Consumer<Options> v) {
            createBundleCallback = v;
            return this;
        }

        OptionsProcessorValidatorBuilder expectValidationErrors(Iterable<? extends Exception> v) {
            v.forEach(this::expectValidationError);
            return this;
        }

        OptionsProcessorValidatorBuilder expectValidationError(Exception v) {

            if (expectedValidationErrors.stream().anyMatch(err -> {
                return err.getMessage().equals(v.getMessage());
            })) {
                throw new IllegalArgumentException(String.format("Error message [%s] already expected", v.getMessage()));
            }
            expectedValidationErrors.add(v);
            return this;
        }

        OptionsProcessorValidatorBuilder expectValidationErrors(Exception... errors) {
            return expectValidationErrors(List.of(errors));
        }

        OptionsProcessorValidatorBuilder expectBundlingEnvironmentConfigurationErrors(List<Exception> v) {
            bundlingEnvironmentBuilder.configurationErrors(bundlingOperation, v);
            return this;
        }

        OptionsProcessorValidatorBuilder expectBundlingEnvironmentConfigurationErrors(Exception... errors) {
            return expectBundlingEnvironmentConfigurationErrors(List.of(errors));
        }

        OptionsProcessorValidatorBuilder validationErrorsOrdered(boolean v) {
            expectedValidationErrorsOrdered = v;
            return this;
        }

        private Optional<Consumer<Options>> createBundleCallback() {
            return Optional.ofNullable(createBundleCallback);
        }

        private final List<String> baseArgs = new ArrayList<>();
        private BundlingOperationDescriptor bundlingOperation;
        private OperatingSystem os = OperatingSystem.current();
        private boolean withDefaultBundlingOperation;
        private final List<Exception> expectedValidationErrors = new ArrayList<>();
        private Consumer<Options> createBundleCallback;
        private boolean expectedValidationErrorsOrdered = true;
        private final MockupCliBundlingEnvironment.Builder bundlingEnvironmentBuilder = MockupCliBundlingEnvironment.build();
    }
}
