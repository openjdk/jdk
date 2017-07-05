/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation, and proper error handling, might not be present in
 * this sample code.
 */

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This sample demonstrates the ability to create custom resource that
 * implements the {@code AutoCloseable} interface. This resource can be used in
 * the try-with-resources construct.
 */
public class CustomAutoCloseableSample {

    /**
     * The main method for the CustomAutoCloseableSample program.
     *
     * @param args is not used.
     */
    public static void main(String[] args) {
        /*
         * TeeStream will be closed automatically after the try block.
         */
        try (TeeStream teeStream = new TeeStream(System.out, Paths.get("out.txt"));
             PrintStream out = new PrintStream(teeStream)) {
            out.print("Hello, world");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Passes the output through to the specified output stream while copying it into a file.
     * The TeeStream functionality is similar to the Unix tee utility.
     * TeeStream implements AutoCloseable interface. See OutputStream for details.
     */
    public static class TeeStream extends OutputStream {

        private final OutputStream fileStream;
        private final OutputStream outputStream;

        /**
         * Creates a TeeStream.
         *
         * @param outputStream an output stream.
         * @param outputFile   an path to file.
         * @throws IOException If an I/O error occurs.
         */
        public TeeStream(OutputStream outputStream, Path outputFile) throws IOException {
            this.fileStream = new BufferedOutputStream(Files.newOutputStream(outputFile));
            this.outputStream = outputStream;
        }

        /**
         * Writes the specified byte to the specified output stream
         * and copies it to the file.
         *
         * @param b the byte to be written.
         * @throws IOException If an I/O error occurs.
         */
        @Override
        public void write(int b) throws IOException {
            fileStream.write(b);
            outputStream.write(b);
        }

        /**
         * Flushes this output stream and forces any buffered output bytes
         * to be written out.
         * The <code>flush</code> method of <code>TeeStream</code> flushes
         * the specified output stream and the file output stream.
         *
         * @throws IOException if an I/O error occurs.
         */
        @Override
        public void flush() throws IOException {
            outputStream.flush();
            fileStream.flush();
        }

        /**
         * Closes underlying streams and resources.
         * The external output stream won't be closed.
         * This method is the member of AutoCloseable interface and
         * it will be invoked automatically after the try-with-resources block.
         *
         * @throws IOException If an I/O error occurs.
         */
        @Override
        public void close() throws IOException {
            try (OutputStream file = fileStream) {
                flush();
            }
        }
    }
}
