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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;
import sun.net.util.SocketExceptions;
import static sun.net.ext.ExtendedSocketOptions.SOCK_STREAM;

/**
 * An implementation of SocketChannels
 */

class SocketChannelImpl
    extends SocketChannel
    implements SelChImpl
{
    // Used to make native read and write calls
    private static NativeDispatcher nd;

    // Our file descriptor object
    private final FileDescriptor fd;
    private final int fdVal;

    // Lock held by current reading or connecting thread
    private final ReentrantLock readLock = new ReentrantLock();

    // Lock held by current writing or connecting thread
    private final ReentrantLock writeLock = new ReentrantLock();

    // Lock held by any thread that modifies the state fields declared below
    // DO NOT invoke a blocking I/O operation while holding this lock!
    private final Object stateLock = new Object();

    // Input/Output closed
    private volatile boolean isInputClosed;
    private volatile boolean isOutputClosed;

    // -- The following fields are protected by stateLock

    // set true when exclusive binding is on and SO_REUSEADDR is emulated
    private boolean isReuseAddress;

    // State, increases monotonically
    private static final int ST_UNCONNECTED = 0;
    private static final int ST_CONNECTIONPENDING = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSING = 3;
    private static final int ST_KILLPENDING = 4;
    private static final int ST_KILLED = 5;
    private volatile int state;  // need stateLock to change

    // IDs of native threads doing reads and writes, for signalling
    private long readerThread;
    private long writerThread;

    // Binding
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;

    // Socket adaptor, created on demand
    private Socket socket;

    // -- End of fields protected by stateLock


    // Constructor for normal connecting sockets
    //
    SocketChannelImpl(SelectorProvider sp) throws IOException {
        super(sp);
        this.fd = Net.socket(true);
        this.fdVal = IOUtil.fdVal(fd);
    }

    SocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp);
        this.fd = fd;
        this.fdVal = IOUtil.fdVal(fd);
        if (bound) {
            synchronized (stateLock) {
                this.localAddress = Net.localAddress(fd);
            }
        }
    }

    // Constructor for sockets obtained from server sockets
    //
    SocketChannelImpl(SelectorProvider sp, FileDescriptor fd, InetSocketAddress isa)
        throws IOException
    {
        super(sp);
        this.fd = fd;
        this.fdVal = IOUtil.fdVal(fd);
        synchronized (stateLock) {
            this.localAddress = Net.localAddress(fd);
            this.remoteAddress = isa;
            this.state = ST_CONNECTED;
        }
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
     * Checks that the channel is open and connected.
     *
     * @apiNote This method uses the "state" field to check if the channel is
     * open. It should never be used in conjuncion with isOpen or ensureOpen
     * as these methods check AbstractInterruptibleChannel's closed field - that
     * field is set before implCloseSelectableChannel is called and so before
     * the state is changed.
     *
     * @throws ClosedChannelException if channel is closed (or closing)
     * @throws NotYetConnectedException if open and not connected
     */
    private void ensureOpenAndConnected() throws ClosedChannelException {
        int state = this.state;
        if (state < ST_CONNECTED) {
            throw new NotYetConnectedException();
        } else if (state > ST_CONNECTED) {
            throw new ClosedChannelException();
        }
    }

    @Override
    public Socket socket() {
        synchronized (stateLock) {
            if (socket == null)
                socket = SocketAdaptor.create(this);
            return socket;
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            return Net.getRevealedLocalAddress(localAddress);
        }
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            return remoteAddress;
        }
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value)
        throws IOException
    {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.IP_TOS) {
                ProtocolFamily family = Net.isIPv6Available() ?
                    StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
                Net.setSocketOption(fd, family, name, value);
                return this;
            }

            if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
                // SO_REUSEADDR emulated when using exclusive bind
                isReuseAddress = (Boolean)value;
                return this;
            }

            // no options that require special handling
            Net.setSocketOption(fd, Net.UNSPEC, name, value);
            return this;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> name)
        throws IOException
    {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
                // SO_REUSEADDR emulated when using exclusive bind
                return (T)Boolean.valueOf(isReuseAddress);
            }

            // special handling for IP_TOS: always return 0 when IPv6
            if (name == StandardSocketOptions.IP_TOS) {
                ProtocolFamily family = Net.isIPv6Available() ?
                    StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
                return (T) Net.getSocketOption(fd, family, name);
            }

            // no options that require special handling
            return (T) Net.getSocketOption(fd, Net.UNSPEC, name);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_KEEPALIVE);
            set.add(StandardSocketOptions.SO_REUSEADDR);
            if (Net.isReusePortAvailable()) {
                set.add(StandardSocketOptions.SO_REUSEPORT);
            }
            set.add(StandardSocketOptions.SO_LINGER);
            set.add(StandardSocketOptions.TCP_NODELAY);
            // additional options required by socket adaptor
            set.add(StandardSocketOptions.IP_TOS);
            set.add(ExtendedSocketOption.SO_OOBINLINE);
            set.addAll(ExtendedSocketOptions.options(SOCK_STREAM));
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    /**
     * Marks the beginning of a read operation that might block.
     *
     * @throws ClosedChannelException if the channel is closed
     * @throws NotYetConnectedException if the channel is not yet connected
     */
    private void beginRead(boolean blocking) throws ClosedChannelException {
        if (blocking) {
            // set hook for Thread.interrupt
            begin();

            synchronized (stateLock) {
                ensureOpenAndConnected();
                // record thread so it can be signalled if needed
                readerThread = NativeThread.current();
            }
        } else {
            ensureOpenAndConnected();
        }
    }

    /**
     * Marks the end of a read operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed due to this
     * thread being interrupted on a blocking read operation.
     */
    private void endRead(boolean blocking, boolean completed)
        throws AsynchronousCloseException
    {
        if (blocking) {
            synchronized (stateLock) {
                readerThread = 0;
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
    public int read(ByteBuffer buf) throws IOException {
        Objects.requireNonNull(buf);

        readLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            try {
                beginRead(blocking);

                // check if input is shutdown
                if (isInputClosed)
                    return IOStatus.EOF;

                if (blocking) {
                    do {
                        n = IOUtil.read(fd, buf, -1, nd);
                    } while (n == IOStatus.INTERRUPTED && isOpen());
                } else {
                    n = IOUtil.read(fd, buf, -1, nd);
                }
            } finally {
                endRead(blocking, n > 0);
                if (n <= 0 && isInputClosed)
                    return IOStatus.EOF;
            }
            return IOStatus.normalize(n);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException
    {
        Objects.checkFromIndexSize(offset, length, dsts.length);

        readLock.lock();
        try {
            boolean blocking = isBlocking();
            long n = 0;
            try {
                beginRead(blocking);

                // check if input is shutdown
                if (isInputClosed)
                    return IOStatus.EOF;

                if (blocking) {
                    do {
                        n = IOUtil.read(fd, dsts, offset, length, nd);
                    } while (n == IOStatus.INTERRUPTED && isOpen());
                } else {
                    n = IOUtil.read(fd, dsts, offset, length, nd);
                }
            } finally {
                endRead(blocking, n > 0);
                if (n <= 0 && isInputClosed)
                    return IOStatus.EOF;
            }
            return IOStatus.normalize(n);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Marks the beginning of a write operation that might block.
     *
     * @throws ClosedChannelException if the channel is closed or output shutdown
     * @throws NotYetConnectedException if the channel is not yet connected
     */
    private void beginWrite(boolean blocking) throws ClosedChannelException {
        if (blocking) {
            // set hook for Thread.interrupt
            begin();

            synchronized (stateLock) {
                ensureOpenAndConnected();
                if (isOutputClosed)
                    throw new ClosedChannelException();
                // record thread so it can be signalled if needed
                writerThread = NativeThread.current();
            }
        } else {
            ensureOpenAndConnected();
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
                writerThread = 0;
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
    public int write(ByteBuffer buf) throws IOException {
        Objects.requireNonNull(buf);

        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            try {
                beginWrite(blocking);
                if (blocking) {
                    do {
                        n = IOUtil.write(fd, buf, -1, nd);
                    } while (n == IOStatus.INTERRUPTED && isOpen());
                } else {
                    n = IOUtil.write(fd, buf, -1, nd);
                }
            } finally {
                endWrite(blocking, n > 0);
                if (n <= 0 && isOutputClosed)
                    throw new AsynchronousCloseException();
            }
            return IOStatus.normalize(n);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException
    {
        Objects.checkFromIndexSize(offset, length, srcs.length);

        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            long n = 0;
            try {
                beginWrite(blocking);
                if (blocking) {
                    do {
                        n = IOUtil.write(fd, srcs, offset, length, nd);
                    } while (n == IOStatus.INTERRUPTED && isOpen());
                } else {
                    n = IOUtil.write(fd, srcs, offset, length, nd);
                }
            } finally {
                endWrite(blocking, n > 0);
                if (n <= 0 && isOutputClosed)
                    throw new AsynchronousCloseException();
            }
            return IOStatus.normalize(n);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Writes a byte of out of band data.
     */
    int sendOutOfBandData(byte b) throws IOException {
        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            try {
                beginWrite(blocking);
                if (blocking) {
                    do {
                        n = sendOutOfBandData(fd, b);
                    } while (n == IOStatus.INTERRUPTED && isOpen());
                } else {
                    n = sendOutOfBandData(fd, b);
                }
            } finally {
                endWrite(blocking, n > 0);
                if (n <= 0 && isOutputClosed)
                    throw new AsynchronousCloseException();
            }
            return IOStatus.normalize(n);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    ensureOpen();
                    IOUtil.configureBlocking(fd, block);
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the local address, or null if not bound
     */
    InetSocketAddress localAddress() {
        synchronized (stateLock) {
            return localAddress;
        }
    }

    /**
     * Returns the remote address, or null if not connected
     */
    InetSocketAddress remoteAddress() {
        synchronized (stateLock) {
            return remoteAddress;
        }
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    ensureOpen();
                    if (state == ST_CONNECTIONPENDING)
                        throw new ConnectionPendingException();
                    if (localAddress != null)
                        throw new AlreadyBoundException();
                    InetSocketAddress isa = (local == null) ?
                        new InetSocketAddress(0) : Net.checkAddress(local);
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkListen(isa.getPort());
                    }
                    NetHooks.beforeTcpBind(fd, isa.getAddress(), isa.getPort());
                    Net.bind(fd, isa.getAddress(), isa.getPort());
                    localAddress = Net.localAddress(fd);
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
        return this;
    }

    @Override
    public boolean isConnected() {
        return (state == ST_CONNECTED);
    }

    @Override
    public boolean isConnectionPending() {
        return (state == ST_CONNECTIONPENDING);
    }

    /**
     * Marks the beginning of a connect operation that might block.
     * @param blocking true if configured blocking
     * @param isa the remote address
     * @throws ClosedChannelException if the channel is closed
     * @throws AlreadyConnectedException if already connected
     * @throws ConnectionPendingException is a connection is pending
     * @throws IOException if the pre-connect hook fails
     */
    private void beginConnect(boolean blocking, InetSocketAddress isa)
        throws IOException
    {
        if (blocking) {
            // set hook for Thread.interrupt
            begin();
        }
        synchronized (stateLock) {
            ensureOpen();
            int state = this.state;
            if (state == ST_CONNECTED)
                throw new AlreadyConnectedException();
            if (state == ST_CONNECTIONPENDING)
                throw new ConnectionPendingException();
            assert state == ST_UNCONNECTED;
            this.state = ST_CONNECTIONPENDING;

            if (localAddress == null)
                NetHooks.beforeTcpConnect(fd, isa.getAddress(), isa.getPort());
            remoteAddress = isa;

            if (blocking) {
                // record thread so it can be signalled if needed
                readerThread = NativeThread.current();
            }
        }
    }

    /**
     * Marks the end of a connect operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed due to this
     * thread being interrupted on a blocking connect operation.
     * @throws IOException if completed and unable to obtain the local address
     */
    private void endConnect(boolean blocking, boolean completed)
        throws IOException
    {
        endRead(blocking, completed);

        if (completed) {
            synchronized (stateLock) {
                if (state == ST_CONNECTIONPENDING) {
                    localAddress = Net.localAddress(fd);
                    state = ST_CONNECTED;
                }
            }
        }
    }

    @Override
    public boolean connect(SocketAddress sa) throws IOException {
        InetSocketAddress isa = Net.checkAddress(sa);
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkConnect(isa.getAddress().getHostAddress(), isa.getPort());

        InetAddress ia = isa.getAddress();
        if (ia.isAnyLocalAddress())
            ia = InetAddress.getLocalHost();

        try {
            readLock.lock();
            try {
                writeLock.lock();
                try {
                    int n = 0;
                    boolean blocking = isBlocking();
                    try {
                        beginConnect(blocking, isa);
                        do {
                            n = Net.connect(fd, ia, isa.getPort());
                        } while (n == IOStatus.INTERRUPTED && isOpen());
                    } finally {
                        endConnect(blocking, (n > 0));
                    }
                    assert IOStatus.check(n);
                    return n > 0;
                } finally {
                    writeLock.unlock();
                }
            } finally {
                readLock.unlock();
            }
        } catch (IOException ioe) {
            // connect failed, close the channel
            close();
            throw SocketExceptions.of(ioe, isa);
        }
    }

    /**
     * Marks the beginning of a finishConnect operation that might block.
     *
     * @throws ClosedChannelException if the channel is closed
     * @throws NoConnectionPendingException if no connection is pending
     */
    private void beginFinishConnect(boolean blocking) throws ClosedChannelException {
        if (blocking) {
            // set hook for Thread.interrupt
            begin();
        }
        synchronized (stateLock) {
            ensureOpen();
            if (state != ST_CONNECTIONPENDING)
                throw new NoConnectionPendingException();
            if (blocking) {
                // record thread so it can be signalled if needed
                readerThread = NativeThread.current();
            }
        }
    }

    /**
     * Marks the end of a finishConnect operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed due to this
     * thread being interrupted on a blocking connect operation.
     * @throws IOException if completed and unable to obtain the local address
     */
    private void endFinishConnect(boolean blocking, boolean completed)
        throws IOException
    {
        endRead(blocking, completed);

        if (completed) {
            synchronized (stateLock) {
                if (state == ST_CONNECTIONPENDING) {
                    localAddress = Net.localAddress(fd);
                    state = ST_CONNECTED;
                }
            }
        }
    }

    @Override
    public boolean finishConnect() throws IOException {
        try {
            readLock.lock();
            try {
                writeLock.lock();
                try {
                    // no-op if already connected
                    if (isConnected())
                        return true;

                    boolean blocking = isBlocking();
                    boolean connected = false;
                    try {
                        beginFinishConnect(blocking);
                        int n = 0;
                        if (blocking) {
                            do {
                                n = checkConnect(fd, true);
                            } while ((n == 0 || n == IOStatus.INTERRUPTED) && isOpen());
                        } else {
                            n = checkConnect(fd, false);
                        }
                        connected = (n > 0);
                    } finally {
                        endFinishConnect(blocking, connected);
                    }
                    assert (blocking && connected) ^ !blocking;
                    return connected;
                } finally {
                    writeLock.unlock();
                }
            } finally {
                readLock.unlock();
            }
        } catch (IOException ioe) {
            // connect failed, close the channel
            close();
            throw SocketExceptions.of(ioe, remoteAddress);
        }
    }

    /**
     * Invoked by implCloseChannel to close the channel.
     *
     * This method waits for outstanding I/O operations to complete. When in
     * blocking mode, the socket is pre-closed and the threads in blocking I/O
     * operations are signalled to ensure that the outstanding I/O operations
     * complete quickly.
     *
     * If the socket is connected then it is shutdown by this method. The
     * shutdown ensures that the peer reads EOF for the case that the socket is
     * not pre-closed or closed by this method.
     *
     * The socket is closed by this method when it is not registered with a
     * Selector. Note that a channel configured blocking may be registered with
     * a Selector. This arises when a key is canceled and the channel configured
     * to blocking mode before the key is flushed from the Selector.
     */
    @Override
    protected void implCloseSelectableChannel() throws IOException {
        assert !isOpen();

        boolean blocking;
        boolean connected;
        boolean interrupted = false;

        // set state to ST_CLOSING
        synchronized (stateLock) {
            assert state < ST_CLOSING;
            blocking = isBlocking();
            connected = (state == ST_CONNECTED);
            state = ST_CLOSING;
        }

        // wait for any outstanding I/O operations to complete
        if (blocking) {
            synchronized (stateLock) {
                assert state == ST_CLOSING;
                long reader = readerThread;
                long writer = writerThread;
                if (reader != 0 || writer != 0) {
                    nd.preClose(fd);
                    connected = false; // fd is no longer connected socket

                    if (reader != 0)
                        NativeThread.signal(reader);
                    if (writer != 0)
                        NativeThread.signal(writer);

                    // wait for blocking I/O operations to end
                    while (readerThread != 0 || writerThread != 0) {
                        try {
                            stateLock.wait();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                }
            }
        } else {
            // non-blocking mode: wait for read/write to complete
            readLock.lock();
            try {
                writeLock.lock();
                writeLock.unlock();
            } finally {
                readLock.unlock();
            }
        }

        // set state to ST_KILLPENDING
        synchronized (stateLock) {
            assert state == ST_CLOSING;
            // if connected and the channel is registered with a Selector then
            // shutdown the output if possible so that the peer reads EOF. If
            // SO_LINGER is enabled and set to a non-zero value then it needs to
            // be disabled so that the Selector does not wait when it closes
            // the socket.
            if (connected && isRegistered()) {
                try {
                    SocketOption<Integer> opt = StandardSocketOptions.SO_LINGER;
                    int interval = (int) Net.getSocketOption(fd, Net.UNSPEC, opt);
                    if (interval != 0) {
                        if (interval > 0) {
                            // disable SO_LINGER
                            Net.setSocketOption(fd, Net.UNSPEC, opt, -1);
                        }
                        Net.shutdown(fd, Net.SHUT_WR);
                    }
                } catch (IOException ignore) { }
            }
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
            if (state == ST_KILLPENDING) {
                state = ST_KILLED;
                nd.close(fd);
            }
        }
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            if (!isConnected())
                throw new NotYetConnectedException();
            if (!isInputClosed) {
                Net.shutdown(fd, Net.SHUT_RD);
                long thread = readerThread;
                if (thread != 0)
                    NativeThread.signal(thread);
                isInputClosed = true;
            }
            return this;
        }
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            if (!isConnected())
                throw new NotYetConnectedException();
            if (!isOutputClosed) {
                Net.shutdown(fd, Net.SHUT_WR);
                long thread = writerThread;
                if (thread != 0)
                    NativeThread.signal(thread);
                isOutputClosed = true;
            }
            return this;
        }
    }

    boolean isInputOpen() {
        return !isInputClosed;
    }

    boolean isOutputOpen() {
        return !isOutputClosed;
    }

    /**
     * Poll this channel's socket for reading up to the given timeout.
     * @return {@code true} if the socket is polled
     */
    boolean pollRead(long timeout) throws IOException {
        boolean blocking = isBlocking();
        assert Thread.holdsLock(blockingLock()) && blocking;

        readLock.lock();
        try {
            boolean polled = false;
            try {
                beginRead(blocking);
                int events = Net.poll(fd, Net.POLLIN, timeout);
                polled = (events != 0);
            } finally {
                endRead(blocking, polled);
            }
            return polled;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Poll this channel's socket for a connection, up to the given timeout.
     * @return {@code true} if the socket is polled
     */
    boolean pollConnected(long timeout) throws IOException {
        boolean blocking = isBlocking();
        assert Thread.holdsLock(blockingLock()) && blocking;

        readLock.lock();
        try {
            writeLock.lock();
            try {
                boolean polled = false;
                try {
                    beginFinishConnect(blocking);
                    int events = Net.poll(fd, Net.POLLCONN, timeout);
                    polled = (events != 0);
                } finally {
                    // invoke endFinishConnect with completed = false so that
                    // the state is not changed to ST_CONNECTED. The socket
                    // adaptor will use finishConnect to finish.
                    endFinishConnect(blocking, /*completed*/false);
                }
                return polled;
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Translates native poll revent ops into a ready operation ops
     */
    public boolean translateReadyOps(int ops, int initialOps, SelectionKeyImpl ski) {
        int intOps = ski.nioInterestOps();
        int oldOps = ski.nioReadyOps();
        int newOps = initialOps;

        if ((ops & Net.POLLNVAL) != 0) {
            // This should only happen if this channel is pre-closed while a
            // selection operation is in progress
            // ## Throw an error if this channel has not been pre-closed
            return false;
        }

        if ((ops & (Net.POLLERR | Net.POLLHUP)) != 0) {
            newOps = intOps;
            ski.nioReadyOps(newOps);
            return (newOps & ~oldOps) != 0;
        }

        boolean connected = isConnected();
        if (((ops & Net.POLLIN) != 0) &&
            ((intOps & SelectionKey.OP_READ) != 0) && connected)
            newOps |= SelectionKey.OP_READ;

        if (((ops & Net.POLLCONN) != 0) &&
            ((intOps & SelectionKey.OP_CONNECT) != 0) && isConnectionPending())
            newOps |= SelectionKey.OP_CONNECT;

        if (((ops & Net.POLLOUT) != 0) &&
            ((intOps & SelectionKey.OP_WRITE) != 0) && connected)
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

    /**
     * Translates an interest operation set into a native poll event set
     */
    public int translateInterestOps(int ops) {
        int newOps = 0;
        if ((ops & SelectionKey.OP_READ) != 0)
            newOps |= Net.POLLIN;
        if ((ops & SelectionKey.OP_WRITE) != 0)
            newOps |= Net.POLLOUT;
        if ((ops & SelectionKey.OP_CONNECT) != 0)
            newOps |= Net.POLLCONN;
        return newOps;
    }

    public FileDescriptor getFD() {
        return fd;
    }

    public int getFDVal() {
        return fdVal;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSuperclass().getName());
        sb.append('[');
        if (!isOpen())
            sb.append("closed");
        else {
            synchronized (stateLock) {
                switch (state) {
                case ST_UNCONNECTED:
                    sb.append("unconnected");
                    break;
                case ST_CONNECTIONPENDING:
                    sb.append("connection-pending");
                    break;
                case ST_CONNECTED:
                    sb.append("connected");
                    if (isInputClosed)
                        sb.append(" ishut");
                    if (isOutputClosed)
                        sb.append(" oshut");
                    break;
                }
                InetSocketAddress addr = localAddress();
                if (addr != null) {
                    sb.append(" local=");
                    sb.append(Net.getRevealedLocalAddressAsString(addr));
                }
                if (remoteAddress() != null) {
                    sb.append(" remote=");
                    sb.append(remoteAddress().toString());
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }


    // -- Native methods --

    private static native int checkConnect(FileDescriptor fd, boolean block)
        throws IOException;

    private static native int sendOutOfBandData(FileDescriptor fd, byte data)
        throws IOException;

    static {
        IOUtil.load();
        nd = new SocketDispatcher();
    }

}
