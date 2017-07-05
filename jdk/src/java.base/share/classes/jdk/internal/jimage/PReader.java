/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Supports reading a file from given positions (offsets) in the file.
 */

public abstract class PReader implements Closeable {
    private final FileChannel fc;

    protected PReader(FileChannel fc) {
        this.fc = fc;
    }

    /**
     * Returns the {@code FileChannel}.
     */
    final FileChannel channel() {
        return fc;
    }

    /**
     * Closes this {@code PReader} and the underlying file.
     */
    @Override
    public final void close() throws IOException {
        fc.close();
    }

    /**
     * Returns {@code true} if this {@code PReader} and the underlying file is
     * open.
     */
    public final boolean isOpen() {
        return fc.isOpen();
    }

    /**
     * Returns {@code len} bytes from a given position in the file. The bytes
     * are returned as a byte array.
     *
     * @throws IOException if an I/O error occurs
     */
    public abstract byte[] read(int len, long position) throws IOException;

    /**
     * Opens the given file, returning a {@code PReader} to read from the file.
     *
     * @implNote Returns a {@code PReader} that supports concurrent pread operations
     * if possible, otherwise a simple {@code PReader} that doesn't support
     * concurrent operations.
     */
    static PReader open(String file) throws IOException {
        Class<?> clazz;
        try {
            clazz = Class.forName("jdk.internal.jimage.concurrent.ConcurrentPReader");
        } catch (ClassNotFoundException e) {
            return new SimplePReader(file);
        }
        try {
            Constructor<?> ctor = clazz.getConstructor(String.class);
            return (PReader) ctor.newInstance(file);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException)
                throw (IOException) cause;
            if (cause instanceof Error)
                throw (Error) cause;
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            throw new Error(e);
        } catch (NoSuchMethodException | IllegalAccessException |
                InstantiationException e) {
            throw new InternalError(e);
        }
    }
}

/**
 * Simple PReader implementation based on {@code RandomAccessFile}.
 *
 * @implNote This class cannot use FileChannel read methods to do the
 * positional reads because FileChannel is interruptible.
 */
class SimplePReader extends PReader {
    private final RandomAccessFile raf;

    private SimplePReader(RandomAccessFile raf) throws IOException {
        super(raf.getChannel());
        this.raf = raf;
    }

    SimplePReader(String file) throws IOException {
        this(new RandomAccessFile(file, "r"));
    }

    @Override
    public byte[] read(int len, long position) throws IOException {
        synchronized (this) {
            byte[] bytes = new byte[len];
            raf.seek(position);
            int n = raf.read(bytes);
            if (n != len)
                throw new InternalError("short read, not handled yet");
            return bytes;
        }
    }
}
