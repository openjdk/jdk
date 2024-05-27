/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.Charset;
import java.util.Locale;

import jdk.internal.io.JdkConsole;
import jdk.internal.io.JdkConsoleProvider;
import jdk.internal.org.jline.reader.EndOfFileException;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.LineReaderBuilder;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;
import jdk.internal.org.jline.terminal.TerminalBuilder.SystemOutput;

/**
 * JdkConsole/Provider implementations for jline
 */
public class JdkConsoleProviderImpl implements JdkConsoleProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public JdkConsole console(boolean isTTY, Charset charset) {
        return new JdkConsoleImpl(charset, new jdk.internal.io.JdkConsoleImpl(charset));
    }

    /**
     * An implementation of JdkConsole, which act as a delegate for the
     * public Console class.
     */
    private static class JdkConsoleImpl implements JdkConsole {
        private final Charset charset;
        private final jdk.internal.io.JdkConsoleImpl delegate;
        private volatile boolean terminalInitialized;
        private volatile Terminal terminalX;
        private volatile boolean jlineInitialized;
        private volatile LineReader jlineX;

        public JdkConsoleImpl(Charset charset, jdk.internal.io.JdkConsoleImpl delegate) {
            this.charset = charset;
            this.delegate = delegate;
        }

        @Override
        public PrintWriter writer() {
            Terminal terminal = getTerminalOrNull(true);
            if (terminal != null) {
                return terminal.writer();
            }
            return delegate.writer();
        }

        @Override
        public Reader reader() {
            Terminal terminal = getTerminalOrNull(true);
            if (terminal != null) {
                return terminal.reader();
            } else {
                return delegate.reader();
            }
        }

        @Override
        public JdkConsole println(Object obj) {
            Terminal terminal = getTerminalOrNull(false);
            if (terminal != null) {
                PrintWriter writer = terminal.writer();
                writer.println(obj);
                writer.flush();
            } else {
                delegate.println(obj);
            }
            return this;
        }

        @Override
        public JdkConsole print(Object obj) {
            Terminal terminal = getTerminalOrNull(false);
            if (terminal != null) {
                PrintWriter writer = terminal.writer();
                writer.print(obj);
                writer.flush();
            } else {
                delegate.print(obj);
            }
            return this;
        }

        @Override
        public String readln(String prompt) {
            try {
                LineReader jline = getInitializedJLineReader();
                if (jline != null) {
                    return jline.readLine(prompt == null ? "null" : prompt.replace("%", "%%"));
                } else {
                    return delegate.readln(prompt);
                }
            } catch (EndOfFileException eofe) {
                return null;
            }
        }

        @Override
        public JdkConsole format(Locale locale, String format, Object ... args) {
            Terminal terminal = getTerminalOrNull(false);
            if (terminal != null) {
                PrintWriter writer = terminal.writer();
                writer.format(locale, format, args).flush();
            } else {
                delegate.format(locale, format, args);
            }
            return this;
        }

        @Override
        public String readLine(Locale locale, String format, Object ... args) {
            try {
                LineReader jline = getInitializedJLineReader();
                if (jline != null) {
                    return jline.readLine(String.format(locale, format, args).replace("%", "%%"));
                } else {
                    return delegate.readLine(locale, format, args);
                }
            } catch (EndOfFileException eofe) {
                return null;
            }
        }

        @Override
        public String readLine() {
            return readLine(Locale.getDefault(Locale.Category.FORMAT), "");
        }

        @Override
        public char[] readPassword(Locale locale, String format, Object ... args) {
            try {
                LineReader jline = getInitializedJLineReader();
                if (jline != null) {
                    try {
                        return jline.readLine(String.format(locale, format, args).replace("%", "%%"), '\0')
                                    .toCharArray();
                    } finally {
                        jline.zeroOut();
                    }
                } else {
                    return delegate.readPassword(locale, format, args);
                }
            } catch (EndOfFileException eofe) {
                return null;
            }
        }

        @Override
        public char[] readPassword() {
            return readPassword(Locale.getDefault(Locale.Category.FORMAT), "");
        }

        @Override
        public void flush() {
            Terminal terminal = getTerminalOrNull(false);
            if (terminal != null) {
                terminal.flush();
            } else {
                delegate.flush();
            }
        }

        @Override
        public Charset charset() {
            return charset;
        }

        private Terminal getTerminalOrNull(boolean initialize) {
            if (terminalInitialized) {
                return this.terminalX;
            }
            if (!initialize) {
                return null;
            }
            synchronized (this) {
                if (!terminalInitialized) {
                    delegate.flush();

                    try {
                        Terminal terminal = TerminalBuilder.builder()
                                                           .encoding(charset)
                                                           .exec(false)
                                                           .systemOutput(SystemOutput.SysOut)
                                                           .build();
                        this.terminalX = terminal;
                        this.terminalInitialized = true;

                        return terminal;
                    } catch (IllegalStateException | IOException ioe) {
                        this.terminalInitialized = true;
                    }
                }
                return this.terminalX;
            }
        }

        private LineReader getInitializedJLineReader() {
            if (jlineInitialized) {
                return this.jlineX;
            }
            synchronized (this) {
                if (!jlineInitialized) {
                    Terminal terminal = getTerminalOrNull(true);
                    if (terminal != null) {
                        LineReader jline = LineReaderBuilder.builder().terminal(terminal).build();
                        this.jlineX = jline;
                        this.jlineInitialized = true;
                        return jline;
                    } else {
                        return null;
                    }
                } else {
                    return this.jlineX;
                }
            }
        }
    }
}
