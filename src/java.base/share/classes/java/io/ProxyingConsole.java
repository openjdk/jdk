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

package java.io;

import java.nio.charset.Charset;
import java.util.Locale;

import jdk.internal.io.JdkConsole;

/**
 * Console implementation for internal use. Custom Console delegate may be
 * provided with jdk.internal.io.JdkConsoleProvider.
 */
final class ProxyingConsole extends Console {
    private final JdkConsole delegate;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private volatile Reader reader;
    private volatile PrintWriter printWriter;

    ProxyingConsole(JdkConsole delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrintWriter writer() {
        PrintWriter printWriter = this.printWriter;
        if (printWriter == null) {
            synchronized (this) {
                printWriter = this.printWriter;
                if (printWriter == null) {
                    printWriter = new WrappingWriter(delegate.writer(), writeLock);
                    this.printWriter = printWriter;
                }
            }
        }
        return printWriter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reader reader() {
        Reader reader = this.reader;
        if (reader == null) {
            synchronized (this) {
                reader = this.reader;
                if (reader == null) {
                    reader = new WrappingReader(delegate.reader(), readLock);
                    this.reader = reader;
                }
            }
        }
        return reader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Console println(Object obj) {
        synchronized (writeLock) {
            delegate.println(obj);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Console print(Object obj) {
        synchronized (writeLock) {
            delegate.print(obj);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOError {@inheritDoc}
     */
    @Override
    public String readln(String prompt) {
        synchronized (writeLock) {
            synchronized (readLock) {
                return delegate.readln(prompt);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Console format(String format, Object ... args) {
        return format(Locale.getDefault(Locale.Category.FORMAT), format, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Console format(Locale locale, String format, Object ... args) {
        synchronized (writeLock) {
            delegate.format(locale, format, args);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Console printf(String format, Object ... args) {
        return printf(Locale.getDefault(Locale.Category.FORMAT), format, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Console printf(Locale locale, String format, Object ... args) {
        synchronized (writeLock) {
            delegate.format(locale, format, args);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String readLine(String format, Object ... args) {
        return readLine(Locale.getDefault(Locale.Category.FORMAT), format, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String readLine(Locale locale, String format, Object ... args) {
        synchronized (writeLock) {
            synchronized (readLock) {
                return delegate.readLine(locale, format, args);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String readLine() {
        synchronized (readLock) {
            return delegate.readLine();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char[] readPassword(String format, Object ... args) {
        return readPassword(Locale.getDefault(Locale.Category.FORMAT), format, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char[] readPassword(Locale locale, String format, Object ... args) {
        synchronized (writeLock) {
            synchronized (readLock) {
                return delegate.readPassword(locale, format, args);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char[] readPassword() {
        synchronized (readLock) {
            return delegate.readPassword();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() {
        delegate.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Charset charset() {
        return delegate.charset();
    }

    private static final class WrappingReader extends Reader {
        private final Reader r;
        private final Object lock;

        WrappingReader(Reader r, Object lock) {
            super(lock);
            this.r = r;
            this.lock = lock;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            synchronized (lock) {
                return r.read(cbuf, off, len);
            }
        }

        @Override
        public void close() {
            // no-op, per Console's spec
        }
    }

    private static final class WrappingWriter extends PrintWriter {
        private final PrintWriter pw;
        private final Object lock;

        public WrappingWriter(PrintWriter pw, Object lock) {
            super(pw, lock);
            this.pw = pw;
            this.lock = lock;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            synchronized (lock) {
                pw.write(cbuf, off, len);
            }
        }

        @Override
        public void flush() {
            pw.flush();
        }

        @Override
        public void close() {
            // no-op, per Console's spec
        }
    }
}
