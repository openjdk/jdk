/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.ConvertedOptionsBuilder;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.OptionsBuilder;
import jdk.jpackage.internal.cli.StandardOption.LauncherProperty;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.LauncherShortcutStartupDirectory;
import jdk.jpackage.internal.util.StringBundle;
import jdk.jpackage.test.Comm;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.TKit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class StandardOptionTest extends JUnitAdapter.TestSrcInitializer {

    @Test
    public void testNamesUnique() {

        final var options = StandardOption.options();

        // Test option names are unique. Let Collectors.toMap() do it.
        options.stream()
                .map(Option::spec)
                .map(OptionSpec::names)
                .flatMap(Collection::stream)
                .map(OptionName::name)
                .collect(toMap(x -> x, x -> x));
    }

    @Test
    public void testDescription() {

        final var options = StandardOption.options();

        var i18n = StringBundle.fromResourceBundle(ResourceBundle.getBundle("jdk.jpackage.internal.resources.HelpResources"));

        options.stream().map(Option::spec).map(OptionSpec::description).forEach(i18n::getString);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "icon"})
    public void test_ICON(String name, @TempDir Path workDir) throws IOException {

        if (!name.isEmpty()) {
            var file = workDir.resolve(name);

            Files.write(file, new byte[0]);

            name = file.toString();
        }

        var spec = StandardOption.ICON.getSpec();

        var result = spec.converter().orElseThrow().convert(spec.name(), StringToken.of(name));

        assertEquals(Path.of(name), result.orElseThrow());
    }

    @Test
    public void test_ICON_validator_fail(@TempDir Path workDir) {

        var spec = StandardOption.ICON.getSpec();

        var result = spec.converter().orElseThrow().convert(spec.name(), StringToken.of(workDir.toString()));

        var ex = assertThrows(JPackageException.class, result::orElseThrow);

        assertEquals(I18N.format("error.parameter-not-file", workDir, "--icon"), ex.getMessage());
    }

    @Test
    public void test_ICON_validator_fail_in_property_file(@TempDir Path workDir) {

        var propertyFile = Path.of("foo.properties");

        var spec = new StandardOptionContext().forFile(propertyFile).mapOptionSpec(StandardOption.ICON.getSpec());

        var result = spec.converter().orElseThrow().convert(spec.name(), StringToken.of(workDir.toString()));

        var ex = assertThrows(JPackageException.class, result::orElseThrow);

        assertEquals(I18N.format("error.properties-parameter-not-file", workDir, "icon", propertyFile), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "non-existent"})
    public void test_TEMP_ROOT_valid(String dir, @TempDir Path workDir) {

        var spec = StandardOption.TEMP_ROOT.getSpec();

        var tempRoot = workDir.resolve(dir);

        var value = spec.converter().orElseThrow().convert(spec.name(), StringToken.of(tempRoot.toString())).orElseThrow();

        assertEquals(tempRoot, value);
    }

    @Test
    public void test_TEMP_ROOT_invalid(@TempDir Path workDir) throws IOException {

        var spec = StandardOption.TEMP_ROOT.getSpec();

        var tempRoot = workDir.resolve("file");

        Files.writeString(tempRoot, "foo");

        var ex = assertThrowsExactly(JPackageException.class,
                spec.converter().orElseThrow().convert(spec.name(), StringToken.of(tempRoot.toString()))::orElseThrow);
        assertEquals(I18N.format("error.parameter-not-empty-directory", tempRoot, "--temp"), ex.getMessage());
        assertEquals(NotDirectoryException.class, ex.getCause().getClass());

        tempRoot = workDir;

        ex = assertThrowsExactly(JPackageException.class,
                spec.converter().orElseThrow().convert(spec.name(), StringToken.of(tempRoot.toString()))::orElseThrow);
        assertEquals(I18N.format("error.parameter-not-empty-directory", tempRoot, "--temp"), ex.getMessage());
        assertEquals(DirectoryNotEmptyException.class, ex.getCause().getClass());
    }

    @Test
    public void test_TYPE_valid() {

        var spec = StandardOption.TYPE.getSpec();

        Stream.of(StandardBundlingOperation.values()).forEach(bundlingOperation -> {
            var pkgTypeStr = bundlingOperation.packageTypeValue();
            var pkgType = spec.converter().orElseThrow().convert(spec.name(), StringToken.of(pkgTypeStr)).orElseThrow();
            assertSame(bundlingOperation.packageType(), pkgType);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "msii", "RPM", "App-image"})
    public void test_TYPE_invalid(String name) {

        var spec = StandardOption.TYPE.getSpec();

        var result = spec.converter().orElseThrow().convert(spec.name(), StringToken.of(name));

        var ex = assertThrows(JPackageException.class, result::orElseThrow);

        assertEquals(I18N.format("ERR_InvalidInstallerType", name), ex.getMessage());
    }

    @Test
    public void test_booleanOptionMutator() {

        var option = OptionSpecBuilder.create(Boolean.class).name("foo").mutate(StandardOption.booleanOptionMutator()).create();

        var empty = Options.of(Map.of());

        // Expect the default value.
        assertTrue(option.findIn(empty).isPresent());

        // Expect the option is not found.
        assertFalse(option.containsIn(empty));
    }

    @ParameterizedTest
    @MethodSource
    public void testLauncherShortcutOptions(LauncherShortcutTestSpec testSpec) {
        testSpec.test();
    }

    @Test
    public void test_launcherOptions() {
        var options = StandardOption.launcherOptions().stream().map(Option::id).toList();

        // Test some that should be in the set
        assertTrue(options.contains(StandardOption.ARGUMENTS.id()));
        assertTrue(options.contains(StandardOption.MODULE.id()));
        assertTrue(options.contains(StandardOption.APPCLASS.id()));
        assertTrue(options.contains(StandardOption.WIN_CONSOLE_HINT.id()));

        // Test some that should NOT be in the set
        assertFalse(options.contains(StandardOption.NAME.id()));
        assertFalse(options.contains(StandardOption.INSTALL_DIR.id()));
    }

    @Test
    public void test_sharedOption() {

        var pred = StandardOption.sharedOption();

        var options = StandardOption.options().stream().filter(option -> {
            return pred.test(option.spec());
        }).map(Option::id).toList();

        // Test some that should be in the set
        assertTrue(options.contains(StandardOption.NAME.id()));
        assertTrue(options.contains(StandardOption.INSTALL_DIR.id()));

        // Test some that should NOT be in the set
        assertFalse(options.contains(StandardOption.WIN_CONSOLE_HINT.id()));
        assertFalse(options.contains(StandardOption.LINUX_MENU_GROUP.id()));
        assertFalse(options.contains(StandardOption.MAC_APP_CATEGORY.id()));
    }

    @ParameterizedTest
    @EnumSource(names = {"WINDOWS", "LINUX", "MACOS"})
    public void test_platformOption(OperatingSystem os) {

        var pred = StandardOption.platformOption(os);

        var options = StandardOption.options().stream().filter(option -> {
            return pred.test(option.spec());
        }).map(Option::id).toList();

        // Test some that should be in the set
        assertTrue(options.contains(StandardOption.NAME.id()));
        assertTrue(options.contains(StandardOption.INSTALL_DIR.id()));

        switch (os) {
            case WINDOWS -> {
                assertTrue(options.contains(StandardOption.WIN_CONSOLE_HINT.id()));
                assertFalse(options.contains(StandardOption.LINUX_MENU_GROUP.id()));
                assertFalse(options.contains(StandardOption.MAC_APP_CATEGORY.id()));
            }
            case LINUX -> {
                assertFalse(options.contains(StandardOption.WIN_CONSOLE_HINT.id()));
                assertTrue(options.contains(StandardOption.LINUX_MENU_GROUP.id()));
                assertFalse(options.contains(StandardOption.MAC_APP_CATEGORY.id()));
            }
            case MACOS -> {
                assertFalse(options.contains(StandardOption.WIN_CONSOLE_HINT.id()));
                assertFalse(options.contains(StandardOption.LINUX_MENU_GROUP.id()));
                assertTrue(options.contains(StandardOption.MAC_APP_CATEGORY.id()));
            }
            default -> {
                throw new AssertionError();
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    public void test_ARGUMENTS(String value, List<String> expectedTokens) {

        var spec = StandardOption.ARGUMENTS.getOption().spec();

        var result = spec.converter().orElseThrow().convert(spec.name(), StringToken.of(value));

        assertEquals(expectedTokens, List.of(result.map(String[].class::cast).orElseThrow()));
    }

    @ParameterizedTest
    @MethodSource
    public void testAddLauncherOption(AddLauncherTestSpec testSpec, @TempDir Path workDir) throws IOException {
        testSpec.test(workDir);
    }

    @Disabled
    @Test
    public void printMarkdownOptionTable() {
        OptionSpecFormatter.groupByOption(System.out::println,
                StandardOption.options().stream().map(Option::spec).toList());
    }

    @Disabled
    @Test
    public void updateMarkdownOptionTable() throws IOException {
        try (var sink = Files.newBufferedWriter(GOLDEN_JPACKAGE_OPTIONS_MD); var pw = new PrintWriter(sink)) {
            OptionSpecFormatter.groupByOption(pw::println,
                    StandardOption.options().stream().map(Option::spec).toList());
        }
    }

    @Test
    public void verifyOptions() throws IOException {

        List<String> optionTable = new ArrayList<>();

        OptionSpecFormatter.groupByOption(optionTable::add,
                StandardOption.options().stream().map(Option::spec).toList());

        var expectedOptionTable = Files.readAllLines(GOLDEN_JPACKAGE_OPTIONS_MD);

        assertEquals(expectedOptionTable, optionTable);
    }

    private static Collection<Arguments> test_ARGUMENTS() {
        return List.of(
                Arguments.of("abc", List.of("abc")),
                Arguments.of("a b c", List.of("a", "b", "c")),
                Arguments.of("a=10 -Dorg.acme.name='John Smith' c=\\\"foo\\\"", List.of("a=10", "-Dorg.acme.name=John Smith", "c=\"foo\"")),
                Arguments.of("  foo \"a b c\" v=' John Smith ' 'H e ll o' ", List.of("foo", "a b c", "v= John Smith ", "H e ll o")),
                Arguments.of("\"\"", List.of("")),
                Arguments.of(" ", List.of()),
                Arguments.of("   ", List.of()),
                Arguments.of(" foo  ", List.of("foo")),
                Arguments.of("", List.of()),
                Arguments.of("'fo\"o'\\ buzz \"b a r\"", List.of("fo\"o\\ buzz", "b a r")),
                Arguments.of("a\\ 'b\"c'\\ d", List.of("a\\ b\"c\\ d")),
                Arguments.of("\"a 'bc' d\"", List.of("a 'bc' d")),
                Arguments.of("\'a 'bc' d\'", List.of("a bc d")),
                Arguments.of("\"a \\'bc\\' d\"", List.of("a 'bc' d")),
                Arguments.of("\'a \\'bc\\' d\'", List.of("a 'bc' d")),
                Arguments.of("'a b c' 'd e f'", List.of("a b c", "d e f")),
                Arguments.of("'a b c' \"'d e f'  h", List.of("a b c", "'d e f'  h")),
                Arguments.of("'a b c' \"'d e f' \t  ", List.of("a b c", "'d e f'")),
                Arguments.of(" a='' '' \t '\\'\\'' \"\" \"\\\"\\\"\" ", List.of("a=", "", "\'\'", "", "\"\"")),
                Arguments.of("' \'foo '", List.of(" foo", "")),
                Arguments.of("' \'foo ' bar", List.of(" foo", " bar")),
                Arguments.of("' \'foo\\ '", List.of(" foo\\ ")),
                Arguments.of("'fo\"o buzz \"b a r\"", List.of("fo\"o buzz \"b a r\"")),
                Arguments.of("'", List.of("")),
                Arguments.of("' f g  ", List.of(" f g")),
                Arguments.of("' f g", List.of(" f g")),
                Arguments.of("'\\'", List.of("'")),
                Arguments.of("'\\'  ", List.of("'")),
                Arguments.of("'\\' a ", List.of("' a")),
                Arguments.of("\"" + "\\\"".repeat(10000) + "A", List.of("\"".repeat(10000) + "A"))
        );
    }


    record AddLauncherTestSpec(Optional<AdditionalLauncher> expected, List<String> expectedErrors, Optional<String> optionValue) {
        AddLauncherTestSpec {
            if (expected.isEmpty() == expectedErrors.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        void test(Path workDir) throws IOException {
            final List<String> args = new ArrayList<>();
            args.add(StandardOption.ADD_LAUNCHER_INTERNAL.getSpec().name().formatForCommandLine());
            optionValue.ifPresent(args::add);
            if (optionValue.isEmpty()) {
                var path = workDir.resolve(expected.orElseThrow().propertyFile());
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                expected.map(v -> v.name() + "=" + path).ifPresent(args::add);
            }

            final var result = new JOptSimpleOptionsBuilder().options(StandardOption.ADD_LAUNCHER_INTERNAL).create()
                    .apply(args.toArray(String[]::new))
                    .flatMap(OptionsBuilder::convertedOptions).map(ConvertedOptionsBuilder::create);

            if (expectedErrors.isEmpty()) {
                final var cmdline = result.orElseThrow();
                assertEquals(expected.map(List::of).orElseGet(List::of), Stream.of(StandardOption.ADD_LAUNCHER_INTERNAL.getFrom(cmdline)).map(v -> {
                    if (optionValue.isEmpty()) {
                        return new AdditionalLauncher(v.name(), workDir.relativize(v.propertyFile()));
                    } else {
                        return v;
                    }
                }).toList());
            } else {
                assertEquals(expectedErrors.stream().toList(), result.errors().stream().map(Exception::getMessage).toList());
            }
        }

        static final class Builder {

            AddLauncherTestSpec create() {
                return new AddLauncherTestSpec(Optional.ofNullable(expected), expectedErrors, Optional.ofNullable(optionValue));
            }

            Builder expect(String name, String path) {
                expected = new AdditionalLauncher(name, Path.of(path));
                return this;
            }

            Builder expectErrors(String... v) {
                expectedErrors.addAll(List.of(v));
                return this;
            }

            Builder optionValue(String v) {
                optionValue = v;
                return this;
            }

            private AdditionalLauncher expected;
            private List<String> expectedErrors = new ArrayList<>();
            private String optionValue;
        }
    }


    private static Collection<AddLauncherTestSpec> testAddLauncherOption() {
        return Stream.of(
                buildAddLauncherTest().expect("foo", "some.properties"),
                buildAddLauncherTest().expect("foo", "a/b/some.properties").expect("bar", "="),
                buildAddLauncherTest().expect("a", "a.properties").expect("a", "b.properties"),
                buildAddLauncherTest().optionValue("some").expectErrors(I18N.format("error.parameter-add-launcher-malformed", "some", "--add-launcher")),
                buildAddLauncherTest().optionValue("").expectErrors(I18N.format("error.parameter-add-launcher-malformed", "", "--add-launcher")),
                buildAddLauncherTest().optionValue("=").expectErrors(I18N.format("ERR_InvalidSLName", "")),
                buildAddLauncherTest().optionValue("a=").expectErrors(I18N.format("error.parameter-add-launcher-not-file", "", "a")),
                buildAddLauncherTest().optionValue("=a").expectErrors(I18N.format("ERR_InvalidSLName", "")),
                // Not a path
                buildAddLauncherTest().optionValue("foo=*").expectErrors(I18N.format("error.parameter-add-launcher-not-file", "*", "foo")),
                // The path is a directory
                buildAddLauncherTest().optionValue("foo=.").expectErrors(I18N.format("error.parameter-add-launcher-not-file", ".", "foo")),
                // The path doesn't exist
                buildAddLauncherTest().optionValue("Foo=foo/bar/buz/111.z").expectErrors(I18N.format("error.parameter-add-launcher-not-file", "foo/bar/buz/111.z", "Foo"))
        ).map(AddLauncherTestSpec.Builder::create).toList();
    }

    private static AddLauncherTestSpec.Builder buildAddLauncherTest() {
        return new AddLauncherTestSpec.Builder();
    }


    record LauncherShortcutTestSpec(OptionValue<LauncherShortcut> option, Optional<LauncherShortcut> expected, List<String> expectedErrors, boolean propertyFile, Optional<String> optionValue) {
        LauncherShortcutTestSpec {
            Objects.requireNonNull(option);
            if (expected.isEmpty() == expectedErrors.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        void test() {
            final List<String> args = new ArrayList<>();
            args.add(option.getSpec().name().formatForCommandLine());
            optionValue.ifPresent(args::add);

            final var builder = new JOptSimpleOptionsBuilder().options(option);
            if (propertyFile) {
                builder.optionSpecMapper(spec -> {
                    return new StandardOptionContext().forFile(DUMMY_PROPERTY_FILE).mapOptionSpec(spec);
                });
            }

            final var result = builder.create()
                    .apply(args.toArray(String[]::new))
                    .flatMap(OptionsBuilder::convertedOptions).map(ConvertedOptionsBuilder::create);

            if (expectedErrors.isEmpty()) {
                assertEquals(expected.orElseThrow(), option.getFrom(result.orElseThrow()));
            } else {
                assertEquals(expectedErrors.stream().toList(), result.errors().stream().map(Exception::getMessage).toList());
            }
        }

        static final class Builder {

            LauncherShortcutTestSpec create() {
                var theExpected = Optional.ofNullable(expected);
                return new LauncherShortcutTestSpec(
                        option,
                        theExpected,
                        theExpected.map(v -> {
                            return List.<String>of();
                        }).orElseGet(() -> {
                            if (propertyFile) {
                                return List.of(I18N.format("error.properties-parameter-not-launcher-shortcut-dir",
                                        optionValue, option.getSpec().name().name(), DUMMY_PROPERTY_FILE));
                            } else {
                                return List.of(I18N.format("error.parameter-not-launcher-shortcut-dir",
                                        optionValue, option.getSpec().name().formatForCommandLine()));
                            }
                        }), propertyFile, Optional.ofNullable(optionValue));
            }

            Builder option(OptionValue<LauncherShortcut> v) {
                option = v;
                return this;
            }

            Builder expect(LauncherShortcut v) {
                expected = v;
                if (expected != null) {
                    optionValue(v.startupDirectory().map(LauncherShortcutStartupDirectory::asStringValue).orElse(null));
                } else {
                    optionValue(null);
                }
                return this;
            }

            Builder expect(LauncherShortcutStartupDirectory v) {
                return expect(new LauncherShortcut(v));
            }

            Builder propertyFile(boolean v) {
                propertyFile = v;
                return this;
            }

            Builder optionValue(String v) {
                optionValue = v;
                return this;
            }


            OptionValue<LauncherShortcut> option;
            private boolean propertyFile;
            private LauncherShortcut expected;
            private String optionValue;
        }

        private static final Path DUMMY_PROPERTY_FILE = Path.of("foo.properties");
    }


    private static Collection<LauncherShortcutTestSpec> testLauncherShortcutOptions() {
        return Stream.of(
                buildLauncherShortcutTest().expect(LauncherShortcutStartupDirectory.DEFAULT).optionValue(null),
                buildLauncherShortcutTest().expect(LauncherShortcutStartupDirectory.APP_DIR),
                buildLauncherShortcutTest().expect(LauncherShortcutStartupDirectory.DEFAULT).propertyFile(true),
                buildLauncherShortcutTest().expect(LauncherShortcutStartupDirectory.APP_DIR).propertyFile(true),
                buildLauncherShortcutTest().expect(new LauncherShortcut()).optionValue("false").propertyFile(true),
                buildLauncherShortcutTest().optionValue(LauncherShortcutStartupDirectory.DEFAULT.asStringValue()),
                buildLauncherShortcutTest().optionValue("false"),
                buildLauncherShortcutTest().optionValue(""),
                buildLauncherShortcutTest().expect(new LauncherShortcut()).optionValue("").propertyFile(true),
                buildLauncherShortcutTest().optionValue("bar"),
                buildLauncherShortcutTest().expect(new LauncherShortcut()).optionValue("bar").propertyFile(true)
        ).map(builder -> {
            return Stream.of(
                    StandardOption.LINUX_SHORTCUT_HINT,
                    StandardOption.WIN_MENU_HINT,
                    StandardOption.WIN_SHORTCUT_HINT
            ).map(builder::option).map(LauncherShortcutTestSpec.Builder::create);
        }).flatMap(x -> x).toList();
    }

    private static LauncherShortcutTestSpec.Builder buildLauncherShortcutTest() {
        return new LauncherShortcutTestSpec.Builder();
    }

    private static final class OptionSpecFormatter {
        static void groupByOption(Consumer<String>sink, Collection<? extends OptionSpec<?>> specs) {
            sink.accept("| Option | Scope | With runtime installer | With predefined app image | Recognized in add launcher .property file | Merge");
            sink.accept("| --- | --- | :---: | :---: | :---: | :---: |");
            for (final var spec : specs.stream().sorted(Comparator.comparing(v -> { return v.name().name(); })).toList()) {
                final var mods = filterByType(spec.scope(), BundlingOperationModifier.class);
                sink.accept(String.format("| %s | %s | %s | %s | %s | %s |",
                        formatOptionNames(spec),
                        format(filterByType(spec.scope(), BundlingOperationOptionScope.class)),
                        (mods.contains(BundlingOperationModifier.BUNDLE_RUNTIME) ? "x" : ""),
                        (mods.contains(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE) ? "x" : ""),
                        (!filterByType(spec.scope(), LauncherProperty.class).isEmpty() ? "x" : ""),
                        spec.mergePolicy()
                ));
            }
        }

        private static String formatOptionNames(OptionSpec<?> spec) {
            return spec.names().stream().map(OptionName::formatForCommandLine).collect(joining(", "));
        }

        private static String format(OptionScope op) {
            return Optional.ofNullable(KNOWN_BUNDLING_OPERATIONS.get(op)).orElseGet(op::toString);
        }

        private static String format(Set<OptionScope> ops) {
            final List<String> knownScopeLabels = new ArrayList<>();

            for (;;) {
                final var theOps = ops;
                final var bestMatchedKnownScope = KNOWN_SCOPES.keySet().stream().map(knownGroup -> {
                    return Comm.compare(theOps, knownGroup);
                }).filter(comm -> {
                    return comm.unique2().isEmpty();
                }).max(Comparator.comparing(comm -> {
                    return comm.common().size();
                }));

                if (bestMatchedKnownScope.isEmpty()) {
                    break;
                } else {
                    knownScopeLabels.add(KNOWN_SCOPES.get(bestMatchedKnownScope.orElseThrow().common()));
                    ops = bestMatchedKnownScope.orElseThrow().unique1();
                }
            }

            return Stream.concat(
                    knownScopeLabels.stream(),
                    ops.stream().map(OptionSpecFormatter::format)
            ).sorted().collect(joining(", "));
        }

        private static Set<OptionScope> filterByType(Collection<OptionScope> ops, Class<? extends OptionScope> filterType) {
            return ops.stream().filter(filterType::isInstance).collect(Collectors.toSet());
        }

        private static final Map<OptionScope, String> KNOWN_BUNDLING_OPERATIONS = Map.of(
                StandardBundlingOperation.CREATE_WIN_APP_IMAGE, "app-image-win",
                StandardBundlingOperation.CREATE_LINUX_APP_IMAGE, "app-image-linux",
                StandardBundlingOperation.CREATE_MAC_APP_IMAGE, "app-image-mac",
                StandardBundlingOperation.CREATE_WIN_EXE, "win-exe",
                StandardBundlingOperation.CREATE_WIN_MSI, "win-msi",
                StandardBundlingOperation.CREATE_LINUX_RPM, "linux-rpm",
                StandardBundlingOperation.CREATE_LINUX_DEB, "linux-deb",
                StandardBundlingOperation.CREATE_MAC_PKG, "mac-pkg",
                StandardBundlingOperation.CREATE_MAC_DMG, "mac-dmg",
                StandardBundlingOperation.SIGN_MAC_APP_IMAGE, "mac-sign"
        );

        private static final Map<Set<OptionScope>, String> KNOWN_SCOPES = Map.of(
                Set.copyOf(StandardBundlingOperation.CREATE_APP_IMAGE), "app-image",
                Set.copyOf(StandardBundlingOperation.WINDOWS), "win",
                Set.copyOf(StandardBundlingOperation.MACOS), "mac",
                Set.of(StandardBundlingOperation.CREATE_MAC_APP_IMAGE, StandardBundlingOperation.CREATE_MAC_DMG, StandardBundlingOperation.CREATE_MAC_PKG), "mac-bundle",
                Set.copyOf(StandardBundlingOperation.LINUX), "linux",
                Set.copyOf(StandardBundlingOperation.CREATE_NATIVE), "native-bundle",
                Set.copyOf(StandardBundlingOperation.CREATE_BUNDLE), "bundle",
                Set.of(StandardBundlingOperation.values()), "all"
        );
    }

    private static final Path GOLDEN_JPACKAGE_OPTIONS_MD = TKit.TEST_SRC_ROOT.resolve(
            "junit/share/jdk.jpackage/jdk/jpackage/internal/cli/jpackage-options.md");

}
