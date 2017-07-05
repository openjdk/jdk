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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

/**
 * Takes a List<ByteBuffer> which is assumed to contain at least one HTTP/2
 * frame and allows it to be processed supplying bytes, ints, shorts, byte[] etc.
 * from the list. As each ByteBuffer is consumed it is removed from the List<>.
 *
 * NOTE. shorts and bytes returned are UNSIGNED ints
 *
 * When finished processing the frame, the List may be empty or may contain
 * partially read or unread ByteBuffers. A new ByteBufferConsumer can be
 * created with the List<>
 */
class ByteBufferConsumer {

    ByteBuffer currentBuffer;

    final List<ByteBuffer> buffers;
    final ListIterator<ByteBuffer> iterator;
    final Supplier<ByteBuffer> newBufferSupplier;

    ByteBufferConsumer(List<ByteBuffer> buffers,
                       Supplier<ByteBuffer> newBufferSupplier) {
        this.buffers = buffers;
        this.newBufferSupplier = newBufferSupplier;
        this.iterator = buffers.listIterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("Empty buffer list");
        }
        currentBuffer = iterator.next();
    }

    private void dump() {
        int l = 0;
        System.err.printf("ByteBufferConsumer:\n");
        for (ByteBuffer buf : buffers) {
            System.err.printf("\t%s\n", buf.toString());
            l+= buf.remaining();
        }
        System.err.printf("BBC contains %d bytes\n", l);
    }

    private synchronized ByteBuffer getBuffer(boolean exception) throws IOException {
        while (currentBuffer == null || !currentBuffer.hasRemaining()) {
            if (currentBuffer != null) {
                iterator.remove();
            }
            if (!iterator.hasNext()) {
                currentBuffer = null;
                if (exception) {
                    throw new IOException ("Connection closed unexpectedly");
                }
                return null;
            }
            currentBuffer = iterator.next();
        }
        return currentBuffer;
    }

    // call this to check if the data has all been consumed

    public boolean consumed() {
        try {
            return getBuffer(false) == null;
        } catch (IOException e) {
            /* CAN'T HAPPEN */
            throw new InternalError();
        }
    }

    public int getByte() throws IOException {
        // TODO: what to do if connection is closed. Throw NPE?
        ByteBuffer buf = getBuffer(true);
        return buf.get() & 0xff;
    }

    public byte[] getBytes(int n) throws IOException {
        return getBytes(n, null);
    }

    public byte[] getBytes(int n, byte[] buf) throws IOException {
        if (buf == null) {
            buf = new byte[n];
        } else if (buf.length < n) {
            throw new IllegalArgumentException("getBytes: buffer too small");
        }
        int offset = 0;
        while (n > 0) {
            ByteBuffer b = getBuffer(true);
            int length = Math.min(n, b.remaining());
            b.get(buf, offset, length);
            offset += length;
            n -= length;
        }
        return buf;
    }

    public int getShort() throws IOException {
        ByteBuffer buf = getBuffer(true);
        int rem = buf.remaining();
        if (rem >= 2) {
            return buf.getShort() & 0xffff;
        }
        // Slow path. Not common
        int val = 0;
        val = (val << 8) + getByte();
        val = (val << 8) + getByte();
        return val;
    }

    public int getInt() throws IOException {
        ByteBuffer buf = getBuffer(true);
        int rem = buf.remaining();
        if (rem >= 4) {
            return buf.getInt();
        }
        // Slow path. Not common
        int val = 0;
        for (int nbytes = 0; nbytes < 4; nbytes++) {
            val = (val << 8) + getByte();
        }
        return val;
    }

    private static final ByteBuffer[] EMPTY = new ByteBuffer[0];

    /**
     * Extracts whatever number of ByteBuffers from list to get required number
     * of bytes. Any remaining buffers are 'tidied up' so reading can continue.
     */
    public ByteBuffer[] getBuffers(int bytecount) throws IOException {
        LinkedList<ByteBuffer> l = new LinkedList<>();
        while (bytecount > 0) {
            ByteBuffer buffer = getBuffer(true);
            int remaining = buffer.remaining();
            if (remaining > bytecount) {
                int difference = remaining - bytecount;
                // split
                ByteBuffer newb = newBufferSupplier.get();
                newb.clear();
                int limit = buffer.limit();
                buffer.limit(limit - difference);
                newb.put(buffer);
                newb.flip();
                buffer.limit(limit);
                l.add(newb);
                bytecount = 0;
            } else {
                l.add(buffer);
                currentBuffer = null;
                iterator.remove();
                bytecount -= remaining;
            }
        }
        return l.toArray(EMPTY);
    }
}
