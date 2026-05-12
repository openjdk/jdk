/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuntimeReleaseFileTest {

    @Test
    void test_invalid_input(@TempDir Path workdir) {
        assertThrows(IOException.class, () -> {
            new RuntimeReleaseFile(workdir);
        });
    }

    @Test
    void test_findRawProperty(@TempDir Path workdir) throws IOException {
        var releaseFile = new RuntimeReleaseFile(createPropFile(workdir, Map.of("Name", "John", "Company", "\"Acme LTD\"")));

        assertEquals(Optional.empty(), releaseFile.findRawProperty("foo"));
        assertEquals(Optional.of("John"), releaseFile.findRawProperty("Name"));
        assertEquals(Optional.of("\"Acme LTD\""), releaseFile.findRawProperty("Company"));
    }

    @Test
    void test_findProperty(@TempDir Path workdir) throws IOException {
        var releaseFile = new RuntimeReleaseFile(createPropFile(workdir, Map.of("Name", "John", "Company", "\"Acme LTD\"")));

        assertEquals(Optional.empty(), releaseFile.findProperty("foo"));
        assertEquals(Optional.of("John"), releaseFile.findProperty("Name"));
        assertEquals(Optional.of("Acme LTD"), releaseFile.findProperty("Company"));
    }

    @ParameterizedTest
    @CsvSource({
        "foo, foo",
        "\"foo\", foo",
        "'foo', 'foo'",
        "\"f\"o\"o\", f\"o\"o",
        "\"foo, \"foo",
        "foo\", foo\"",
    })
    void test_findProperty(String rawValue, String expectedValue, @TempDir Path workdir) throws IOException {
        var releaseFile = new RuntimeReleaseFile(createPropFile(workdir, Map.of("FOO", rawValue)));

        assertEquals(expectedValue, releaseFile.findProperty("FOO").orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({
        "\"27.1.2\", 27.1.2",
        "27.1.2, 27.1.2",
        "\"27.1.2-ea\", 27.1.2-ea",
        "27.1.2-ea, 27.1.2-ea",
        "\"27.1.2+15\", 27.1.2+15",
        "27.1.2+15, 27.1.2+15",
    })
    void test_getJavaVersion(String version, String expectedVersion, @TempDir Path workdir) throws IOException {
        var releaseFile = new RuntimeReleaseFile(createPropFileWithValue(workdir, "JAVA_VERSION", version));

        final var value = releaseFile.getJavaVersion();

        assertEquals(expectedVersion, value.toString());
    }

    @ParameterizedTest
    @CsvSource({
        "\"7.1.2+foo\"",
        "\"foo\"",
        "\"\"",
        "7.1.2+foo",
        "foo",
        "''"
    })
    void test_getJavaVersion_invalid(String version, @TempDir Path workdir) throws IOException {
        var releaseFile = new RuntimeReleaseFile(createPropFileWithValue(workdir, "JAVA_VERSION", version));

        var ex = assertThrows(RuntimeException.class, releaseFile::getJavaVersion);

        assertFalse(NoSuchElementException.class.isInstance(ex));
    }

    @Test
    void test_without_version(@TempDir Path workdir) throws IOException {
        var releaseFile = new RuntimeReleaseFile(createPropFileWithValue(workdir, "JDK_VERSION", "\"27.1.2\""));

        assertThrowsExactly(NoSuchElementException.class, releaseFile::getJavaVersion);
    }

    @Test
    void test_getModules(@TempDir Path workdir) throws IOException {
        var releaseFile = new RuntimeReleaseFile(createPropFileWithValue(workdir, "MODULES", "foo bar\t  buz  "));

        assertEquals(List.of("foo", "bar", "buz"), releaseFile.getModules());
    }

    @Test
    void test_current() throws IOException {
        var releaseFile = new RuntimeReleaseFile(Path.of(System.getProperty("java.home")).resolve("release"));

        final var expectedVersion = Runtime.version();
        final var actualVersion = releaseFile.getJavaVersion();

        assertEquals(expectedVersion.version(), actualVersion.version());

        final var expectedModules = ModuleFinder.ofSystem().findAll().stream()
                .map(ModuleReference::descriptor).map(ModuleDescriptor::name).sorted().toList();
        final var actualModules = releaseFile.getModules().stream().sorted().toList();

        assertEquals(expectedModules, actualModules);
    }

    private Path createPropFileWithValue(Path workdir, String name, String value) {
        return createPropFile(workdir, Map.of(name, value));
    }

    private Path createPropFile(Path workdir, Map<String, String> input) {
        Path releaseFile = workdir.resolve("foo");
        Properties props = new Properties();
        props.putAll(input);

        try (Writer writer = Files.newBufferedWriter(releaseFile)) {
            props.store(writer, null);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return releaseFile;
    }
}
