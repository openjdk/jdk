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
import java.util.List;
import jdk.jpackage.test.JUnitUtils.ExceptionPattern;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.Validator.ValidatingConsumerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class StandardValidatorTest {

    @Test
    public void test_IS_DIRECTORY(@TempDir Path tempDir) throws IOException {

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
    public void test_IS_FILE_OR_SYMLINK(@TempDir Path tempDir) throws IOException {

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
    public void test_IS_FILE_OR_SYMLINK_symlink_file(@TempDir Path tempDir) throws IOException {

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        final var symlink = Files.createSymbolicLink(tempDir.resolve("foo-symlink"), file);

        assertTrue(StandardValidator.IS_FILE_OR_SYMLINK.test(symlink));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void test_IS_FILE_OR_SYMLINK_symlink_dir(@TempDir Path tempDir) throws IOException {

        final var symlink = Files.createSymbolicLink(tempDir.resolve("foo-symlink"), tempDir);

        assertFalse(StandardValidator.IS_FILE_OR_SYMLINK.test(symlink));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void test_IS_FILE_OR_SYMLINK_symlink_invalid(@TempDir Path tempDir) throws IOException {

        final var symlink = Files.createSymbolicLink(tempDir.resolve("foo-symlink"), tempDir.resolve("foo"));

        assertFalse(StandardValidator.IS_FILE_OR_SYMLINK.test(symlink));
    }

    @Test
    public void test_IS_DIRECTORY_OR_NON_EXISTENT(@TempDir Path tempDir) throws IOException {

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
    public void test_IS_DIRECTORY_EMPTY_OR_NON_EXISTENT(@TempDir Path tempDir) throws IOException {

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
    public void test_IS_URL() {

        final var testee = StandardValidator.IS_URL;

        assertDoesNotThrow(() -> testee.accept("http://foo"));

        final var ex = assertThrowsExactly(ValidatingConsumerException.class, () -> testee.accept(":"));

        assertNotNull(ex.getCause());

        assertThrowsExactly(NullPointerException.class, () -> testee.accept(null));
    }

    @ParameterizedTest
    @MethodSource
    public void test_IS_NAME_VALID_valid(String name) {

        final var testee = StandardValidator.IS_NAME_VALID;

        assertTrue(testee.test(name));
    }

    @ParameterizedTest
    @MethodSource
    public void test_IS_NAME_VALID_invalid(String name) {

        final var testee = StandardValidator.IS_NAME_VALID;

        assertFalse(testee.test(name));
    }

    @Test
    public void test_IS_NAME_VALID_null() {

        final var testee = StandardValidator.IS_NAME_VALID;

        assertThrowsExactly(NullPointerException.class, () -> testee.test(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Foo", "1", "public"})
    public void test_IS_CLASSNAME_valid(String classname) {

        final var testee = StandardValidator.IS_CLASSNAME;

        testee.accept(classname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a/b"})
    public void test_IS_CLASSNAME_invalid(String classname) {

        final var testee = StandardValidator.IS_CLASSNAME;

        final var ex = assertThrowsExactly(ValidatingConsumerException.class, () -> testee.accept(classname));

        assertTrue(new ExceptionPattern().isCauseInstanceOf(IllegalArgumentException.class).match(ex));
    }

    @Test
    public void test_IS_CLASSNAME_null() {

        final var testee = StandardValidator.IS_CLASSNAME;

        assertThrowsExactly(NullPointerException.class, () -> testee.accept(null));
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
}
