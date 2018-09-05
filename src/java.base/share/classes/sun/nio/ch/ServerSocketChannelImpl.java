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
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;
import static sun.net.ext.ExtendedSocketOptions.SOCK_STREAM;

/**
 * An implementation of ServerSocketChannels
 */

class ServerSocketChannelImpl
    extends ServerSocketChannel
    implements SelChImpl
{
    // Used to make native close and configure calls
    private static NativeDispatcher nd;

    // Our file descriptor
    private final FileDescriptor fd;
    private final int fdVal;

    // Lock held by thread currently blocked on this channel
    private final ReentrantLock acceptLock = new ReentrantLock();

    // Lock held by any thread that modifies the state fields declared below
    // DO NOT invoke a blocking I/O operation while holding this lock!
    private final Object stateLock = new Object();

    // -- The following fields are protected by stateLock

    // Channel state, increases monotonically
    private static final int ST_INUSE = 0;
    private static final int ST_CLOSING = 1;
    private static final int ST_KILLPENDING = 2;
    private static final int ST_KILLED = 3;
    private int state;

    // ID of native thread currently blocked in this channel, for signalling
    private long thread;

    // Binding
    private InetSocketAddress localAddress; // null => unbound

    // set true when exclusive binding is on and SO_REUSEADDR is emulated
    private boolean isReuseAddress;

    // Our socket adaptor, if any
    private ServerSocket socket;

    // -- End of fields protected by stateLock


    ServerSocketChannelImpl(SelectorProvider sp) throws IOException {
        super(sp);
        this.fd =  Net.serverSocket(true);
        this.fdVal = IOUtil.fdVal(fd);
    }

    ServerSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp);
        this.fd =  fd;
        this.fdVal = IOUtil.fdVal(fd);
        if (bound) {
            synchronized (stateLock) {
                localAddress = Net.localAddress(fd);
            }
        }
    }

    // @throws ClosedChannelException if channel is closed
    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    @Override
    public ServerSocket socket() {
        synchronized (stateLock) {
            if (socket == null)
                socket = ServerSocketAdaptor.create(this);
            return socket;
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            return (localAddress == null)
                    ? null
                    : Net.getRevealedLocalAddress(localAddress);
        }
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value)
        throws IOException
    {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");
        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
                // SO_REUSEADDR emulated when using exclusive bind
                isReuseAddress = (Boolean)value;
            } else {
                // no options that require special handling
                Net.setSocketOption(fd, Net.UNSPEC, name, value);
            }
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
            // no options that require special handling
            return (T) Net.getSocketOption(fd, Net.UNSPEC, name);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_REUSEADDR);
            if (Net.isReusePortAvailable()) {
                set.add(StandardSocketOptions.SO_REUSEPORT);
            }
            set.addAll(ExtendedSocketOptions.options(SOCK_STREAM));
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            if (localAddress != null)
                throw new AlreadyBoundException();
            InetSocketAddress isa = (local == null)
                                    ? new InetSocketAddress(0)
                                    : Net.checkAddress(local);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null)
                sm.checkListen(isa.getPort());
            NetHooks.beforeTcpBind(fd, isa.getAddress(), isa.getPort());
            Net.bind(fd, isa.getAddress(), isa.getPort());
            Net.listen(fd, backlog < 1 ? 50 : backlog);
            localAddress = Net.localAddress(fd);
        }
        return this;
    }

    /**
     * Marks the beginning of an I/O operation that might block.
     *
     * @throws ClosedChannelException if the channel is closed
     * @throws NotYetBoundException if the channel's socket has not been bound yet
     */
    private void begin(boolean blocking) throws ClosedChannelException {
        if (blocking)
            begin();  // set blocker to close channel if interrupted
        synchronized (stateLock) {
            ensureOpen();
            if (localAddress == null)
                throw new NotYetBoundException();
            if (blocking)
                thread = NativeThread.current();
        }
    }

    /**
     * Marks the end of an I/O operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed due to this
     * thread being interrupted on a blocking I/O operation.
     */
    private void end(boolean blocking, boolean completed)
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
            end(completed);
        }
    }

    @Override
    public SocketChannel accept() throws IOException {
        acceptLock.lock();
        try {
            int n = 0;
            FileDescriptor newfd = new FileDescriptor();
            InetSocketAddress[] isaa = new InetSocketAddress[1];

            boolean blocking = isBlocking();
            try {
                begin(blocking);
                do {
                    n = accept(this.fd, newfd, isaa);
                } while (n == IOStatus.INTERRUPTED && isOpen());
            } finally {
                end(blocking, n > 0);
                assert IOStatus.check(n);
            }

            if (n < 1)
                return null;

            // newly accepted socket is initially in blocking mode
            IOUtil.configureBlocking(newfd, true);

            InetSocketAddress isa = isaa[0];
            SocketChannel sc = new SocketChannelImpl(provider(), newfd, isa);

            // check permitted to accept connections from the remote address
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                try {
                    sm.checkAccept(isa.getAddress().getHostAddress(), isa.getPort());
                } catch (SecurityException x) {
                    sc.close();
                    throw x;
                }
            }
            return sc;

        } finally {
            acceptLock.unlock();
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        acceptLock.lock();
        try {
            synchronized (stateLock) {
                ensureOpen();
                IOUtil.configureBlocking(fd, block);
            }
        } finally {
            acceptLock.unlock();
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
     * The socket is closed by this method when it is not registered with a
     * Selector. Note that a channel configured blocking may be registered with
     * a Selector. This arises when a key is canceled and the channel configured
     * to blocking mode before the key is flushed from the Selector.
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

        // wait for any outstanding accept to complete
        if (blocking) {
            synchronized (stateLock) {
                assert state == ST_CLOSING;
                long th = thread;
                if (th != 0) {
                    nd.preClose(fd);
                    NativeThread.signal(th);

                    // wait for accept operation to end
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
            // non-blocking mode: wait for accept to complete
            acceptLock.lock();
            acceptLock.unlock();
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
            if (state == ST_KILLPENDING) {
                state = ST_KILLED;
                nd.close(fd);
            }
        }
    }

    /**
     * Returns true if channel's socket is bound
     */
    boolean isBound() {
        synchronized (stateLock) {
            return localAddress != null;
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
     * Poll this channel's socket for a new connection up to the given timeout.
     * @return {@code true} if there is a connection to accept
     */
    boolean pollAccept(long timeout) throws IOException {
        assert Thread.holdsLock(blockingLock()) && isBlocking();
        acceptLock.lock();
        try {
            boolean polled = false;
            try {
                begin(true);
                int events = Net.poll(fd, Net.POLLIN, timeout);
                polled = (events != 0);
            } finally {
                end(true, polled);
            }
            return polled;
        } finally {
            acceptLock.unlock();
        }
    }

    /**
     * Translates native poll revent set into a ready operation set
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

        if (((ops & Net.POLLIN) != 0) &&
            ((intOps & SelectionKey.OP_ACCEPT) != 0))
                newOps |= SelectionKey.OP_ACCEPT;

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
        if ((ops & SelectionKey.OP_ACCEPT) != 0)
            newOps |= Net.POLLIN;
        return newOps;
    }

    public FileDescriptor getFD() {
        return fd;
    }

    public int getFDVal() {
        return fdVal;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName());
        sb.append('[');
        if (!isOpen()) {
            sb.append("closed");
        } else {
            synchronized (stateLock) {
                InetSocketAddress addr = localAddress;
                if (addr == null) {
                    sb.append("unbound");
                } else {
                    sb.append(Net.getRevealedLocalAddressAsString(addr));
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Accept a connection on a socket.
     *
     * @implNote Wrap native call to allow instrumentation.
     */
    private int accept(FileDescriptor ssfd,
                       FileDescriptor newfd,
                       InetSocketAddress[] isaa)
        throws IOException
    {
        return accept0(ssfd, newfd, isaa);
    }

    // -- Native methods --

    // Accepts a new connection, setting the given file descriptor to refer to
    // the new socket and setting isaa[0] to the socket's remote address.
    // Returns 1 on success, or IOStatus.UNAVAILABLE (if non-blocking and no
    // connections are pending) or IOStatus.INTERRUPTED.
    //
    private native int accept0(FileDescriptor ssfd,
                               FileDescriptor newfd,
                               InetSocketAddress[] isaa)
        throws IOException;

    private static native void initIDs();

    static {
        IOUtil.load();
        initIDs();
        nd = new SocketDispatcher();
    }

}
