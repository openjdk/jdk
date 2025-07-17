/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

final class ChunkInputStream extends InputStream {
    private final Iterator<RepositoryChunk> chunks;
    private long unstreamedSize = 0;
    private RepositoryChunk currentChunk;
    private InputStream stream;

    ChunkInputStream(List<RepositoryChunk> chunks) throws IOException {
        List<RepositoryChunk> l = new ArrayList<>(chunks.size());
        for (RepositoryChunk c : chunks) {
            c.use(); // keep alive while we're reading.
            l.add(c);
            unstreamedSize += c.getSize();
        }

        this.chunks = l.iterator();
        nextStream();
    }

    @Override
    public int available() throws IOException {
        long total = unstreamedSize;
        if (stream != null) {
            total += stream.available();
        }
        return total <= Integer.MAX_VALUE ? (int) total : Integer.MAX_VALUE;
    }

    private boolean nextStream() throws IOException {
        if (!nextChunk()) {
            return false;
        }

        stream = new BufferedInputStream(Files.newInputStream(currentChunk.getFile()));
        unstreamedSize -= currentChunk.getSize();
        return true;
    }

    private boolean nextChunk() {
        if (!chunks.hasNext()) {
            return false;
        }
        currentChunk = chunks.next();
        return true;
    }

    @Override
    public int read() throws IOException {
        while (true) {
            if (stream != null) {
                int r = stream.read();
                if (r != -1) {
                    return r;
                }
                closeStream();
            }
            if (!nextStream()) {
                return -1;
            }
        }
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, buf.length);
        if (len == 0) {
            return 0;
        }

        int totalRead = 0;
        while (len > 0) {
            if (stream == null) {
                closeChunk();
                if (!nextStream()) {
                    return totalRead > 0 ? totalRead : -1;
                }
            }
            int read = stream.read(buf, off, len);
            if (read > -1) {
                totalRead += read;
                len -= read;
                if (len == 0) {
                    return totalRead;
                }
                off += read;
            } else {
                closeStream();
            }
        }
        return totalRead;
    }

    private void closeStream() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
        closeChunk();
    }

    private void closeChunk() {
        if (currentChunk != null) {
            currentChunk.release();
            currentChunk = null;
        }
    }

    @Override
    public void close() throws IOException {
        closeStream();
        while (currentChunk != null) {
            closeChunk();
            if (!nextChunk()) {
                return;
            }
        }
    }
}
