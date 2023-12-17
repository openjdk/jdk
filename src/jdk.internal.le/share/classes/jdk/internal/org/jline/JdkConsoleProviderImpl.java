/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.org.jline;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

import jdk.internal.io.JdkConsole;
import jdk.internal.io.JdkConsoleProvider;
import jdk.internal.org.jline.reader.EndOfFileException;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.LineReaderBuilder;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;

/**
 * JdkConsole/Provider implementations for jline
 */
public class JdkConsoleProviderImpl implements JdkConsoleProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public JdkConsole console(boolean isTTY, Charset charset) {
        try {
            Terminal terminal = TerminalBuilder.builder().encoding(charset)
                                               .exec(false).build();
            return new JdkConsoleImpl(terminal);
        } catch (IllegalStateException ise) {
            //cannot create a non-dumb, non-exec terminal,
            //use the standard Console:
            return null;
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * An implementation of JdkConsole, which act as a delegate for the
     * public Console class.
     */
    private static class JdkConsoleImpl implements JdkConsole {
        private final Terminal terminal;
        private volatile LineReader jline;

        @Override
        public PrintWriter writer() {
            return terminal.writer();
        }

        @Override
        public Reader reader() {
            return terminal.reader();
        }

        @Override
        public JdkConsole format(String fmt, Object ... args) {
            writer().format(fmt, args).flush();
            return this;
        }

        @Override
        public JdkConsole printf(String format, Object ... args) {
            return format(format, args);
        }

        @Override
        public String readLine(String fmt, Object ... args) {
            try {
                initJLineIfNeeded();
                return jline.readLine(fmt.formatted(args));
            } catch (EndOfFileException eofe) {
                return null;
            }
        }

        @Override
        public String readLine() {
            return readLine("");
        }

        @Override
        public char[] readPassword(String fmt, Object ... args) {
            try {
                initJLineIfNeeded();
                return jline.readLine(fmt.formatted(args), '\0').toCharArray();
            } catch (EndOfFileException eofe) {
                return null;
            } finally {
                jline.zeroOut();
            }
        }

        @Override
        public char[] readPassword() {
            return readPassword("");
        }

        @Override
        public void flush() {
            terminal.flush();
        }

        @Override
        public Charset charset() {
            return terminal.encoding();
        }

        public JdkConsoleImpl(Terminal terminal) {
            this.terminal = terminal;
        }

        private void initJLineIfNeeded() {
            LineReader jline = this.jline;
            if (jline == null) {
                synchronized (this) {
                    jline = this.jline;
                    if (jline == null) {
                        jline = LineReaderBuilder.builder().terminal(terminal).build();
                        this.jline = jline;
                    }
                }
            }
        }
    }
}
