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
        return new LazyDelegatingJdkConsoleImpl(charset);
    }

    private static class LazyDelegatingJdkConsoleImpl implements JdkConsole {
        private final Charset charset;
        private volatile boolean jlineInitialized;
        private volatile JdkConsole delegate;

        public LazyDelegatingJdkConsoleImpl(Charset charset) {
            this.charset = charset;
            this.delegate = new jdk.internal.io.JdkConsoleImpl(charset);
        }

        @Override
        public PrintWriter writer() {
            return getDelegate(true).writer();
        }

        @Override
        public Reader reader() {
            return getDelegate(true).reader();
        }

        @Override
        public JdkConsole println(Object obj) {
            JdkConsole delegate = getDelegate(false);

            delegate.println(obj);
            flushOldDelegateIfNeeded(delegate);

            return this;
        }

        @Override
        public JdkConsole print(Object obj) {
            JdkConsole delegate = getDelegate(false);

            delegate.print(obj);
            flushOldDelegateIfNeeded(delegate);

            return this;
        }

        @Override
        public String readln(String prompt) {
            return getDelegate(true).readln(prompt);
        }

        @Override
        public JdkConsole format(Locale locale, String format, Object... args) {
            JdkConsole delegate = getDelegate(false);

            delegate.format(locale, format, args);
            flushOldDelegateIfNeeded(delegate);

            return this;
        }

        @Override
        public String readLine(Locale locale, String format, Object... args) {
            return getDelegate(true).readLine(locale, format, args);
        }

        @Override
        public String readLine() {
            return getDelegate(true).readLine();
        }

        @Override
        public char[] readPassword(Locale locale, String format, Object... args) {
            return getDelegate(true).readPassword(locale, format, args);
        }

        @Override
        public char[] readPassword() {
            return getDelegate(true).readPassword();
        }

        @Override
        public void flush() {
            getDelegate(false).flush();
        }

        @Override
        public Charset charset() {
            return charset;
        }

        private void flushOldDelegateIfNeeded(JdkConsole oldDelegate) {
            if (oldDelegate != getDelegate(false)) {
                //if the delegate changed in the mean time, make sure the original
                //delegate is flushed:
                oldDelegate.flush();
            }
        }

        private JdkConsole getDelegate(boolean needsJLine) {
            if (!needsJLine || jlineInitialized) {
                return delegate;
            }

            return initializeJLineDelegate();
        }

        private synchronized JdkConsole initializeJLineDelegate() {
            JdkConsole newDelegate = delegate;

            if (jlineInitialized) {
                return newDelegate;
            }

            try {
                Terminal terminal = TerminalBuilder.builder().encoding(charset)
                                                   .exec(false)
                                                   .nativeSignals(false)
                                                   .systemOutput(SystemOutput.SysOut)
                                                   .build();
                newDelegate = new JdkConsoleImpl(terminal);
            } catch (IllegalStateException ise) {
                //cannot create a non-dumb, non-exec terminal,
                //use the standard Console:
            } catch (IOException ioe) {
                //something went wrong, keep the existing delegate
            }

            delegate = newDelegate;
            jlineInitialized = true;

            return newDelegate;
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
        public JdkConsole println(Object obj) {
            writer().println(obj);
            writer().flush();
            return this;
        }

        @Override
        public JdkConsole print(Object obj) {
            writer().print(obj);
            writer().flush();
            return this;
        }

        @Override
        public String readln(String prompt) {
            try {
                initJLineIfNeeded();
                return jline.readLine(prompt == null ? "null" : prompt.replace("%", "%%"));
            } catch (EndOfFileException eofe) {
                return null;
            }
        }

        @Override
        public JdkConsole format(Locale locale, String format, Object ... args) {
            writer().format(locale, format, args).flush();
            return this;
        }

        @Override
        public String readLine(Locale locale, String format, Object ... args) {
            try {
                initJLineIfNeeded();
                return jline.readLine(String.format(locale, format, args).replace("%", "%%"));
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
                initJLineIfNeeded();
                return jline.readLine(String.format(locale, format, args).replace("%", "%%"), '\0')
                            .toCharArray();
            } catch (EndOfFileException eofe) {
                return null;
            } finally {
                jline.zeroOut();
            }
        }

        @Override
        public char[] readPassword() {
            return readPassword(Locale.getDefault(Locale.Category.FORMAT), "");
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
