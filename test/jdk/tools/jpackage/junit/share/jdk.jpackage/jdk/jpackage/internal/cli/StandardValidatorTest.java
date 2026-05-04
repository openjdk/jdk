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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.Validator.ValidatingConsumerException;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.test.JUnitUtils.ExceptionPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StandardValidatorTest {

    @Test
    void test_IS_DIRECTORY(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_DIRECTORY;

        assertTrue(testee.test(tempDir));
        assertFalse(testee.test(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        assertFalse(testee.test(file));
        assertTrue(testee.test(tempDir));

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @Test
    void test_IS_FILE_OR_SYMLINK(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_FILE_OR_SYMLINK;

        assertFalse(testee.test(tempDir));
        assertFalse(testee.test(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        assertTrue(testee.test(file));
        assertFalse(testee.test(tempDir));

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void test_IS_FILE_OR_SYMLINK_symlink_file(@TempDir Path tempDir) throws IOException {

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        final var symlink = Files.createSymbolicLink(tempDir.resolve("foo-symlink"), file);

        assertTrue(StandardValidator.IS_FILE_OR_SYMLINK.test(symlink));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void test_IS_FILE_OR_SYMLINK_symlink_dir(@TempDir Path tempDir) throws IOException {

        final var symlink = Files.createSymbolicLink(tempDir.resolve("foo-symlink"), tempDir);

        assertFalse(StandardValidator.IS_FILE_OR_SYMLINK.test(symlink));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void test_IS_FILE_OR_SYMLINK_symlink_invalid(@TempDir Path tempDir) throws IOException {

        final var symlink = Files.createSymbolicLink(tempDir.resolve("foo-symlink"), tempDir.resolve("foo"));

        assertFalse(StandardValidator.IS_FILE_OR_SYMLINK.test(symlink));
    }

    @Test
    void test_IS_DIRECTORY_OR_NON_EXISTENT(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_DIRECTORY_OR_NON_EXISTENT;

        assertTrue(testee.test(tempDir));
        assertTrue(testee.test(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        assertFalse(testee.test(file));
        assertTrue(testee.test(tempDir));

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @Test
    void test_IS_DIRECTORY_EMPTY_OR_NON_EXISTENT(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_DIRECTORY_EMPTY_OR_NON_EXISTENT;

        assertDoesNotThrow(() -> testee.accept(tempDir));
        assertDoesNotThrow(() -> testee.accept(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        var cause = assertThrowsExactly(ValidatingConsumerException.class, () -> testee.accept(file)).getCause();
        assertEquals(NotDirectoryException.class, cause.getClass());

        cause = assertThrowsExactly(ValidatingConsumerException.class, () -> testee.accept(tempDir)).getCause();
        assertEquals(DirectoryNotEmptyException.class, cause.getClass());

        assertThrowsExactly(NullPointerException.class, () -> testee.accept(null));
    }

    @Test
    void test_IS_URL() {

        final var testee = StandardValidator.IS_URL;

        assertDoesNotThrow(() -> testee.accept("http://foo"));

        final var ex = assertThrowsExactly(ValidatingConsumerException.class, () -> testee.accept(":"));

        assertNotNull(ex.getCause());

        assertThrowsExactly(NullPointerException.class, () -> testee.accept(null));
    }

    @ParameterizedTest
    @MethodSource
    void test_IS_NAME_VALID_valid(String name) {

        final var testee = StandardValidator.IS_NAME_VALID;

        assertTrue(testee.test(name));
    }

    @ParameterizedTest
    @MethodSource
    void test_IS_NAME_VALID_invalid(String name) {

        final var testee = StandardValidator.IS_NAME_VALID;

        assertFalse(testee.test(name));
    }

    @Test
    void test_IS_NAME_VALID_null() {

        final var testee = StandardValidator.IS_NAME_VALID;

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Foo", "1", "public"})
    void test_IS_CLASSNAME_valid(String classname) {

        final var testee = StandardValidator.IS_CLASSNAME;

        testee.accept(classname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a/b"})
    void test_IS_CLASSNAME_invalid(String classname) {

        final var testee = StandardValidator.IS_CLASSNAME;

        final var ex = assertThrowsExactly(ValidatingConsumerException.class, () -> testee.accept(classname));

        assertTrue(new ExceptionPattern().isCauseInstanceOf(IllegalArgumentException.class).match(ex));
    }

    @Test
    void test_IS_CLASSNAME_null() {

        final var testee = StandardValidator.IS_CLASSNAME;

        assertThrowsExactly(NullPointerException.class, () -> testee.accept(null));
    }

    @Test
    void test_IS_MAC_BUNDLE_valid(@TempDir Path workDir) throws IOException {

        final var testee = StandardValidator.IS_MAC_BUNDLE;

        for (var component : MacBundleComponent.values()) {
            component.create(workDir);
        }

        assertTrue(testee.test(workDir));
    }

    @ParameterizedTest
    @EnumSource(MacBundleComponent.class)
    void test_IS_MAC_BUNDLE_invalid(MacBundleComponent missing, @TempDir Path workDir) throws IOException {

        final var testee = StandardValidator.IS_MAC_BUNDLE;

        for (var component : MacBundleComponent.values()) {
            component.create(workDir);
        }

        FileUtils.deleteRecursive(workDir.resolve(missing.path()));

        assertFalse(testee.test(workDir));
    }

    @Test
    void test_IS_MAC_BUNDLE_null() {

        final var testee = StandardValidator.IS_MAC_BUNDLE;

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a",
            "a.b",
            "a.B10-",
    })
    void test_IS_MAC_BUNDLE_IDENTIFIER_valid(String value) {

        final var testee = StandardValidator.IS_MAC_BUNDLE_IDENTIFIER;

        assertTrue(testee.test(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "#",
            "a\\",
            "a_b",
    })
    void test_IS_MAC_BUNDLE_IDENTIFIER_invalid(String value) {

        final var testee = StandardValidator.IS_MAC_BUNDLE_IDENTIFIER;

        assertFalse(testee.test(value));
    }

    @Test
    void test_IS_MAC_BUNDLE_IDENTIFIER_null() {

        final var testee = StandardValidator.IS_MAC_BUNDLE_IDENTIFIER;

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "aa",
            "a.b10z+-",
    })
    void test_IS_LINUX_DEB_PACKAGE_NAME_valid(String value) {

        final var testee = StandardValidator.IS_LINUX_DEB_PACKAGE_NAME;

        assertTrue(testee.test(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "a",
            "1a",
            "aA",
            "a\\a",
    })
    void test_IS_LINUX_DEB_PACKAGE_NAME_invalid(String value) {

        final var testee = StandardValidator.IS_LINUX_DEB_PACKAGE_NAME;

        assertFalse(testee.test(value));
    }

    @Test
    void test_IS_LINUX_DEB_PACKAGE_NAME_null() {

        final var testee = StandardValidator.IS_LINUX_DEB_PACKAGE_NAME;

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "aa",
            "a.b10z+-",
            "a.B10z+-_",
            "a",
            "1a",
    })
    void test_IS_LINUX_RPM_PACKAGE_NAME_valid(String value) {

        final var testee = StandardValidator.IS_LINUX_RPM_PACKAGE_NAME;

        assertTrue(testee.test(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "$",
            "foo=bar"
    })
    void test_IS_LINUX_RPM_PACKAGE_NAME_invalid(String value) {

        final var testee = StandardValidator.IS_LINUX_RPM_PACKAGE_NAME;

        assertFalse(testee.test(value));
    }

    @Test
    void test_IS_LINUX_RPM_PACKAGE_NAME_null() {

        final var testee = StandardValidator.IS_LINUX_RPM_PACKAGE_NAME;

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @ParameterizedTest
    @MethodSource
    void test_installDirValidator_valid(PackageType type, Path value) {

        final var testee = StandardValidator.installDirValidator(type);

        assertTrue(testee.test(value));
    }

    @ParameterizedTest
    @MethodSource
    void test_installDirValidator_invalid(PackageType type, Path value) {

        final var testee = StandardValidator.installDirValidator(type);

        assertFalse(testee.test(value));
    }

    @Test
    void test_installDirValidator_null() {

        assertThrowsExactly(NullPointerException.class, () -> StandardValidator.installDirValidator(null));

        final var testee = StandardValidator.installDirValidator(DummyPackageType.DUMMY);

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    private static Stream<Arguments> test_IS_NAME_VALID_valid() {
        List<String> data = new ArrayList<>();
        data.addAll(List.of(
                "a",
                "a!",
                "name with space"
        ));

        if (!OperatingSystem.isWindows()) {
            data.addAll(List.of("a?", "*foo*"));
        }

        return data.stream().map(Arguments::of);
    }

    private static Stream<Arguments> test_IS_NAME_VALID_invalid() {
        List<String> data = new ArrayList<>();
        data.addAll(List.of(
                " ",
                "a\0b",
                "foo ",
                "",
                "a/b",
                "a\\b",
                "ab\\",
                "ab/",
                "a%b",
                "a\"b"
        ));

        if (OperatingSystem.isWindows()) {
            data.addAll(List.of("a?", "*foo*"));
        }

        return data.stream().map(Arguments::of);
    }

    private static Collection<Arguments> test_installDirValidator_valid() {
        var testCases = new ArrayList<Arguments>();

        if (OperatingSystem.isWindows()) {
            for (var type : packageTypes(StandardBundlingOperation.WINDOWS_CREATE_NATIVE)) {
                testCases.add(Arguments.of(type, "Foo"));
                testCases.add(Arguments.of(type, "Foo/\\/"));
            }
        }

        if (OperatingSystem.isMacOS()) {
            for (var type : packageTypes(StandardBundlingOperation.MACOS_CREATE_NATIVE)) {
                testCases.add(Arguments.of(type, "/Application"));
                testCases.add(Arguments.of(type, "/Application//"));
            }
        }

        if (OperatingSystem.isLinux()) {
            for (var type : packageTypes(StandardBundlingOperation.LINUX_CREATE_NATIVE)) {
                testCases.add(Arguments.of(type, "/opt"));
                testCases.add(Arguments.of(type, "/opt//"));
                testCases.add(Arguments.of(type, "/foo/bar///"));
            }
        }

        final var root = Path.of("").toAbsolutePath().getRoot().toString();

        testCases.add(Arguments.of(DummyPackageType.DUMMY, root + "foo/bar///"));
        testCases.add(Arguments.of(DummyPackageType.DUMMY, "foo/bar///"));

        return testCases;
    }

    private static Collection<Arguments> test_installDirValidator_invalid() {
        final var testCases = new ArrayList<Arguments>();

        final var root = Path.of("").toAbsolutePath().getRoot().toString();

        for (var type : Stream.concat(
                Stream.of(DummyPackageType.DUMMY),
                packageTypes(StandardBundlingOperation.CREATE_NATIVE).stream()
        ).toList()) {
            testCases.add(Arguments.of(type, ""));
            testCases.add(Arguments.of(type, "."));
            testCases.add(Arguments.of(type, ".."));
            testCases.add(Arguments.of(type, "../foo/bar"));
            testCases.add(Arguments.of(type, root));
        }

        for (var invalidSuffix : List.of(
                "/./",
                "/.",
                "/../",
                "/.."
        )) {
            for (var bundlingOperation : StandardBundlingOperation.CREATE_NATIVE) {
                final String validRoot;
                if (bundlingOperation.descriptor().os() == OperatingSystem.WINDOWS) {
                    validRoot = "Foo";
                    testCases.add(Arguments.of(toPackageType(bundlingOperation), root + "Bar"));
                } else {
                    validRoot = root + "foo";
                    testCases.add(Arguments.of(toPackageType(bundlingOperation), "bar"));
                }

                testCases.add(Arguments.of(toPackageType(bundlingOperation), validRoot + invalidSuffix));
            }

            testCases.add(Arguments.of(DummyPackageType.DUMMY, "Foo" + invalidSuffix));
            testCases.add(Arguments.of(DummyPackageType.DUMMY, root + "foo" + invalidSuffix));
        }

        return testCases;
    }

    private static Collection<PackageType> packageTypes(Collection<BundlingOperationOptionScope> bundlingOperations) {
        return bundlingOperations.stream().map(StandardValidatorTest::toPackageType).toList();
    }

    private static PackageType toPackageType(BundlingOperationOptionScope bundlingOperation) {
        return ((StandardBundlingOperation)bundlingOperation).packageType();
    }

    enum MacBundleComponent {
        CONTENTS_DIR("Contents"),
        MACOS_DIR("Contents/MacOS"),
        INFO_PLIST("Contents/Info.plist"),
        ;

        MacBundleComponent(String path) {
            this.path = Path.of(Objects.requireNonNull(path));
        }

        Path path() {
            return path;
        }

        boolean isDirectory() {
            return name().endsWith("_DIR");
        }

        void create(Path root) throws IOException {
            var fullPath = root.resolve(path);
            if (isDirectory()) {
                Files.createDirectories(fullPath);
            } else {
                Files.createDirectories(fullPath.getParent());
                Files.createFile(fullPath);
            }
        }

        private final Path path;
    }

    // Enum to make it readable in test descriptions
    private enum DummyPackageType implements PackageType {
        DUMMY;

        @Override
        public String label() {
            throw new AssertionError();
        }
    };
}
