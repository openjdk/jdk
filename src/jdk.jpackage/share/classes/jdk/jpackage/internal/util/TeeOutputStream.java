/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

public final class TeeOutputStream extends OutputStream {

    public TeeOutputStream(Iterable<OutputStream> items) {
        items.forEach(Objects::requireNonNull);
        this.items = items;
    }

    @Override
    public void write(int b) throws IOException {
        for (final var item : items) {
            item.write(b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        for (final var item : items) {
            item.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        for (final var item : items) {
            item.write(b, off, len);
        }
    }

    @Override
    public void flush() throws IOException {
        forEach(Flushable::flush);
    }

    @Override
    public void close() throws IOException {
        forEach(Closeable::close);
    }

    private void forEach(ThrowingConsumer<OutputStream, IOException> c) throws IOException {
        IOException firstEx = null;
        for (final var item : items) {
            try {
                c.accept(item);
            } catch (IOException e) {
                if (firstEx == null) {
                    firstEx = e;
                }
            }
        }
        if (firstEx != null) {
            throw firstEx;
        }
    }

    private final Iterable<OutputStream> items;
}
