/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

package java.net.http;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Manages a ByteBuffer[] for writing frames into for output. The last
 * ByteBuffer in the list is always unflipped (able to receive more bytes for
 * sending) until getBufferArray() is called, which calls finish().
 *
 * This allows multiple frames to be written to the same BBG.
 *
 * Buffers added with addByteBuffer() must be already flipped.
 */
class ByteBufferGenerator {

    ByteBuffer currentBuffer;
    // source is assumed to always return the same sized buffer
    final BufferHandler pool;
    final ArrayList<ByteBuffer> buflist;
    final int bufsize;
    boolean finished;

    ByteBufferGenerator(BufferHandler pool) {
        this.buflist = new ArrayList<>();
        this.pool = pool;
        this.currentBuffer = pool.getBuffer();
        this.bufsize = currentBuffer.capacity();
    }

    private static final ByteBuffer[] EMPTY = new ByteBuffer[0];

    public ByteBuffer[] getBufferArray() {
        finish();
        return buflist.toArray(EMPTY);
    }

    public ArrayList<ByteBuffer> getBufferList() {
        finish();
        return buflist;
    }

    private synchronized void finish() {
        if (finished) {
            return;
        }
        finished = true;
        currentBuffer.flip();
        if (currentBuffer.hasRemaining()) {
            buflist.add(currentBuffer);
        } else {
            pool.returnBuffer(currentBuffer);
        }
    }

    // only used for SettingsFrame: offset is number of bytes to
    // ignore at start (we only want the payload of the settings frame)
    public byte[] asByteArray(int offset) {
        ByteBuffer[] bufs = getBufferArray();
        int size = 0;
        for (ByteBuffer buf : bufs) {
            size += buf.remaining();
        }
        byte[] bytes = new byte[size-offset];
        int pos = 0;
        for (ByteBuffer buf : bufs) {
            int rem = buf.remaining();
            int ignore = Math.min(rem, offset);
            buf.position(buf.position()+ignore);
            rem -= ignore;
            offset -= ignore;
            buf.get(bytes, pos, rem);
            pos += rem;
        }
        return bytes;
    }

    ByteBuffer getBuffer(long n) {
        if (currentBuffer.remaining() < n) {
            getNewBuffer();
            if (n > currentBuffer.capacity()) {
                throw new IllegalArgumentException("requested buffer too large");
            }
        }
        return currentBuffer;
    }

    void getNewBuffer() {
        currentBuffer.flip();
        if (currentBuffer.hasRemaining()) {
            buflist.add(currentBuffer);
        } else {
            pool.returnBuffer(currentBuffer);
        }
        currentBuffer = pool.getBuffer();
    }

    void addByteBuffer(ByteBuffer buf) {
        getNewBuffer();
        buflist.add(buf);
    }

    void addPadding(int length) {
        while (length > 0) {
            int n = Math.min(length, bufsize);
            ByteBuffer b = getBuffer(n);
            // TODO: currently zeroed?
            b.position(b.position() + n);
            length -= n;
        }
    }
}
