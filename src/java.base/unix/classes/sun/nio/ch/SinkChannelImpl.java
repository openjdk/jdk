/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

class SinkChannelImpl
    extends Pipe.SinkChannel
    implements SelChImpl
{
    // Used to make native read and write calls
    private static final NativeDispatcher nd = new FileDispatcherImpl();

    // The file descriptor associated with this channel
    private final FileDescriptor fd;
    private final int fdVal;

    // Lock held by current writing thread
    private final ReentrantLock writeLock = new ReentrantLock();

    // Lock held by any thread that modifies the state fields declared below
    // DO NOT invoke a blocking I/O operation while holding this lock!
    private final Object stateLock = new Object();

    // -- The following fields are protected by stateLock

    // Channel state
    private static final int ST_INUSE = 0;
    private static final int ST_CLOSING = 1;
    private static final int ST_KILLPENDING = 2;
    private static final int ST_KILLED = 3;
    private int state;

    // ID of native thread doing write, for signalling
    private long thread;

    // -- End of fields protected by stateLock


    public FileDescriptor getFD() {
        return fd;
    }

    public int getFDVal() {
        return fdVal;
    }

    SinkChannelImpl(SelectorProvider sp, FileDescriptor fd) {
        super(sp);
        this.fd = fd;
        this.fdVal = IOUtil.fdVal(fd);
    }

    /**
     * Invoked by implCloseChannel to close the channel.
     */
    @Override
    protected void implCloseSelectableChannel() throws IOException {
        assert !isOpen();

        boolean interrupted = false;
        boolean blocking;

        // set state to ST_CLOSING
        synchronized (stateLock) {
            assert state < ST_CLOSING;
            state = ST_CLOSING;
            blocking = isBlocking();
        }

        // wait for any outstanding write to complete
        if (blocking) {
            synchronized (stateLock) {
                assert state == ST_CLOSING;
                long th = thread;
                if (th != 0) {
                    nd.preClose(fd);
                    NativeThread.signal(th);

                    // wait for write operation to end
                    while (thread != 0) {
                        try {
                            stateLock.wait();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                }
            }
        } else {
            // non-blocking mode: wait for write to complete
            writeLock.lock();
            writeLock.unlock();
        }

        // set state to ST_KILLPENDING
        synchronized (stateLock) {
            assert state == ST_CLOSING;
            state = ST_KILLPENDING;
        }

        // close socket if not registered with Selector
        if (!isRegistered())
            kill();

        // restore interrupt status
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    @Override
    public void kill() throws IOException {
        synchronized (stateLock) {
            assert thread == 0;
            if (state == ST_KILLPENDING) {
                state = ST_KILLED;
                nd.close(fd);
            }
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        writeLock.lock();
        try {
            synchronized (stateLock) {
                IOUtil.configureBlocking(fd, block);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public boolean translateReadyOps(int ops, int initialOps, SelectionKeyImpl ski) {
        int intOps = ski.nioInterestOps();
        int oldOps = ski.nioReadyOps();
        int newOps = initialOps;

        if ((ops & Net.POLLNVAL) != 0)
            throw new Error("POLLNVAL detected");

        if ((ops & (Net.POLLERR | Net.POLLHUP)) != 0) {
            newOps = intOps;
            ski.nioReadyOps(newOps);
            return (newOps & ~oldOps) != 0;
        }

        if (((ops & Net.POLLOUT) != 0) &&
            ((intOps & SelectionKey.OP_WRITE) != 0))
            newOps |= SelectionKey.OP_WRITE;

        ski.nioReadyOps(newOps);
        return (newOps & ~oldOps) != 0;
    }

    public boolean translateAndUpdateReadyOps(int ops, SelectionKeyImpl ski) {
        return translateReadyOps(ops, ski.nioReadyOps(), ski);
    }

    public boolean translateAndSetReadyOps(int ops, SelectionKeyImpl ski) {
        return translateReadyOps(ops, 0, ski);
    }

    public int translateInterestOps(int ops) {
        int newOps = 0;
        if (ops == SelectionKey.OP_WRITE)
            newOps |= Net.POLLOUT;
        return newOps;
    }

    /**
     * Marks the beginning of a write operation that might block.
     *
     * @throws ClosedChannelException if the channel is closed
     * @throws NotYetConnectedException if the channel is not yet connected
     */
    private void beginWrite(boolean blocking) throws ClosedChannelException {
        if (blocking) {
            // set hook for Thread.interrupt
            begin();
        }
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (blocking)
                thread = NativeThread.current();
        }
    }

    /**
     * Marks the end of a write operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed due to this
     * thread being interrupted on a blocking write operation.
     */
    private void endWrite(boolean blocking, boolean completed)
        throws AsynchronousCloseException
    {
        if (blocking) {
            synchronized (stateLock) {
                thread = 0;
                // notify any thread waiting in implCloseSelectableChannel
                if (state == ST_CLOSING) {
                    stateLock.notifyAll();
                }
            }
            // remove hook for Thread.interrupt
            end(completed);
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        Objects.requireNonNull(src);

        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            try {
                beginWrite(blocking);
                do {
                    n = IOUtil.write(fd, src, -1, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
            } finally {
                endWrite(blocking, n > 0);
                assert IOStatus.check(n);
            }
            return IOStatus.normalize(n);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, srcs.length);

        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            long n = 0;
            try {
                beginWrite(blocking);
                do {
                    n = IOUtil.write(fd, srcs, offset, length, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
            } finally {
                endWrite(blocking, n > 0);
                assert IOStatus.check(n);
            }
            return IOStatus.normalize(n);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }
}
