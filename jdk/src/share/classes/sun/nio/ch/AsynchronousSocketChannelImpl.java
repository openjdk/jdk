/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.SocketOption;
import java.net.StandardSocketOption;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.io.FileDescriptor;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import sun.net.NetHooks;

/**
 * Base implementation of AsynchronousSocketChannel
 */

abstract class AsynchronousSocketChannelImpl
    extends AsynchronousSocketChannel
    implements Cancellable, Groupable
{
    protected final FileDescriptor fd;

    // protects state, localAddress, and remoteAddress
    protected final Object stateLock = new Object();

    protected volatile SocketAddress localAddress = null;
    protected volatile SocketAddress remoteAddress = null;

    // State, increases monotonically
    static final int ST_UNINITIALIZED = -1;
    static final int ST_UNCONNECTED = 0;
    static final int ST_PENDING = 1;
    static final int ST_CONNECTED = 2;
    protected volatile int state = ST_UNINITIALIZED;

    // reading state
    private final Object readLock = new Object();
    private boolean reading;
    private boolean readShutdown;
    private boolean readKilled;     // further reading disallowed due to timeout

    // writing state
    private final Object writeLock = new Object();
    private boolean writing;
    private boolean writeShutdown;
    private boolean writeKilled;    // further writing disallowed due to timeout

    // close support
    private final ReadWriteLock closeLock = new ReentrantReadWriteLock();
    private volatile boolean open = true;

    AsynchronousSocketChannelImpl(AsynchronousChannelGroupImpl group)
        throws IOException
    {
        super(group.provider());
        this.fd = Net.socket(true);
        this.state = ST_UNCONNECTED;
    }

    // Constructor for sockets obtained from AsynchronousServerSocketChannelImpl
    AsynchronousSocketChannelImpl(AsynchronousChannelGroupImpl group,
                                  FileDescriptor fd,
                                  InetSocketAddress remote)
        throws IOException
    {
        super(group.provider());
        this.fd = fd;
        this.state = ST_CONNECTED;
        this.localAddress = Net.localAddress(fd);
        this.remoteAddress = remote;
    }

    @Override
    public final boolean isOpen() {
        return open;
    }

    /**
     * Marks beginning of access to file descriptor/handle
     */
    final void begin() throws IOException {
        closeLock.readLock().lock();
        if (!isOpen())
            throw new ClosedChannelException();
    }

    /**
     * Marks end of access to file descriptor/handle
     */
    final void end() {
        closeLock.readLock().unlock();
    }

    /**
     * Invoked to close socket and release other resources.
     */
    abstract void implClose() throws IOException;

    @Override
    public final void close() throws IOException {
        // synchronize with any threads initiating asynchronous operations
        closeLock.writeLock().lock();
        try {
            if (!open)
                return;     // already closed
            open = false;
        } finally {
            closeLock.writeLock().unlock();
        }
        implClose();
    }

    final void enableReading(boolean killed) {
        synchronized (readLock) {
            reading = false;
            if (killed)
                readKilled = true;
        }
    }

    final void enableReading() {
        enableReading(false);
    }

    final void enableWriting(boolean killed) {
        synchronized (writeLock) {
            writing = false;
            if (killed)
                writeKilled = true;
        }
    }

    final void enableWriting() {
        enableWriting(false);
    }

    final void killReading() {
        synchronized (readLock) {
            readKilled = true;
        }
    }

    final void killWriting() {
        synchronized (writeLock) {
            writeKilled = true;
        }
    }

    final void killConnect() {
        // when a connect is cancelled then the connection may have been
        // established so prevent reading or writing.
        killReading();
        killWriting();
    }

    /**
     * Invoked by read to initiate the I/O operation.
     */
    abstract <V extends Number,A> Future<V> readImpl(ByteBuffer[] dsts,
                                                     boolean isScatteringRead,
                                                     long timeout,
                                                     TimeUnit unit,
                                                     A attachment,
                                                     CompletionHandler<V,? super A> handler);

    @SuppressWarnings("unchecked")
    private <V extends Number,A> Future<V> read(ByteBuffer[] dsts,
                                                boolean isScatteringRead,
                                                long timeout,
                                                TimeUnit unit,
                                                A attachment,
                                                CompletionHandler<V,? super A> handler)
    {
        if (!isOpen()) {
            CompletedFuture<V,A> result = CompletedFuture
                .withFailure(this, new ClosedChannelException(), attachment);
            Invoker.invoke(handler, result);
            return result;
        }

        if (remoteAddress == null)
            throw new NotYetConnectedException();
        if (timeout < 0L)
            throw new IllegalArgumentException("Negative timeout");

        boolean hasSpaceToRead = isScatteringRead || dsts[0].hasRemaining();
        boolean shutdown = false;

        // check and update state
        synchronized (readLock) {
            if (readKilled)
                throw new RuntimeException("Reading not allowed due to timeout or cancellation");
            if (reading)
                throw new ReadPendingException();
            if (readShutdown) {
                shutdown = true;
            } else {
                if (hasSpaceToRead) {
                    reading = true;
                }
            }
        }

        // immediately complete with -1 if shutdown for read
        // immediately complete with 0 if no space remaining
        if (shutdown || !hasSpaceToRead) {
            CompletedFuture<V,A> result;
            if (isScatteringRead) {
                Long value = (shutdown) ? Long.valueOf(-1L) : Long.valueOf(0L);
                result = (CompletedFuture<V,A>)CompletedFuture.withResult(this, value, attachment);
            } else {
                int value = (shutdown) ? -1 : 0;
                result = (CompletedFuture<V,A>)CompletedFuture.withResult(this, value, attachment);
            }
            Invoker.invoke(handler, result);
            return result;
        }

        return readImpl(dsts, isScatteringRead, timeout, unit, attachment, handler);
    }

    @Override
    public final <A> Future<Integer> read(ByteBuffer dst,
                                          long timeout,
                                          TimeUnit unit,
                                          A attachment,
                                          CompletionHandler<Integer,? super A> handler)
    {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        ByteBuffer[] bufs = new ByteBuffer[1];
        bufs[0] = dst;
        return read(bufs, false, timeout, unit, attachment, handler);
    }

    @Override
    public final <A> Future<Long> read(ByteBuffer[] dsts,
                                       int offset,
                                       int length,
                                       long timeout,
                                       TimeUnit unit,
                                       A attachment,
                                       CompletionHandler<Long,? super A> handler)
    {
        if ((offset < 0) || (length < 0) || (offset > dsts.length - length))
            throw new IndexOutOfBoundsException();
        ByteBuffer[] bufs = Util.subsequence(dsts, offset, length);
        for (int i=0; i<bufs.length; i++) {
            if (bufs[i].isReadOnly())
                throw new IllegalArgumentException("Read-only buffer");
        }
        return read(bufs, true, timeout, unit, attachment, handler);
    }

    /**
     * Invoked by write to initiate the I/O operation.
     */
    abstract <V extends Number,A> Future<V> writeImpl(ByteBuffer[] srcs,
                                                      boolean isGatheringWrite,
                                                      long timeout,
                                                      TimeUnit unit,
                                                      A attachment,
                                                      CompletionHandler<V,? super A> handler);

    @SuppressWarnings("unchecked")
    private <V extends Number,A> Future<V> write(ByteBuffer[] srcs,
                                                 boolean isGatheringWrite,
                                                 long timeout,
                                                 TimeUnit unit,
                                                 A attachment,
                                                 CompletionHandler<V,? super A> handler)
    {
        boolean hasDataToWrite = isGatheringWrite || srcs[0].hasRemaining();

        boolean closed = false;
        if (isOpen()) {
            if (remoteAddress == null)
                throw new NotYetConnectedException();
            if (timeout < 0L)
                throw new IllegalArgumentException("Negative timeout");
            // check and update state
            synchronized (writeLock) {
                if (writeKilled)
                    throw new RuntimeException("Writing not allowed due to timeout or cancellation");
                if (writing)
                    throw new WritePendingException();
                if (writeShutdown) {
                    closed = true;
                } else {
                    if (hasDataToWrite)
                        writing = true;
                }
            }
        } else {
            closed = true;
        }

        // channel is closed or shutdown for write
        if (closed) {
            CompletedFuture<V,A> result = CompletedFuture
                .withFailure(this, new ClosedChannelException(), attachment);
            Invoker.invoke(handler, result);
            return result;
        }

        // nothing to write so complete immediately
        if (!hasDataToWrite) {
            CompletedFuture<V,A> result;
            if (isGatheringWrite) {
                result = (CompletedFuture<V,A>)CompletedFuture.withResult(this, 0L, attachment);
            } else {
                result = (CompletedFuture<V,A>)CompletedFuture.withResult(this, 0, attachment);
            }
            Invoker.invoke(handler, result);
            return result;
        }

        return writeImpl(srcs, isGatheringWrite, timeout, unit, attachment, handler);
    }

    @Override
    public final <A> Future<Integer> write(ByteBuffer src,
                                           long timeout,
                                           TimeUnit unit,
                                           A attachment,
                                           CompletionHandler<Integer,? super A> handler)
    {
        ByteBuffer[] bufs = new ByteBuffer[1];
        bufs[0] = src;
        return write(bufs, false, timeout, unit, attachment, handler);
    }

    @Override
    public final <A> Future<Long> write(ByteBuffer[] srcs,
                                        int offset,
                                        int length,
                                        long timeout,
                                        TimeUnit unit,
                                        A attachment,
                                        CompletionHandler<Long,? super A> handler)
    {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length))
            throw new IndexOutOfBoundsException();
        srcs = Util.subsequence(srcs, offset, length);
        return write(srcs, true, timeout, unit, attachment, handler);
    }

    @Override
    public final AsynchronousSocketChannel bind(SocketAddress local)
        throws IOException
    {
        try {
            begin();
            synchronized (stateLock) {
                if (state == ST_PENDING)
                    throw new ConnectionPendingException();
                if (localAddress != null)
                    throw new AlreadyBoundException();
                InetSocketAddress isa = (local == null) ?
                    new InetSocketAddress(0) : Net.checkAddress(local);
                NetHooks.beforeTcpBind(fd, isa.getAddress(), isa.getPort());
                Net.bind(fd, isa.getAddress(), isa.getPort());
                localAddress = Net.localAddress(fd);
            }
        } finally {
            end();
        }
        return this;
    }

    @Override
    public final SocketAddress getLocalAddress() throws IOException {
        if (!isOpen())
            throw new ClosedChannelException();
        return localAddress;
    }

    @Override
    public final <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value)
        throws IOException
    {
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        try {
            begin();
            if (writeShutdown)
                throw new IOException("Connection has been shutdown for writing");
            Net.setSocketOption(fd, Net.UNSPEC, name, value);
            return this;
        } finally {
            end();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T getOption(SocketOption<T> name) throws IOException {
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        try {
            begin();
            return (T) Net.getSocketOption(fd, Net.UNSPEC, name);
        } finally {
            end();
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<SocketOption<?>>(5);
            set.add(StandardSocketOption.SO_SNDBUF);
            set.add(StandardSocketOption.SO_RCVBUF);
            set.add(StandardSocketOption.SO_KEEPALIVE);
            set.add(StandardSocketOption.SO_REUSEADDR);
            set.add(StandardSocketOption.TCP_NODELAY);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final SocketAddress getRemoteAddress() throws IOException {
        if (!isOpen())
            throw new ClosedChannelException();
        return remoteAddress;
    }

    @Override
    public final AsynchronousSocketChannel shutdownInput() throws IOException {
        try {
            begin();
            if (remoteAddress == null)
                throw new NotYetConnectedException();
            synchronized (readLock) {
                if (!readShutdown) {
                    Net.shutdown(fd, Net.SHUT_RD);
                    readShutdown = true;
                }
            }
        } finally {
            end();
        }
        return this;
    }

    @Override
    public final AsynchronousSocketChannel shutdownOutput() throws IOException {
        try {
            begin();
            if (remoteAddress == null)
                throw new NotYetConnectedException();
            synchronized (writeLock) {
                if (!writeShutdown) {
                    Net.shutdown(fd, Net.SHUT_WR);
                    writeShutdown = true;
                }
            }
        } finally {
            end();
        }
        return this;
    }

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName());
        sb.append('[');
        synchronized (stateLock) {
            if (!isOpen()) {
                sb.append("closed");
            } else {
                switch (state) {
                case ST_UNCONNECTED:
                    sb.append("unconnected");
                    break;
                case ST_PENDING:
                    sb.append("connection-pending");
                    break;
                case ST_CONNECTED:
                    sb.append("connected");
                    if (readShutdown)
                        sb.append(" ishut");
                    if (writeShutdown)
                        sb.append(" oshut");
                    break;
                }
                if (localAddress != null) {
                    sb.append(" local=");
                    sb.append(localAddress.toString());
                }
                if (remoteAddress != null) {
                    sb.append(" remote=");
                    sb.append(remoteAddress.toString());
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
