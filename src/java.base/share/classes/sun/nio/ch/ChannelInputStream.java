/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.Objects;

/**
 * This class is defined here rather than in java.nio.channels.Channels
 * so that code can be shared with SocketAdaptor.
 *
 * @author Mike McCloskey
 * @author Mark Reinhold
 * @since 1.4
 */

public class ChannelInputStream
    extends InputStream
{

    public static int read(ReadableByteChannel ch, ByteBuffer bb,
                           boolean block)
        throws IOException
    {
        if (ch instanceof SelectableChannel) {
            SelectableChannel sc = (SelectableChannel)ch;
            synchronized (sc.blockingLock()) {
                boolean bm = sc.isBlocking();
                if (!bm)
                    throw new IllegalBlockingModeException();
                if (bm != block)
                    sc.configureBlocking(block);
                int n = ch.read(bb);
                if (bm != block)
                    sc.configureBlocking(bm);
                return n;
            }
        } else {
            return ch.read(bb);
        }
    }

    protected final ReadableByteChannel ch;
    private ByteBuffer bb = null;
    private byte[] bs = null;           // Invoker's previous array
    private byte[] b1 = null;

    public ChannelInputStream(ReadableByteChannel ch) {
        this.ch = ch;
    }

    public synchronized int read() throws IOException {
        if (b1 == null)
            b1 = new byte[1];
        int n = this.read(b1);
        if (n == 1)
            return b1[0] & 0xff;
        return -1;
    }

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

    protected int read(ByteBuffer bb)
        throws IOException
    {
        return ChannelInputStream.read(ch, bb, true);
    }

    public int available() throws IOException {
        // special case where the channel is to a file
        if (ch instanceof SeekableByteChannel) {
            SeekableByteChannel sbc = (SeekableByteChannel)ch;
            long rem = Math.max(0, sbc.size() - sbc.position());
            return (rem > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)rem;
        }
        return 0;
    }

    public synchronized long skip(long n) throws IOException {
        // special case where the channel is to a file
        if (ch instanceof SeekableByteChannel) {
            SeekableByteChannel sbc = (SeekableByteChannel)ch;
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

    public void close() throws IOException {
        ch.close();
    }

    private static final int TRANSFER_SIZE = 8192;

    @Override
    public long transferTo(final OutputStream out) throws IOException {
        if (out instanceof Channels.ChannelOutputStream) {
            final var oc = ((Channels.ChannelOutputStream) out).channel();

            if (ch instanceof FileChannel) {
                final var fc = (FileChannel) ch;
                final var pos = fc.position();
                final var size = fc.size();
                var i = 0L;
                for (final var n = size - pos; i < n;
                  i += fc.transferTo(pos + i, Long.MAX_VALUE, oc));
                fc.position(size);
                return i;
            }

            if (oc instanceof FileChannel) {
                final var fc = (FileChannel) oc;
                final var fcpos = fc.position();

                if (ch instanceof SeekableByteChannel) {
                    final var pos = ((SeekableByteChannel) ch).position();
                    final var size = ((SeekableByteChannel) ch).size();
                    var i = 0L;
                    for (final var n = size - pos; i < n;
                      i += fc.transferFrom(ch, fcpos + i, Long.MAX_VALUE));
                    fc.position(fcpos + i);
                    return i;
                }

                final var bb = ByteBuffer.allocateDirect(TRANSFER_SIZE);
                var i = 0L;
                int r;
                do {
                    i += fc.transferFrom(ch, fcpos + i, Long.MAX_VALUE);
                    r = ch.read(bb); // detect end-of-stream
                    if (r > -1) {
                        bb.flip();
                        while (bb.hasRemaining())
                          oc.write(bb);
                        bb.clear();
                        i += r;
                    }
                } while (r > -1);
                fc.position(fcpos + i);
                return i;
            }

            final var bb = ByteBuffer.allocateDirect(TRANSFER_SIZE);
            var i = 0L;
            for (var r = ch.read(bb); r > -1; r = ch.read(bb)) {
                bb.flip();
                while (bb.hasRemaining())
                  oc.write(bb);
                bb.clear();
                i += r;
            }
            return i;
        }

        return super.transferTo(out);
    }

}
