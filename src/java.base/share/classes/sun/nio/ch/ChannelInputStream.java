/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Objects;
import jdk.internal.util.ArraysSupport;

/**
 * An InputStream that reads bytes from a channel.
 *
 * @author Mike McCloskey
 * @author Mark Reinhold
 */
class ChannelInputStream extends InputStream {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final ReadableByteChannel ch;
    private ByteBuffer bb;
    private byte[] bs;       // Invoker's previous array
    private byte[] b1;

    /**
     * Initialize a ChannelInputStream that reads from the given channel.
     */
    ChannelInputStream(ReadableByteChannel ch) {
        this.ch = ch;
    }

    /**
     * Reads a sequence of bytes from the channel into the given buffer.
     */
    private int read(ByteBuffer bb) throws IOException {
        if (ch instanceof SelectableChannel sc) {
            synchronized (sc.blockingLock()) {
                if (!sc.isBlocking())
                    throw new IllegalBlockingModeException();
                return ch.read(bb);
            }
        } else {
            return ch.read(bb);
        }
    }

    @Override
    public synchronized int read() throws IOException {
        if (b1 == null)
            b1 = new byte[1];
        int n = read(b1);
        if (n == 1)
            return b1[0] & 0xff;
        return -1;
    }

    @Override
    public synchronized int read(byte[] bs, int off, int len)
        throws IOException
    {
        Objects.checkFromIndexSize(off, len, bs.length);
        if (len == 0)
            return 0;

        ByteBuffer bb = ((this.bs == bs)
                         ? this.bb
                         : ByteBuffer.wrap(bs));
        bb.limit(Math.min(off + len, bb.capacity()));
        bb.position(off);
        this.bb = bb;
        this.bs = bs;
        return read(bb);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        if (!(ch instanceof SeekableByteChannel sbc))
            return super.readAllBytes();

        long length = sbc.size();
        long position = sbc.position();
        long size = length - position;

        if (length <= 0 || size <= 0)
            return super.readAllBytes();

        if (size > (long) Integer.MAX_VALUE) {
            String msg =
                String.format("Required array size too large: %d = %d - %d",
                    size, length, position);
            throw new OutOfMemoryError(msg);
        }

        int capacity = (int)size;
        byte[] buf = new byte[capacity];

        int nread = 0;
        int n;
        for (;;) {
            // read to EOF which may read more or less than initial size, e.g.,
            // file is truncated while we are reading
            while ((n = read(buf, nread, capacity - nread)) > 0)
                nread += n;

            // if last call to read() returned -1, we are done; otherwise,
            // try to read one more byte and if that fails we're done too
            if (n < 0 || (n = read()) < 0)
                break;

            // one more byte was read; need to allocate a larger buffer
            capacity = Math.max(ArraysSupport.newLength(capacity,
                                                        1,         // min growth
                                                        capacity), // pref growth
                                DEFAULT_BUFFER_SIZE);
            buf = Arrays.copyOf(buf, capacity);
            buf[nread++] = (byte)n;
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        if (len < 0)
            throw new IllegalArgumentException("len < 0");
        if (len == 0)
            return new byte[0];

        if (!(ch instanceof SeekableByteChannel sbc))
            return super.readNBytes(len);

        long length = sbc.size();
        long position = sbc.position();
        long size = length - position;

        if (length <= 0 || size <= 0)
            return super.readNBytes(len);

        int capacity = (int)Math.min(len, size);
        byte[] buf = new byte[capacity];

        int remaining = capacity;
        int nread = 0;
        int n;
        do {
            n = read(buf, nread, remaining);
            if (n > 0) {
                nread += n;
                remaining -= n;
            } else if (n == 0) {
                // Block until a byte is read or EOF is detected
                byte b = (byte)read();
                if (b == -1 )
                    break;
                buf[nread++] = b;
                remaining--;
            }
        } while (n >= 0 && remaining > 0);
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }

    @Override
    public int available() throws IOException {
        // special case where the channel is to a file
        if (ch instanceof SeekableByteChannel sbc) {
            long rem = Math.max(0, sbc.size() - sbc.position());
            return (rem > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)rem;
        }
        return 0;
    }

    @Override
    public synchronized long skip(long n) throws IOException {
        // special case where the channel is to a file
        if (ch instanceof SeekableByteChannel sbc) {
            long pos = sbc.position();
            long newPos;
            if (n > 0) {
                newPos = pos + n;
                long size = sbc.size();
                if (newPos < 0 || newPos > size) {
                    newPos = size;
                }
            } else {
                newPos = Long.max(pos + n, 0);
            }
            sbc.position(newPos);
            return newPos - pos;
        }
        return super.skip(n);
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");

        if (ch instanceof FileChannel fc) {
            // FileChannel -> SocketChannel
            if (out instanceof SocketOutputStream sos) {
                SocketChannelImpl sc = sos.channel();
                synchronized (sc.blockingLock()) {
                    if (!sc.isBlocking())
                        throw new IllegalBlockingModeException();
                    return transfer(fc, sc);
                }
            }

            // FileChannel -> WritableByteChannel
            if (out instanceof ChannelOutputStream cos) {
                WritableByteChannel wbc = cos.channel();

                if (wbc instanceof SelectableChannel sc) {
                    synchronized (sc.blockingLock()) {
                        if (!sc.isBlocking())
                            throw new IllegalBlockingModeException();
                        return transfer(fc, wbc);
                    }
                }

                return transfer(fc, wbc);
            }
        }

        return super.transferTo(out);
    }

    /**
     * Transfers all bytes from a channel's file to a target writeable byte channel.
     * If the writeable byte channel is a selectable channel then it must be in
     * blocking mode.
     */
    private static long transfer(FileChannel fc, WritableByteChannel target)
        throws IOException
    {
        long initialPos = fc.position();
        long pos = initialPos;
        try {
            while (pos < fc.size()) {
                pos += fc.transferTo(pos, Long.MAX_VALUE, target);
            }
        } finally {
            fc.position(pos);
        }
        return pos - initialPos;
    }

    @Override
    public void close() throws IOException {
        ch.close();
    }
}
