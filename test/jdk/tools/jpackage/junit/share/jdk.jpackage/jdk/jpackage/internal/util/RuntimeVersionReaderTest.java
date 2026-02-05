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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RuntimeVersionReaderTest {

    @ParameterizedTest
    @CsvSource({
        "27.1.2, true",
        "27.1.2, false",
        "27.1.2-ea, true",
        "27.1.2-ea, false"
    })
    public void test_release_file_with_version(String version,
            boolean quoteVersion, @TempDir Path workdir) {
        final var value = RuntimeVersionReader.readVersion(
                createPropFileWithValue(workdir, "JAVA_VERSION", version, quoteVersion));
        assertTrue(value.isPresent());
        value.ifPresent(val -> {
            assertEquals(version, value.get().toString());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "7.1.2+foo, true",
        "foo, true",
        "'', true",
        "7.1.2+foo, false",
        "foo, false",
        "'', false"
    })
    public void test_release_file_with_invalid_version(String version,
            boolean quoteVersion, @TempDir Path workdir) {
        final var value = RuntimeVersionReader.readVersion(
                createPropFileWithValue(workdir, "JAVA_VERSION", version, quoteVersion));
        assertFalse(value.isPresent());
    }

    @Test
    public void test_release_file_without_version(@TempDir Path workdir) {
        final var value = RuntimeVersionReader.readVersion(
                createPropFileWithValue(workdir, "JDK_VERSION", "27.1.2", true));
        assertFalse(value.isPresent());
    }

    @Test
    public void test_release_file_invalid_input(@TempDir Path workdir) {
        final var value = RuntimeVersionReader.readVersion(workdir);
        assertFalse(value.isPresent());
    }

    private Path createPropFileWithValue(Path workdir, String name, String value,
                boolean quoteValue) {
        Path releaseFile = workdir.resolve("release");
        Properties props = new Properties();
        if (quoteValue) {
            props.setProperty(name, "\"" + value + "\"");
        } else {
            props.setProperty(name, value);
        }

        try (Writer writer = Files.newBufferedWriter(releaseFile)) {
            props.store(writer, null);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return releaseFile;
    }
}
