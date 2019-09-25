/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

class UnixDomainSocketChannelImpl
    extends AbstractInterruptibleChannel
    implements ByteChannel
{
    // Used to make native read and write calls
    private static final NativeDispatcher nd = new SocketDispatcher();

    // Our file descriptor object
    private final FileDescriptor fd;
    // Lock held by current reading or connecting thread
    private final ReentrantLock readLock = new ReentrantLock();

    // Lock held by current writing or connecting thread
    private final ReentrantLock writeLock = new ReentrantLock();

    // Lock for managing close state
    private final Object stateLock = new Object();

    // Channel state
    private static final int ST_INUSE = 0;
    private static final int ST_CLOSING = 1;
    private static final int ST_CLOSED = 2;
    private int state;

    // IDs of native threads doing reads and writes, for signalling
    private long readerThread;
    private long writerThread;

    UnixDomainSocketChannelImpl(FileDescriptor fd)
        throws IOException
    {
        this.fd = fd;
    }

    /**
     * Checks that the channel is open.
     *
     * @throws ClosedChannelException if channel is closed (or closing)
     */
    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    /**
     * Closes the socket if there are no I/O operations in progress
     */
    private boolean tryClose() throws IOException {
        assert Thread.holdsLock(stateLock) && state == ST_CLOSING;
        if (readerThread == 0 && writerThread == 0) {
            state = ST_CLOSED;
            nd.close(fd);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Complete closure of pre-closed socket (release the file descriptor)
     */
    private void tryFinishClose() {
        try {
            tryClose();
        } catch (IOException ignore) { }
    }

    /**
     * Marks the beginning of a read operation
     *
     * @throws ClosedChannelException if the channel is closed
     * @throws NotYetConnectedException if the channel is not yet connected
     */
    private void beginRead() throws ClosedChannelException {
        // set hook for Thread.interrupt
        begin();
        synchronized (stateLock) {
            ensureOpen();
            readerThread = NativeThread.current();
        }
    }

    /**
     * Marks the end of a read operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed due to this
     * thread being interrupted on a blocking read operation.
     */
    private void endRead(boolean completed)
        throws AsynchronousCloseException
    {
        synchronized (stateLock) {
            readerThread = 0;
            if (state == ST_CLOSING) {
                tryFinishClose();
            }
        }
        end(completed);
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        Objects.requireNonNull(buf);

        readLock.lock();
        try {
            int n = 0;
            try {
                beginRead();
                n = IOUtil.read(fd, buf, -1, nd);
                while (IOStatus.okayToRetry(n) && isOpen()) {
                    park(Net.POLLIN, 0L);
                    n = IOUtil.read(fd, buf, -1, nd);
                }
            } finally {
                endRead(n > 0);
            }
            return n;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Marks the beginning of a write operation that might block.
     *
     * @throws ClosedChannelException if the channel is closed
     * @throws NotYetConnectedException if the channel is not yet connected
     */
    private void beginWrite() throws ClosedChannelException {
        begin();
        synchronized (stateLock) {
            // set hook for Thread.interrupt
            ensureOpen();
            writerThread = NativeThread.current();
        }
    }

    /**
     * Marks the end of a write operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed due to this
     * thread being interrupted on a blocking write operation.
     */
    private void endWrite(boolean completed)
        throws AsynchronousCloseException
    {
        synchronized (stateLock) {
            writerThread = 0;
            if (state == ST_CLOSING) {
                tryFinishClose();
            }
        }
        end(completed);
    }

    void park(int event, long nanos) throws IOException {
        long millis;
        if (nanos <= 0) {
            millis = -1;
        } else {
            millis = NANOSECONDS.toMillis(nanos);
        }
        Net.poll(fd, event, millis);
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        Objects.requireNonNull(buf);

        writeLock.lock();
        try {
            int n = 0;
            try {
                beginWrite();
                n = IOUtil.write(fd, buf, -1, nd);
                while (IOStatus.okayToRetry(n) && isOpen()) {
                    park(Net.POLLOUT, 0L);
                    n = IOUtil.write(fd, buf, -1, nd);
                }
            } finally {
                endWrite(n > 0);
            }
            return n;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Closes this channel
     *
     * If there is an I/O operation in progress then the socket is pre-closed
     * and the I/O threads signalled, in which case the final close is deferred
     * until all I/O operations complete.
     */
    @Override
    protected void implCloseChannel() throws IOException {
        synchronized (stateLock) {
            assert state == ST_INUSE;
            state = ST_CLOSING;
            if (!tryClose()) {
                long reader = readerThread;
                long writer = writerThread;
                if (reader != 0 || writer != 0) {
                    nd.preClose(fd);
                    if (reader != 0)
                        NativeThread.signal(reader);
                    if (writer != 0)
                        NativeThread.signal(writer);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSuperclass().getName());
        sb.append('[');
        if (!isOpen())
            sb.append("closed");
        sb.append(']');
        return sb.toString();
    }
}
