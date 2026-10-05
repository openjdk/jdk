/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Supplier;

import jdk.internal.ValueBased;
import jdk.internal.io.JdkConsole;

/**
 * Console implementation for internal use. Custom Console delegate may be
 * provided with jdk.internal.io.JdkConsoleProvider.
 */
@ValueBased
final class ProxyingConsole extends Console {
    private final JdkConsole delegate;
    private final LazyConstant<Reader> reader =
        LazyConstant.of(new Supplier<>(){
            @Override
            public Reader get() {
                return new WrappingReader(delegate.reader());
            }
        });
    private final LazyConstant<PrintWriter> printWriter =
        LazyConstant.of(new Supplier<>() {
            @Override
            public PrintWriter get() {
                return new WrappingWriter(delegate.writer());
            }
        });

    ProxyingConsole(JdkConsole delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrintWriter writer() {
        return printWriter.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reader reader() {
        return reader.get();
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
        delegate.format(locale, format, args);
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
        delegate.format(locale, format, args);
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
        return delegate.readLine(locale, format, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String readLine() {
        return delegate.readLine();
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
        return delegate.readPassword(locale, format, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char[] readPassword() {
        return delegate.readPassword();
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

        WrappingReader(Reader r) {
            super(r);
            this.r = r;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return r.read(cbuf, off, len);
        }

        @Override
        public void close() {
            // no-op, per Console's spec
        }
    }

    private static final class WrappingWriter extends PrintWriter {
        public WrappingWriter(PrintWriter pw) {
            super(pw);
        }

        @Override
        public void close() {
            // no-op, per Console's spec
        }
    }
}
