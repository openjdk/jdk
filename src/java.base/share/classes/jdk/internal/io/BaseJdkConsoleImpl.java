/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.io;

import java.io.IOError;
import java.io.IOException;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Formatter;
import java.util.Locale;
import java.util.Objects;

import sun.nio.cs.StreamDecoder;
import sun.nio.cs.StreamEncoder;

/**
 * Base for JDK's JdkConsole implementations.
 */
public abstract class BaseJdkConsoleImpl implements JdkConsole {
    @Override
    public PrintWriter writer() {
        return pw;
    }

    @Override
    public Reader reader() {
        return reader;
    }

    @Override
    public JdkConsole println(Object obj) {
        pw.println(obj);
        // automatic flushing covers println
        return this;
    }

    @Override
    public JdkConsole print(Object obj) {
        pw.print(obj);
        pw.flush(); // automatic flushing does not cover print
        return this;
    }

    @Override
    public String readln(String prompt) {
        String line = null;
        synchronized (writeLock) {
            synchronized(readLock) {
                pw.print(prompt);
                pw.flush(); // automatic flushing does not cover print
                try {
                    char[] ca = readline(false);
                    if (ca != null)
                        line = new String(ca);
                } catch (IOException x) {
                    throw new IOError(x);
                }
            }
        }
        return line;
    }

    @Override
    public String readln() {
        String line = null;
        synchronized(readLock) {
            try {
                char[] ca = readline(false);
                if (ca != null)
                    line = new String(ca);
            } catch (IOException x) {
                throw new IOError(x);
            }
        }
        return line;
    }

    @Override
    public JdkConsole format(Locale locale, String format, Object ... args) {
        formatter.format(locale, format, args).flush();
        return this;
    }

    @Override
    public String readLine(Locale locale, String format, Object ... args) {
        String line = null;
        synchronized (writeLock) {
            synchronized(readLock) {
                if (!format.isEmpty())
                    pw.format(locale, format, args);
                try {
                    char[] ca = readline(false);
                    if (ca != null)
                        line = new String(ca);
                } catch (IOException x) {
                    throw new IOError(x);
                }
            }
        }
        return line;
    }

    @Override
    public String readLine() {
        return readLine(Locale.getDefault(Locale.Category.FORMAT), "");
    }

    @Override
    public char[] readPassword(Locale locale, String format, Object ... args) {
        char[] passwd = null;
        synchronized (writeLock) {
            synchronized(readLock) {
                try {
                    if (!format.isEmpty())
                        pw.format(locale, format, args);
                    passwd = readline(true);
                } catch (IOException x) {
                    throw new IOError(x);
                }
                pw.println();
            }
        }
        return passwd;
    }

    @Override
    public char[] readPassword() {
        return readPassword(Locale.getDefault(Locale.Category.FORMAT), "");
    }

    @Override
    public void flush() {
        pw.flush();
    }

    @Override
    public Charset charset() {
        return charset;
    }

    protected Reader wrapReader(Reader baseReader) {
        return baseReader;
    }

    protected final Charset charset;
    protected final Object readLock;
    protected final Object writeLock;
    protected final Reader reader;
    protected final Writer out;
    protected final PrintWriter pw;
    protected final Formatter formatter;

    protected abstract char[] readline(boolean password) throws IOException;

    @SuppressWarnings("this-escape")
    public BaseJdkConsoleImpl(Charset charset) {
        Objects.requireNonNull(charset);
        this.charset = charset;
        readLock = new Object();
        writeLock = new Object();
        out = StreamEncoder.forOutputStreamWriter(
                new FileOutputStream(FileDescriptor.out),
                writeLock,
                charset);
        pw = new PrintWriter(out, true) {
            public void close() {
            }
        };
        formatter = new Formatter(out);
        StreamDecoder plainReader = StreamDecoder.forInputStreamReader(
                new FileInputStream(FileDescriptor.in),
                readLock,
                charset);
        reader = wrapReader(plainReader);
    }
}
