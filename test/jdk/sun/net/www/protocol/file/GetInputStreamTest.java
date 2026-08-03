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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary verify the behaviour of URLConnection.getInputStream()
 *          for "file:" protocol
 * @run junit ${test.main.class}
 */
class GetInputStreamTest {

    /**
     * Calls URLConnection.getInputStream() on the URLConnection for a directory and verifies
     * the contents returned by the InputStream.
     */
    @Test
    void testDirInputStream() throws Exception {
        final Path dir = Files.createTempDirectory(Path.of("."), "fileurlconn-");
        final int numEntries = 3;
        // write some files into that directory
        for (int i = 1; i <= numEntries; i++) {
            Files.writeString(dir.resolve(i + ".txt"), "" + i);
        }
        final String expectedDirListing = getDirListing(dir.toFile(), numEntries);
        final URLConnection conn = dir.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection is null for " + dir);
        // call getInputStream() and verify that the streamed directory
        // listing is the expected one
        try (final InputStream is = conn.getInputStream()) {
            assertNotNull(is, "InputStream is null for " + conn);
            final String actual = new BufferedReader(new InputStreamReader(is))
                    .readAllAsString();
            assertEquals(expectedDirListing, actual,
                    "unexpected content from input stream for dir " + dir);
        }
        // now that we successfully obtained the InputStream, read its content
        // and closed it, call getInputStream() again and verify that it can no longer
        // be used to read any more content.
        try (final InputStream is = conn.getInputStream()) {
            assertNotNull(is, "input stream is null for " + conn);
            final int readByte = is.read();
            assertEquals(-1, readByte, "expected to have read EOF from the stream");
        }
    }

    /**
     * Calls URLConnection.getInputStream() on the URLConnection for a regular file and verifies
     * the contents returned by the InputStream.
     */
    @Test
    void testRegularFileInputStream() throws Exception {
        final Path dir = Files.createTempDirectory(Path.of("."), "fileurlconn-");
        final Path regularFile = dir.resolve("foo.txt");
        final String expectedContent = "bar";
        Files.writeString(regularFile, expectedContent);

        final URLConnection conn = regularFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection is null for " + regularFile);
        // get the input stream and verify the streamed content
        try (final InputStream is = conn.getInputStream()) {
            assertNotNull(is, "input stream is null for " + conn);
            final String actual = new BufferedReader(new InputStreamReader(is))
                    .readAllAsString();
            assertEquals(expectedContent, actual,
                    "unexpected content from input stream for file " + regularFile);
        }
        // now that we successfully obtained the InputStream, read its content
        // and closed it, call getInputStream() again and verify that it can no longer
        // be used to read any more content.
        try (final InputStream is = conn.getInputStream()) {
            assertNotNull(is, "input stream is null for " + conn);
            // for regular files the FileURLConnection's InputStream throws a IOException
            // when attempting to read after EOF
            final IOException thrown = assertThrows(IOException.class, is::read);
            final String exMessage = thrown.getMessage();
            assertEquals("Stream closed", exMessage, "unexpected exception message");
        }
    }

    /**
     * Verifies that URLConnection.getInputStream() for a non-existent file path
     * throws FileNotFoundException.
     */
    @Test
    void testNonExistentFile() throws Exception {
        final Path existentDir = Files.createTempDirectory(Path.of("."), "fileurlconn-");
        final Path nonExistent = existentDir.resolve("non-existent");
        final URLConnection conn = nonExistent.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection is null for " + nonExistent);
        final FileNotFoundException thrown = assertThrows(FileNotFoundException.class,
                conn::getInputStream);
        final String exMessage = thrown.getMessage();
        assertTrue(exMessage != null && exMessage.contains(nonExistent.getFileName().toString()),
                "unexpected exception message: " + exMessage);
    }

    private static String getDirListing(final File dir, final int numExpectedEntries) {
        final List<String> dirListing = Arrays.asList(dir.list());
        dirListing.sort(Collator.getInstance()); // same as what FileURLConnection does

        assertEquals(numExpectedEntries, dirListing.size(),
                dir + " - expected " + numExpectedEntries + " entries but found: " + dirListing);

        final StringBuilder sb = new StringBuilder();
        for (String fileName : dirListing) {
            sb.append(fileName);
            sb.append("\n");
        }
        return sb.toString();
    }
}
