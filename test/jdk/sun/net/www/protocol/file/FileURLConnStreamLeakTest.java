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

import java.io.InputStream;
import java.lang.ref.Reference;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void beforeEach() throws Exception {
        final Path file = Files.createTempFile(Path.of("."), "8367561-", ".txt");
        Files.writeString(file, String.valueOf(System.currentTimeMillis()));
        this.testFile = file;
    }

    @AfterEach
    void afterEach() throws Exception {
        Files.deleteIfExists(this.testFile);
    }


    @Test
    void testGetContentEncoding() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getContentEncoding();
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetContentLength() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getContentLength();
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetContentLengthLong() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getContentLengthLong();
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetContentType() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getContentType();
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetDate() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getDate();
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetExpiration() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getExpiration();
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetHeaderField() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getHeaderField(0);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetHeaderFieldString() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final String val = conn.getHeaderField("foo");
        assertNull(val, "unexpected header field value: " + val);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetHeaderFieldDate() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getHeaderFieldDate("bar", 42);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetHeaderFieldInt() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final int val = conn.getHeaderFieldInt("hello", 42);
        assertEquals(42, val, "unexpected header value");
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetHeaderFieldKey() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final String val = conn.getHeaderFieldKey(42);
        assertNull(val, "unexpected header value: " + val);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetHeaderFieldLong() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final long val = conn.getHeaderFieldLong("foo", 42);
        assertEquals(42, val, "unexpected header value");
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetHeaderFields() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final Map<String, List<String>> headers = conn.getHeaderFields();
        assertNotNull(headers, "null headers");
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetLastModified() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        final var _ = conn.getLastModified();
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetInputStream() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        try (final InputStream is = conn.getInputStream()) {
            assertNotNull(is, "input stream is null");
        }
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }

    @Test
    void testGetOutputStream() throws Exception {
        final URLConnection conn = this.testFile.toUri().toURL().openConnection();
        assertNotNull(conn, "URLConnection for " + this.testFile + " is null");
        assertEquals(FILE_URLCONNECTION_CLASSNAME, conn.getClass().getName(),
                "unexpected URLConnection type");
        // FileURLConnection only supports reading
        assertThrows(UnknownServiceException.class, conn::getOutputStream);
        // verify that the URLConnection isn't holding on to any file descriptors
        // of this test file.
        Files.delete(this.testFile); // must not fail
        // FileInputStream has a Cleaner which closes its underlying file descriptor.
        // Here we add a reachability fence to URLConnection. This ensures that any FileInputStream
        // it may be retaining, won't be GCed until after we have checked for file descriptor
        // leaks on the previous line.
        Reference.reachabilityFence(conn);
    }
}

