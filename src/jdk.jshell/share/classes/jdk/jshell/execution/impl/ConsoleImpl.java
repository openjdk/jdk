/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jshell.execution.impl;

import java.io.BufferedOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import jdk.internal.io.JdkConsole;
import jdk.internal.io.JdkConsoleProvider;
import jdk.jshell.JShellConsole;

/**
 *
 */
public class ConsoleImpl {

    public static void ensureOutputAreWritten() {
        var console = ConsoleProviderImpl.console;

        if (console != null) {
            console.ensureOutputAreWritten();
        }
    }

    public static class ConsoleProviderImpl implements JdkConsoleProvider {

        public ConsoleProviderImpl() {
        }

        private static InputStream remoteOutput;
        private static OutputStream remoteInput;
        private static RemoteConsole console;

        @Override
        public JdkConsole console(boolean isTTY, Charset inCharset, Charset outCharset) {
            synchronized (ConsoleProviderImpl.class) {
                if (remoteOutput != null && remoteInput != null) {
                    return console = new RemoteConsole(remoteOutput, remoteInput);
                }
                return null;
            }
        }

        public static synchronized void setRemoteOutput(InputStream remoteOutput) {
            ConsoleProviderImpl.remoteOutput = remoteOutput;
        }

        public static synchronized void setRemoteInput(OutputStream remoteInput) {
            ConsoleProviderImpl.remoteInput =
                    new BufferedOutputStream(remoteInput);
        }

    }

    private static final class RemoteConsole implements JdkConsole {
        private final InputStream remoteOutput;
        private final OutputStream remoteInput;
        private PrintWriter writer;
        private Reader reader;

        public RemoteConsole(InputStream remoteOutput, OutputStream remoteInput) {
            this.remoteInput = new BufferedOutputStream(remoteInput);
            this.remoteOutput = new InputStream() {
                @Override
                public int read() throws IOException {
                    RemoteConsole.this.remoteInput.flush();
                    return remoteOutput.read();
                }
            };
        }

        private void sendChars(char[] data, int off, int len) throws IOException {
            ConsoleImpl.sendChars(remoteInput, data, off, len);
        }

        private int readChars(char[] data, int off, int len) throws IOException {
            sendInt(len);
            int actualLen = readInt();
            for (int i = 0; i < actualLen; i++) {
                data[off + i] = (char) ((remoteOutput.read() <<  8) |
                                        (remoteOutput.read() <<  0));
            }
            return actualLen;
        }

        private char[] readChars() throws IOException {
            int actualLen = readInt();
            if (actualLen == (-1)) {
                return null;
            }
            char[] result = new char[actualLen];
            for (int i = 0; i < actualLen; i++) {
                result[i] = (char) ((remoteOutput.read() <<  8) |
                                    (remoteOutput.read() <<  0));
            }
            return result;
        }

        private void sendInt(int data) throws IOException {
            ConsoleImpl.sendInt(remoteInput, data);
        }

        private int readInt() throws IOException {
            return (remoteOutput.read() << 24) |
                   (remoteOutput.read() << 16) |
                   (remoteOutput.read() <<  8) |
                   (remoteOutput.read() <<  0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized PrintWriter writer() {
            if (writer == null) {
                writer = new PrintWriter(new Writer() {
                    int i;
                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException {
                        sendAndReceive(() -> {
                            remoteInput.write(Task.WRITE_CHARS.ordinal());
                            sendChars(cbuf, off, len);
                            return null;
                        });
                    }

                    @Override
                    public void flush() throws IOException {
                        sendAndReceive(() -> {
                            remoteInput.write(Task.FLUSH_OUTPUT.ordinal());
                            remoteOutput.read();
                            return null;
                        });
                    }

                    @Override
                    public void close() throws IOException {
                    }
                });
            }
            return writer;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized Reader reader() {
            if (reader == null) {
                reader = new Reader() {
                    @Override
                    public int read(char[] cbuf, int off, int len) throws IOException {
                        if (len == 0) {
                            return 0;
                        }
                        return sendAndReceive(() -> {
                            remoteInput.write(Task.READ_CHARS.ordinal());
                            int r = readInt();
                            if (r == (-1)) {
                                return -1;
                            } else {
                                cbuf[off] = (char) r;
                                return 1;
                            }
                        });
                    }

                    @Override
                    public void close() throws IOException {
                    }
                };
            } return reader;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JdkConsole println(Object obj) {
            writer().println(obj);
            writer().flush();
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JdkConsole print(Object obj) {
            writer().print(obj);
            writer().flush();
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JdkConsole format(Locale locale, String format, Object... args) {
            writer().format(locale, format, args).flush();
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String readLine(Locale locale, String format, Object... args) {
            Objects.requireNonNull(format, "the format String must be non-null");

            String prompt = String.format(locale, format, args);
            char[] chars = prompt.toCharArray();

            try {
                return sendAndReceive(() -> {
                    remoteInput.write(Task.READ_LINE.ordinal());
                    sendChars(chars, 0, chars.length);
                    char[] line = readChars();
                    if (line == null) {
                        return null;
                    }
                    return new String(line);
                });
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String readLine() {
            return readLine(Locale.getDefault(Locale.Category.FORMAT), "");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public char[] readPassword(Locale locale, String format, Object... args) {
            Objects.requireNonNull(format, "the format String must be non-null");

            String prompt = String.format(locale, format, args);
            char[] chars = prompt.toCharArray();

            try {
                return sendAndReceive(() -> {
                    remoteInput.write(Task.READ_PASSWORD.ordinal());
                    sendChars(chars, 0, chars.length);
                    return readChars();
                });
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public char[] readPassword() {
            return readPassword(Locale.getDefault(Locale.Category.FORMAT), "");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void flush() {
            try {
                sendAndReceive(() -> {
                    remoteInput.write(Task.FLUSH_CONSOLE.ordinal());
                    remoteOutput.read();
                    return null;
                });
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        @Override
        public Charset charset() {
            try {
                return sendAndReceive(() -> {
                    remoteInput.write(Task.CHARSET.ordinal());
                    return Charset.forName(new String(readChars()));
                });
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        void ensureOutputAreWritten() {
            try {
                sendAndReceive(() -> {
                    remoteInput.write(Task.ENSURE_OUTPUTS_ARE_WRITTEN.ordinal());
                    return remoteOutput.read();
                });
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        private synchronized <R, E extends Exception> R sendAndReceive(SendAndReceive<R, E> task) throws IOException, E {
            return task.run();
        }

        interface SendAndReceive<R, E extends Exception> {
            R run() throws E;
        }

    }

    public static final class ConsoleOutputStream extends OutputStream {

        int[] buffer = new int[1024];
        int bp;
        final JShellConsole console;
        public final InputStream sinkInput;
        final OutputStream sinkOutput;

        public ConsoleOutputStream(JShellConsole console) {
            this.console = console;
            PipeInputStream sinkInput = new PipeInputStream();
            this.sinkInput = sinkInput;
            this.sinkOutput = sinkInput.createOutput();
        }

        @Override
        public synchronized void write(int b) throws IOException {
            if (bp + 1 >= buffer.length) {
                buffer = Arrays.copyOf(buffer, 2 * buffer.length);
            }

            // Can be negative because widening from byte in write(byte[], int, int).
            // java.io.OutputStream.write(int b) stipulates "The 24 high-order bits of b are ignored."
            buffer[bp++] = b & 0xff;

            switch (Task.values()[buffer[0]]) {
                case WRITE_CHARS -> {
                    char[] data = readCharsOrNull(1);
                    if (data != null) {
                        console.writer().write(data);
                        bp = 0;
                    }
                }
                case FLUSH_OUTPUT -> {
                    console.writer().flush();
                    sinkOutput.write(0);
                    bp = 0;
                }
                case READ_CHARS -> {
                    int c = console.reader().read();
                    sendInt(sinkOutput, c);
                    bp = 0;
                }
                case READ_LINE -> {
                    char[] data = readCharsOrNull(1);
                    if (data != null) {
                        String line = console.readLine(new String(data));
                        if (line == null) {
                            sendInt(sinkOutput, -1);
                        } else {
                            char[] chars = line.toCharArray();
                            sendChars(sinkOutput, chars, 0, chars.length);
                        }
                        bp = 0;
                    }
                }
                case READ_PASSWORD -> {
                    char[] data = readCharsOrNull(1);
                    if (data != null) {
                        char[] chars = console.readPassword(new String(data));
                        if (chars == null) {
                            sendInt(sinkOutput, -1);
                        } else {
                            sendChars(sinkOutput, chars, 0, chars.length);
                        }
                        bp = 0;
                    }
                }
                case FLUSH_CONSOLE -> {
                    console.flush();
                    sinkOutput.write(0);
                    bp = 0;
                }
                case CHARSET -> {
                    char[] name = console.charset().name().toCharArray();
                    sendChars(sinkOutput, name, 0, name.length);
                    bp = 0;
                }
                case ENSURE_OUTPUTS_ARE_WRITTEN -> {
                    sinkOutput.write(0);
                    bp = 0;
                }
            }
        }

        private int readInt(int pos) throws IOException {
            return (buffer[pos + 0] << 24) |
                   (buffer[pos + 1] << 16) |
                   (buffer[pos + 2] <<  8) |
                   (buffer[pos + 3] <<  0);
        }

        private char readChar(int pos) throws IOException {
            return (char) ((buffer[pos] << 8) |
                    (buffer[pos + 1] << 0));
        }

        private char[] readCharsOrNull(int pos) throws IOException {
            if (bp >= pos + 4) {
                int len = readInt(pos);
                if (bp >= pos + 4 + 2 * len) {
                    char[] result = new char[len];
                    for (int i = 0; i < len; i++) {
                        result[i] = readChar(pos + 4 + 2 * i);
                    }
                    return result;
                }
            }
            return null;
        }
    }

    private static void sendChars(OutputStream remoteInput, char[] data, int off, int len) throws IOException {
        sendInt(remoteInput, len);
        for (int i = 0; i < len; i++) {
            char c = data[off + i];

            remoteInput.write((c >> 8) & 0xFF);
            remoteInput.write((c >> 0) & 0xFF);
        }
    }

    private static void sendInt(OutputStream remoteInput, int data) throws IOException {
        remoteInput.write((data >> 24) & 0xFF);
        remoteInput.write((data >> 16) & 0xFF);
        remoteInput.write((data >>  8) & 0xFF);
        remoteInput.write((data >>  0) & 0xFF);
    }

    private enum Task {
        WRITE_CHARS,
        FLUSH_OUTPUT,
        READ_CHARS,
        READ_LINE,
        READ_PASSWORD,
        FLUSH_CONSOLE,
        CHARSET,
        ENSURE_OUTPUTS_ARE_WRITTEN,
        ;
    }
}
