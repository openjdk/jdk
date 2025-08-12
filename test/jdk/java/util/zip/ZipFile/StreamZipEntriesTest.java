/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @run junit StreamZipEntriesTest
 * @summary Make sure we can stream entries of a zip file.
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;


public class StreamZipEntriesTest {

    // ZIP file produced in this test
    private Path zip = Path.of("stream.zip");
    // JAR file produced in this test
    private Path jar = Path.of("stream.jar");

    /**
     * Create sample ZIP and JAR files used in in this test
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void setUp() throws IOException {

        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("entry1.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
            zo.putNextEntry(new ZipEntry("entry2.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
        }

        try (OutputStream out = Files.newOutputStream(jar);
             ZipOutputStream zo = new ZipOutputStream(out)) {
            // A JAR file may start with a META-INF/ directory before the manifest
            zo.putNextEntry(new ZipEntry("META-INF/"));
            // Write the manifest
            zo.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            new Manifest().write(zo);

            // Write two regular entries
            zo.putNextEntry(new ZipEntry("entry1.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
            zo.putNextEntry(new ZipEntry("entry2.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Delete the ZIP file produced after each test method
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
        Files.deleteIfExists(jar);
    }

    /**
     * Verify that ZipFile.stream() produces the expected entries
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void testStreamZip() throws IOException {
        Set<String> names = new HashSet<>(Set.of("entry1.txt", "entry2.txt"));

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream().forEach(e -> {
                assertTrue(e instanceof ZipEntry);
                String name = e.getName();
                assertNotNull(names.remove(name));
                String toString = e.toString();
                assertEquals(name, toString);
            });

            // Check that all expected names were processed
            assertTrue(names.isEmpty());

            // Check that Stream.toArray produces the expected result
            Object elements[] = zf.stream().toArray();
            assertEquals(2, elements.length);
            assertEquals(elements[0].toString(), "entry1.txt");
            assertEquals(elements[1].toString(), "entry2.txt");
        }
    }

    /**
     * Verify that JarFile.stream() produces the expected entries
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void testStreamJar() throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            Set<String> names = new HashSet<>(Set.of(
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "entry1.txt",
                    "entry2.txt"
            ));

            jf.stream().forEach(e -> {
                        assertTrue(e instanceof JarEntry);
                        String name = e.getName();
                        assertNotNull(names.remove(name));
                        String toString = e.toString();
                        assertEquals(name, toString);
                    }
            );

            // Check that all expected names were processed
            assertTrue(names.isEmpty(), "Unprocessed entries: " + names);


            // Check that Stream.toArray produces the expected result
            Object elements[] = jf.stream().toArray();
            assertEquals(4, elements.length);
            assertEquals(elements[0].toString(), "META-INF/");
            assertEquals(elements[1].toString(), "META-INF/MANIFEST.MF");
            assertEquals(elements[2].toString(), "entry1.txt");
            assertEquals(elements[3].toString(), "entry2.txt");
        }
    }

    /**
     * Calling ZipFile.stream() on a closed ZipFile should throw ISE
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void testClosedZipFile() throws IOException {
        ZipFile zf = new ZipFile(zip.toFile());
        zf.close();
        assertThrows(IllegalStateException.class, () -> {
            Stream s = zf.stream();
        });
    }

    /**
     * Calling JarFile.stream() on a closed JarFile should throw ISE
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void testClosedJarFile() throws IOException {
        JarFile jf = new JarFile(jar.toFile());
        jf.close();
        assertThrows(IllegalStateException.class, () -> {
            Stream s = jf.stream();
        });
    }
}
