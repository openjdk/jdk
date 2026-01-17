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

public class RuntimeVersionReaderTest {

    @Test
    public void test_release_file_with_version(@TempDir Path workdir) {
        final Optional<String> version;
        try {
            version = RuntimeVersionReader.readVersion(
                    createPropFileWithValue(workdir, "JAVA_VERSION", "27.1.2"));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        assertTrue(version.isPresent());
        version.ifPresent(ver -> {
            assertEquals("27.1.2", version.get());
        });
    }

    @Test
    public void test_release_file_without_version(@TempDir Path workdir) {
        final Optional<String> version;
        try {
            version = RuntimeVersionReader.readVersion(
                    createPropFileWithValue(workdir, "JDK_VERSION", "27.1.2"));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        assertFalse(version.isPresent());
    }

    @Test
    public void test_release_file_invalid_input(@TempDir Path workdir) {
        final Optional<String> version;
        try {
            version = RuntimeVersionReader.readVersion(workdir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        assertFalse(version.isPresent());
    }

    private Path createPropFileWithValue(Path workdir, String name, String value)
            throws IOException {
        Path releaseFile = workdir.resolve("release");
        Properties props = new Properties();
        props.setProperty(name, "\"" + value + "\"");
        try (Writer writer = Files.newBufferedWriter(releaseFile)) {
            props.store(writer, null);
        }

        return releaseFile;
    }


}
