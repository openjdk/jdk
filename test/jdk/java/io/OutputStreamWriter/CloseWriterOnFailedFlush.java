/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136895
 * @summary Verify stream closed after write error in StreamEncoder::implClose
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;

public class CloseWriterOnFailedFlush {
    private static final String STR_IOE = "Test";   // IOException
    private static final String STR_MIE = "\ud83c"; // MalformedInputException

    public static void main(String[] args) throws IOException {
        boolean failed = false;

        for (String s : new String[] {STR_IOE, STR_MIE}) {
            System.out.println("string: " + s);
            ErroringOutputStream stream = new ErroringOutputStream();
            try (Writer writer = new OutputStreamWriter(stream,
                     StandardCharsets.UTF_8.newEncoder())) {
                writer.write(s);
            } catch (IOException ex) {
                Class exClass = ex.getClass();
                if (s.equals(STR_IOE) && exClass != IOException.class ||
                    s.equals(STR_MIE) && exClass != MalformedInputException.class)
                    throw ex;
            }

            if (stream.isOpen()) {
                System.err.println("Stream is STILL open");
                failed = true;
            } else {
                System.out.println("Stream is closed");
            }
        }

        if (failed)
            throw new RuntimeException("Test failed");
    }

    private static class ErroringOutputStream extends OutputStream {
        private boolean open = true;

        @Override
        public void write(int b) throws IOException {
            throw new IOException();
        }

        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
            System.out.println("Closing");
        }
    }
}
