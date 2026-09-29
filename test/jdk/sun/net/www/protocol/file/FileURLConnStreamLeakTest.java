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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8367561
 * @summary verify that the implementation of URLConnection APIs for "file:"
 *          protocol does not leak InputStream(s)
 * @run junit/othervm ${test.main.class}
 */
class FileURLConnStreamLeakTest {

    private static final String FILE_URLCONNECTION_CLASSNAME = "sun.net.www.protocol.file.FileURLConnection";

    private Path testFile;
    // FileInputStream has a Cleaner which closes its underlying file descriptor.
    // Here we keep reference to the URLConnection for the duration of each test method.
    // This ensures that any FileInputStream this URLConnection may be retaining, won't be GCed
    // until after the test method has checked for file descriptor leaks.
    private URLConnection conn;

    @BeforeEach
    void beforeEach() throws Exception {
        final Path file = Files.createTempFile(Path.of("."), "8367561-", ".txt");
        Files.writeString(file, String.valueOf(System.currentTimeMillis()));
        this.testFile = file;
        this.conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(this.conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
    }

    @AfterEach
    void afterEach() throws Exception {
        this.conn = null;
        // the file should already have been deleted by the test method
        Files.deleteIfExists(this.testFile);
    }

    static List<Consumer<URLConnection>> urlConnOperations() {
        return List.of(
                URLConnection::getContentEncoding,
                URLConnection::getContentLength,
                URLConnection::getContentLengthLong,
                URLConnection::getContentType,
                URLConnection::getDate,
                URLConnection::getExpiration,
                URLConnection::getLastModified
        );
    }

    @MethodSource("urlConnOperations")
    @ParameterizedTest
    void testURLConnOps(final Consumer<URLConnection> connConsumer) throws IOException {
        connConsumer.accept(this.conn);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetHeaderField() throws Exception {
        final var _ = this.conn.getHeaderField(0);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetHeaderFieldString() throws Exception {
        final String val = this.conn.getHeaderField("foo");
        assertNull(val, "unexpected header field value: " + val);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetHeaderFieldDate() throws Exception {
        final var _ = this.conn.getHeaderFieldDate("bar", 42);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetHeaderFieldInt() throws Exception {
        final int val = this.conn.getHeaderFieldInt("hello", 42);
        assertEquals(42, val, "unexpected header value");
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetHeaderFieldKey() throws Exception {
        final String val = this.conn.getHeaderFieldKey(42);
        assertNull(val, "unexpected header value: " + val);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetHeaderFieldLong() throws Exception {
        final long val = this.conn.getHeaderFieldLong("foo", 42);
        assertEquals(42, val, "unexpected header value");
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetHeaderFields() throws Exception {
        final Map<String, List<String>> headers = this.conn.getHeaderFields();
        assertNotNull(headers, "null headers");
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetInputStream() throws Exception {
        try (final InputStream is = this.conn.getInputStream()) {
            assertNotNull(is, "input stream is null");
        }
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }

    @Test
    void testGetOutputStream() throws Exception {
        // FileURLConnection only supports reading
        assertThrows(UnknownServiceException.class, this.conn::getOutputStream);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
    }
}

