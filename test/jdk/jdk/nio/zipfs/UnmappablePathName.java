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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/* @test
 * @bug 8380450
 * @summary Unmappable characters in ZipFileSystem path names should be rejected with InvalidPathException
 * @run junit ${test.main.class}
 */
public class UnmappablePathName {

    // Charset used when creating the ZipFileSystem used in this test
    static final Charset CHARSET = StandardCharsets.US_ASCII;
    // 'ø' is an unmappable character in US_ASCII
    static final String UNMAPPABLE = "\u00f8";
    // ZIP file created in this test
    static final Path ZIP = Paths.get("unmappable-path.zip");

    /**
     * Verify that calling ZipFileSystem.getPath with an unmappable path
     * name is rejected with an InvalidPathException.
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @Test
    void unmappableGetPath() throws IOException {
        try (FileSystem fs = createFileSystem(ZIP, CHARSET)) {
            assertThrows(InvalidPathException.class, () -> fs.getPath(UNMAPPABLE));
        }
    }

    /**
     * Verify that calling ZipFileSystem.getPath with a partially unmappable path
     * name is rejected with an InvalidPathException.
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @Test
    void unmappableGetPathPartial() throws IOException {
        try (FileSystem fs = createFileSystem(ZIP, CHARSET)) {
            assertThrows(InvalidPathException.class, () -> fs.getPath("mappable", UNMAPPABLE));
        }
    }

    /**
     * Verify that calling ZipPath::resolve with an unmappable path
     * name is rejected with an InvalidPathException.
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @Test
    void unmappableResolve() throws IOException {
        try (FileSystem fs = createFileSystem(ZIP, CHARSET)) {
            Path path = fs.getPath("mappable");
            assertThrows(InvalidPathException.class, () -> path.resolve(UNMAPPABLE));
        }
    }

    /**
     * Verify that calling ZipPath::resolve with a partially unmappable path
     * name is rejected with an InvalidPathException.
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @Test
    void unmappableResolvePartial() throws IOException {
        try (FileSystem fs = createFileSystem(ZIP, CHARSET)) {
            Path path = fs.getPath("mappable");
            assertThrows(InvalidPathException.class, () -> path.resolve("mappable", UNMAPPABLE));
        }
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(ZIP);
    }

    // Create a ZipFileSystem using the specified charset
    private FileSystem createFileSystem(Path path, Charset charset) throws IOException {
        URI uri = URI.create("jar:" + path.toUri());
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("create", "true");
        env.put("encoding", charset.name());
        return FileSystems.newFileSystem(uri, env);
    }
}
