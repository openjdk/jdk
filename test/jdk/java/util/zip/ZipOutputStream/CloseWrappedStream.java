/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8313739
   @summary Verify that ZipOutputStream closes the wrapped stream even after failed writes
   @run junit CloseWrappedStream
   */

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class CloseWrappedStream {

    @Test
    public void shouldCloseWrappedStream() throws IOException {
        // A wrapped stream which should be closed even after a write failure
        WrappedOutputStream wrappedStream = new WrappedOutputStream();

        IOException exception = assertThrows(IOException.class, () -> {
            try (ZipOutputStream zo = new ZipOutputStream(wrappedStream)) {
                zo.putNextEntry(new ZipEntry("file.txt"));
                zo.write("hello".getBytes(StandardCharsets.UTF_8));
                // Make next write throw IOException
                wrappedStream.fail = true;
            } // Close throws when deflated data is flushed to wrapped stream
        });

        // Sanity check that we failed with the expected message
        assertEquals(WrappedOutputStream.MSG, exception.getMessage());
        // Verify that the wrapped stream was closed
        assertTrue(wrappedStream.closed, "Expected wrapped output stream to be closed");
    }

    /**
     * Output stream which conditionally throws IOException on writes
     * and tracks its close status.
     */
    static class WrappedOutputStream extends FilterOutputStream {
        static final String MSG = "injected failure";
        boolean fail = false;
        boolean closed = false;

        public WrappedOutputStream() {
            super(new ByteArrayOutputStream());
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException{
            if (fail) {
                throw new IOException(MSG);
            } else {
                super.write(b, off, len);
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
