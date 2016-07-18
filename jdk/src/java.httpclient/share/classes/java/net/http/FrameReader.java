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
 * questions.
 */

package java.net.http;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents one frame. May be initialized with a leftover buffer from previous
 * frame. Call {@code haveFrame()} to determine if buffers contains at least one
 * frame. If false, the obtain another buffer and call {@code}input(ByteBuffer)}.
 * There may be additional bytes at end of the frame list.
 */
class FrameReader {

    final List<ByteBuffer> buffers;

    FrameReader() {
        buffers = new LinkedList<>();
    }

    FrameReader(FrameReader that) {
        this.buffers = that.buffers;
    }

    FrameReader(ByteBuffer remainder) {
        buffers = new LinkedList<>();
        if (remainder != null) {
            buffers.add(remainder);
        }
    }

    public synchronized void input(ByteBuffer buffer) {
        buffers.add(buffer);
    }

    public synchronized boolean haveFrame() {
        //buffers = Utils.superCompact(buffers, () -> ByteBuffer.allocate(Utils.BUFSIZE));
        int size = 0;
        for (ByteBuffer buffer : buffers) {
            size += buffer.remaining();
        }
        if (size < 3) {
            return false; // don't have length yet
        }
        // we at least have length field
        int length = 0;
        int j = 0;
        ByteBuffer b = buffers.get(j);
        b.mark();
        for (int i=0; i<3; i++) {
            while (!b.hasRemaining()) {
                b.reset();
                b = buffers.get(++j);
                b.mark();
            }
            length = (length << 8) + (b.get() & 0xff);
        }
        b.reset();
        return (size >= length + 9); // frame length
    }

    synchronized List<ByteBuffer> frame() {
        return buffers;
    }
}
