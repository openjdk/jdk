/*
 * Copyright 2001-2002 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.ch;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;


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
        if ((off < 0) || (off > bs.length) || (len < 0) ||
            ((off + len) > bs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0)
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

    public void close() throws IOException {
        ch.close();
    }

}
