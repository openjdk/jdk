/*
 * Copyright (c) 2000, 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.*;


class SourceChannelImpl
    extends Pipe.SourceChannel
    implements SelChImpl
{

    // Used to make native read and write calls
    private static NativeDispatcher nd;

    // The file descriptor associated with this channel
    FileDescriptor fd;

    // fd value needed for dev/poll. This value will remain valid
    // even after the value in the file descriptor object has been set to -1
    int fdVal;

    // ID of native thread doing read, for signalling
    private volatile long thread = 0;

    // Lock held by current reading thread
    private final Object lock = new Object();

    // Lock held by any thread that modifies the state fields declared below
    // DO NOT invoke a blocking I/O operation while holding this lock!
    private final Object stateLock = new Object();

    // -- The following fields are protected by stateLock

    // Channel state
    private static final int ST_UNINITIALIZED = -1;
    private static final int ST_INUSE = 0;
    private static final int ST_KILLED = 1;
    private volatile int state = ST_UNINITIALIZED;

    // -- End of fields protected by stateLock


    public FileDescriptor getFD() {
        return fd;
    }

    public int getFDVal() {
        return fdVal;
    }

    SourceChannelImpl(SelectorProvider sp, FileDescriptor fd) {
        super(sp);
        this.fd = fd;
        this.fdVal = IOUtil.fdVal(fd);
        this.state = ST_INUSE;
    }

    protected void implCloseSelectableChannel() throws IOException {
        synchronized (stateLock) {
            nd.preClose(fd);
            long th = thread;
            if (th != 0)
                NativeThread.signal(th);
            if (!isRegistered())
                kill();
        }
    }

    public void kill() throws IOException {
        synchronized (stateLock) {
            if (state == ST_KILLED)
                return;
            if (state == ST_UNINITIALIZED) {
                state = ST_KILLED;
                return;
            }
            assert !isOpen() && !isRegistered();
            nd.close(fd);
            state = ST_KILLED;
        }
    }

    protected void implConfigureBlocking(boolean block) throws IOException {
        IOUtil.configureBlocking(fd, block);
    }

    public boolean translateReadyOps(int ops, int initialOps,
                                     SelectionKeyImpl sk) {
        int intOps = sk.nioInterestOps(); // Do this just once, it synchronizes
        int oldOps = sk.nioReadyOps();
        int newOps = initialOps;

        if ((ops & PollArrayWrapper.POLLNVAL) != 0)
            throw new Error("POLLNVAL detected");

        if ((ops & (PollArrayWrapper.POLLERR
                    | PollArrayWrapper.POLLHUP)) != 0) {
            newOps = intOps;
            sk.nioReadyOps(newOps);
            return (newOps & ~oldOps) != 0;
        }

        if (((ops & PollArrayWrapper.POLLIN) != 0) &&
            ((intOps & SelectionKey.OP_READ) != 0))
            newOps |= SelectionKey.OP_READ;

        sk.nioReadyOps(newOps);
        return (newOps & ~oldOps) != 0;
    }

    public boolean translateAndUpdateReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, sk.nioReadyOps(), sk);
    }

    public boolean translateAndSetReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, 0, sk);
    }

    public void translateAndSetInterestOps(int ops, SelectionKeyImpl sk) {
        if (ops == SelectionKey.OP_READ)
            ops = PollArrayWrapper.POLLIN;
        sk.selector.putEventOps(sk, ops);
    }

    private void ensureOpen() throws IOException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        synchronized (lock) {
            int n = 0;
            try {
                begin();
                if (!isOpen())
                    return 0;
                thread = NativeThread.current();
                do {
                    n = IOUtil.read(fd, dst, -1, nd, lock);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                thread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException
    {
        if ((offset < 0) || (length < 0) || (offset > dsts.length - length))
           throw new IndexOutOfBoundsException();
        return read(Util.subsequence(dsts, offset, length));
    }

    public long read(ByteBuffer[] dsts) throws IOException {
        if (dsts == null)
            throw new NullPointerException();
        ensureOpen();
        synchronized (lock) {
            long n = 0;
            try {
                begin();
                if (!isOpen())
                    return 0;
                thread = NativeThread.current();
                do {
                    n = IOUtil.read(fd, dsts, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                thread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    static {
        Util.load();
        nd = new FileDispatcherImpl();
    }

}
