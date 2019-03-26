/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.ResourceManager;
import sun.net.ext.ExtendedSocketOptions;

/**
 * An implementation of DatagramChannels.
 */

class DatagramChannelImpl
    extends DatagramChannel
    implements SelChImpl
{
    // Used to make native read and write calls
    private static NativeDispatcher nd = new DatagramDispatcher();

    // The protocol family of the socket
    private final ProtocolFamily family;

    // Our file descriptor
    private final FileDescriptor fd;
    private final int fdVal;

    // Cached InetAddress and port for unconnected DatagramChannels
    // used by receive0
    private InetAddress cachedSenderInetAddress;
    private int cachedSenderPort;

    // Lock held by current reading or connecting thread
    private final ReentrantLock readLock = new ReentrantLock();

    // Lock held by current writing or connecting thread
    private final ReentrantLock writeLock = new ReentrantLock();

    // Lock held by any thread that modifies the state fields declared below
    // DO NOT invoke a blocking I/O operation while holding this lock!
    private final Object stateLock = new Object();

    // -- The following fields are protected by stateLock

    // State (does not necessarily increase monotonically)
    private static final int ST_UNCONNECTED = 0;
    private static final int ST_CONNECTED = 1;
    private static final int ST_CLOSING = 2;
    private static final int ST_KILLPENDING = 3;
    private static final int ST_KILLED = 4;
    private int state;

    // IDs of native threads doing reads and writes, for signalling
    private long readerThread;
    private long writerThread;

    // Binding and remote address (when connected)
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;

    // Our socket adaptor, if any
    private DatagramSocket socket;

    // Multicast support
    private MembershipRegistry registry;

    // set true when socket is bound and SO_REUSEADDRESS is emulated
    private boolean reuseAddressEmulated;

    // set true/false when socket is already bound and SO_REUSEADDR is emulated
    private boolean isReuseAddress;

    // -- End of fields protected by stateLock

    public DatagramChannelImpl(SelectorProvider sp)
        throws IOException
    {
        super(sp);
        ResourceManager.beforeUdpCreate();
        try {
            this.family = Net.isIPv6Available()
                    ? StandardProtocolFamily.INET6
                    : StandardProtocolFamily.INET;
            this.fd = Net.socket(family, false);
            this.fdVal = IOUtil.fdVal(fd);
        } catch (IOException ioe) {
            ResourceManager.afterUdpClose();
            throw ioe;
        }
    }

    public DatagramChannelImpl(SelectorProvider sp, ProtocolFamily family)
        throws IOException
    {
        super(sp);
        Objects.requireNonNull(family, "'family' is null");
        if ((family != StandardProtocolFamily.INET) &&
            (family != StandardProtocolFamily.INET6)) {
            throw new UnsupportedOperationException("Protocol family not supported");
        }
        if (family == StandardProtocolFamily.INET6) {
            if (!Net.isIPv6Available()) {
                throw new UnsupportedOperationException("IPv6 not available");
            }
        }

        ResourceManager.beforeUdpCreate();
        try {
            this.family = family;
            this.fd = Net.socket(family, false);
            this.fdVal = IOUtil.fdVal(fd);
        } catch (IOException ioe) {
            ResourceManager.afterUdpClose();
            throw ioe;
        }
    }

    public DatagramChannelImpl(SelectorProvider sp, FileDescriptor fd)
        throws IOException
    {
        super(sp);

        // increment UDP count to match decrement when closing
        ResourceManager.beforeUdpCreate();

        this.family = Net.isIPv6Available()
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET;
        this.fd = fd;
        this.fdVal = IOUtil.fdVal(fd);
        synchronized (stateLock) {
            this.localAddress = Net.localAddress(fd);
        }
    }

    // @throws ClosedChannelException if channel is closed
    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    @Override
    public DatagramSocket socket() {
        synchronized (stateLock) {
            if (socket == null)
                socket = DatagramSocketAdaptor.create(this);
            return socket;
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            // Perform security check before returning address
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
    public <T> DatagramChannel setOption(SocketOption<T> name, T value)
        throws IOException
    {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.IP_TOS ||
                name == StandardSocketOptions.IP_MULTICAST_TTL ||
                name == StandardSocketOptions.IP_MULTICAST_LOOP)
            {
                // options are protocol dependent
                Net.setSocketOption(fd, family, name, value);
                return this;
            }

            if (name == StandardSocketOptions.IP_MULTICAST_IF) {
                if (value == null)
                    throw new IllegalArgumentException("Cannot set IP_MULTICAST_IF to 'null'");
                NetworkInterface interf = (NetworkInterface)value;
                if (family == StandardProtocolFamily.INET6) {
                    int index = interf.getIndex();
                    if (index == -1)
                        throw new IOException("Network interface cannot be identified");
                    Net.setInterface6(fd, index);
                } else {
                    // need IPv4 address to identify interface
                    Inet4Address target = Net.anyInet4Address(interf);
                    if (target == null)
                        throw new IOException("Network interface not configured for IPv4");
                    int targetAddress = Net.inet4AsInt(target);
                    Net.setInterface4(fd, targetAddress);
                }
                return this;
            }
            if (name == StandardSocketOptions.SO_REUSEADDR
                && Net.useExclusiveBind() && localAddress != null) {
                reuseAddressEmulated = true;
                this.isReuseAddress = (Boolean)value;
            }

            // remaining options don't need any special handling
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

            if (name == StandardSocketOptions.IP_TOS ||
                name == StandardSocketOptions.IP_MULTICAST_TTL ||
                name == StandardSocketOptions.IP_MULTICAST_LOOP)
            {
                return (T) Net.getSocketOption(fd, family, name);
            }

            if (name == StandardSocketOptions.IP_MULTICAST_IF) {
                if (family == StandardProtocolFamily.INET) {
                    int address = Net.getInterface4(fd);
                    if (address == 0)
                        return null;    // default interface

                    InetAddress ia = Net.inet4FromInt(address);
                    NetworkInterface ni = NetworkInterface.getByInetAddress(ia);
                    if (ni == null)
                        throw new IOException("Unable to map address to interface");
                    return (T) ni;
                } else {
                    int index = Net.getInterface6(fd);
                    if (index == 0)
                        return null;    // default interface

                    NetworkInterface ni = NetworkInterface.getByIndex(index);
                    if (ni == null)
                        throw new IOException("Unable to map index to interface");
                    return (T) ni;
                }
            }

            if (name == StandardSocketOptions.SO_REUSEADDR && reuseAddressEmulated) {
                return (T)Boolean.valueOf(isReuseAddress);
            }

            // no special handling
            return (T) Net.getSocketOption(fd, Net.UNSPEC, name);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_REUSEADDR);
            if (Net.isReusePortAvailable()) {
                set.add(StandardSocketOptions.SO_REUSEPORT);
            }
            set.add(StandardSocketOptions.SO_BROADCAST);
            set.add(StandardSocketOptions.IP_TOS);
            set.add(StandardSocketOptions.IP_MULTICAST_IF);
            set.add(StandardSocketOptions.IP_MULTICAST_TTL);
            set.add(StandardSocketOptions.IP_MULTICAST_LOOP);
            set.addAll(ExtendedSocketOptions.datagramSocketOptions());
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
     * @param blocking true if configured blocking
     * @param mustBeConnected true if the socket must be connected
     * @return remote address if connected
     * @throws ClosedChannelException if the channel is closed
     * @throws NotYetConnectedException if mustBeConnected and not connected
     * @throws IOException if socket not bound and cannot be bound
     */
    private SocketAddress beginRead(boolean blocking, boolean mustBeConnected)
        throws IOException
    {
        if (blocking) {
            // set hook for Thread.interrupt
            begin();
        }
        SocketAddress remote;
        synchronized (stateLock) {
            ensureOpen();
            remote = remoteAddress;
            if ((remote == null) && mustBeConnected)
                throw new NotYetConnectedException();
            if (localAddress == null)
                bindInternal(null);
            if (blocking)
                readerThread = NativeThread.current();
        }
        return remote;
    }

    /**
     * Marks the end of a read operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed asynchronously
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

    private SocketAddress sender;       // Set by receive0 (## ugh)

    @Override
    public SocketAddress receive(ByteBuffer dst) throws IOException {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");

        readLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            ByteBuffer bb = null;
            try {
                SocketAddress remote = beginRead(blocking, false);
                boolean connected = (remote != null);
                SecurityManager sm = System.getSecurityManager();
                if (connected || (sm == null)) {
                    // connected or no security manager
                    do {
                        n = receive(fd, dst, connected);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                    if (n == IOStatus.UNAVAILABLE)
                        return null;
                } else {
                    // Cannot receive into user's buffer when running with a
                    // security manager and not connected
                    bb = Util.getTemporaryDirectBuffer(dst.remaining());
                    for (;;) {
                        do {
                            n = receive(fd, bb, connected);
                        } while ((n == IOStatus.INTERRUPTED) && isOpen());
                        if (n == IOStatus.UNAVAILABLE)
                            return null;
                        InetSocketAddress isa = (InetSocketAddress)sender;
                        try {
                            sm.checkAccept(isa.getAddress().getHostAddress(),
                                           isa.getPort());
                        } catch (SecurityException se) {
                            // Ignore packet
                            bb.clear();
                            n = 0;
                            continue;
                        }
                        bb.flip();
                        dst.put(bb);
                        break;
                    }
                }
                assert sender != null;
                return sender;
            } finally {
                if (bb != null)
                    Util.releaseTemporaryDirectBuffer(bb);
                endRead(blocking, n > 0);
                assert IOStatus.check(n);
            }
        } finally {
            readLock.unlock();
        }
    }

    private int receive(FileDescriptor fd, ByteBuffer dst, boolean connected)
        throws IOException
    {
        int pos = dst.position();
        int lim = dst.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        if (dst instanceof DirectBuffer && rem > 0)
            return receiveIntoNativeBuffer(fd, dst, rem, pos, connected);

        // Substitute a native buffer. If the supplied buffer is empty
        // we must instead use a nonempty buffer, otherwise the call
        // will not block waiting for a datagram on some platforms.
        int newSize = Math.max(rem, 1);
        ByteBuffer bb = Util.getTemporaryDirectBuffer(newSize);
        try {
            int n = receiveIntoNativeBuffer(fd, bb, newSize, 0, connected);
            bb.flip();
            if (n > 0 && rem > 0)
                dst.put(bb);
            return n;
        } finally {
            Util.releaseTemporaryDirectBuffer(bb);
        }
    }

    private int receiveIntoNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                        int rem, int pos, boolean connected)
        throws IOException
    {
        int n = receive0(fd, ((DirectBuffer)bb).address() + pos, rem, connected);
        if (n > 0)
            bb.position(pos + n);
        return n;
    }

    public int send(ByteBuffer src, SocketAddress target)
        throws IOException
    {
        Objects.requireNonNull(src);
        InetSocketAddress isa = Net.checkAddress(target, family);

        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            try {
                SocketAddress remote = beginWrite(blocking, false);
                if (remote != null) {
                    // connected
                    if (!target.equals(remote)) {
                        throw new AlreadyConnectedException();
                    }
                    do {
                        n = IOUtil.write(fd, src, -1, nd);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                } else {
                    // not connected
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        InetAddress ia = isa.getAddress();
                        if (ia.isMulticastAddress()) {
                            sm.checkMulticast(ia);
                        } else {
                            sm.checkConnect(ia.getHostAddress(), isa.getPort());
                        }
                    }
                    do {
                        n = send(fd, src, isa);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                }
            } finally {
                endWrite(blocking, n > 0);
                assert IOStatus.check(n);
            }
            return IOStatus.normalize(n);
        } finally {
            writeLock.unlock();
        }
    }

    private int send(FileDescriptor fd, ByteBuffer src, InetSocketAddress target)
        throws IOException
    {
        if (src instanceof DirectBuffer)
            return sendFromNativeBuffer(fd, src, target);

        // Substitute a native buffer
        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        ByteBuffer bb = Util.getTemporaryDirectBuffer(rem);
        try {
            bb.put(src);
            bb.flip();
            // Do not update src until we see how many bytes were written
            src.position(pos);

            int n = sendFromNativeBuffer(fd, bb, target);
            if (n > 0) {
                // now update src
                src.position(pos + n);
            }
            return n;
        } finally {
            Util.releaseTemporaryDirectBuffer(bb);
        }
    }

    private int sendFromNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                     InetSocketAddress target)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        boolean preferIPv6 = (family != StandardProtocolFamily.INET);
        int written;
        try {
            written = send0(preferIPv6, fd, ((DirectBuffer)bb).address() + pos,
                            rem, target.getAddress(), target.getPort());
        } catch (PortUnreachableException pue) {
            if (isConnected())
                throw pue;
            written = rem;
        }
        if (written > 0)
            bb.position(pos + written);
        return written;
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        Objects.requireNonNull(buf);

        readLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            try {
                beginRead(blocking, true);
                do {
                    n = IOUtil.read(fd, buf, -1, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());

            } finally {
                endRead(blocking, n > 0);
                assert IOStatus.check(n);
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
                beginRead(blocking, true);
                do {
                    n = IOUtil.read(fd, dsts, offset, length, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());

            } finally {
                endRead(blocking, n > 0);
                assert IOStatus.check(n);
            }
            return IOStatus.normalize(n);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Marks the beginning of a write operation that might block.
     * @param blocking true if configured blocking
     * @param mustBeConnected true if the socket must be connected
     * @return remote address if connected
     * @throws ClosedChannelException if the channel is closed
     * @throws NotYetConnectedException if mustBeConnected and not connected
     * @throws IOException if socket not bound and cannot be bound
     */
    private SocketAddress beginWrite(boolean blocking, boolean mustBeConnected)
        throws IOException
    {
        if (blocking) {
            // set hook for Thread.interrupt
            begin();
        }
        SocketAddress remote;
        synchronized (stateLock) {
            ensureOpen();
            remote = remoteAddress;
            if ((remote == null) && mustBeConnected)
                throw new NotYetConnectedException();
            if (localAddress == null)
                bindInternal(null);
            if (blocking)
                writerThread = NativeThread.current();
        }
        return remote;
    }

    /**
     * Marks the end of a write operation that may have blocked.
     *
     * @throws AsynchronousCloseException if the channel was closed asynchronously
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
                beginWrite(blocking, true);
                do {
                    n = IOUtil.write(fd, buf, -1, nd);
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
    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException
    {
        Objects.checkFromIndexSize(offset, length, srcs.length);

        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            long n = 0;
            try {
                beginWrite(blocking, true);
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

    InetSocketAddress localAddress() {
        synchronized (stateLock) {
            return localAddress;
        }
    }

    InetSocketAddress remoteAddress() {
        synchronized (stateLock) {
            return remoteAddress;
        }
    }

    @Override
    public DatagramChannel bind(SocketAddress local) throws IOException {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    ensureOpen();
                    if (localAddress != null)
                        throw new AlreadyBoundException();
                    bindInternal(local);
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
        return this;
    }

    private void bindInternal(SocketAddress local) throws IOException {
        assert Thread.holdsLock(stateLock) && (localAddress == null);

        InetSocketAddress isa;
        if (local == null) {
            // only Inet4Address allowed with IPv4 socket
            if (family == StandardProtocolFamily.INET) {
                isa = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0);
            } else {
                isa = new InetSocketAddress(0);
            }
        } else {
            isa = Net.checkAddress(local, family);
        }
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkListen(isa.getPort());

        Net.bind(family, fd, isa.getAddress(), isa.getPort());
        localAddress = Net.localAddress(fd);
    }

    @Override
    public boolean isConnected() {
        synchronized (stateLock) {
            return (state == ST_CONNECTED);
        }
    }

    @Override
    public DatagramChannel connect(SocketAddress sa) throws IOException {
        InetSocketAddress isa = Net.checkAddress(sa, family);
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            InetAddress ia = isa.getAddress();
            if (ia.isMulticastAddress()) {
                sm.checkMulticast(ia);
            } else {
                sm.checkConnect(ia.getHostAddress(), isa.getPort());
                sm.checkAccept(ia.getHostAddress(), isa.getPort());
            }
        }

        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    ensureOpen();
                    if (state == ST_CONNECTED)
                        throw new AlreadyConnectedException();

                    int n = Net.connect(family,
                                        fd,
                                        isa.getAddress(),
                                        isa.getPort());
                    if (n <= 0)
                        throw new Error();      // Can't happen

                    // connected
                    remoteAddress = isa;
                    state = ST_CONNECTED;

                    // refresh local address
                    localAddress = Net.localAddress(fd);

                    // flush any packets already received.
                    boolean blocking = isBlocking();
                    if (blocking) {
                        IOUtil.configureBlocking(fd, false);
                    }
                    try {
                        ByteBuffer buf = ByteBuffer.allocate(100);
                        while (receive(buf) != null) {
                            buf.clear();
                        }
                    } finally {
                        if (blocking) {
                            IOUtil.configureBlocking(fd, true);
                        }
                    }
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
    public DatagramChannel disconnect() throws IOException {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    if (!isOpen() || (state != ST_CONNECTED))
                        return this;

                    // disconnect socket
                    boolean isIPv6 = (family == StandardProtocolFamily.INET6);
                    disconnect0(fd, isIPv6);

                    // no longer connected
                    remoteAddress = null;
                    state = ST_UNCONNECTED;

                    // refresh local address
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

    /**
     * Joins channel's socket to the given group/interface and
     * optional source address.
     */
    private MembershipKey innerJoin(InetAddress group,
                                    NetworkInterface interf,
                                    InetAddress source)
        throws IOException
    {
        if (!group.isMulticastAddress())
            throw new IllegalArgumentException("Group not a multicast address");

        // check multicast address is compatible with this socket
        if (group instanceof Inet4Address) {
            if (family == StandardProtocolFamily.INET6 && !Net.canIPv6SocketJoinIPv4Group())
                throw new IllegalArgumentException("IPv6 socket cannot join IPv4 multicast group");
        } else if (group instanceof Inet6Address) {
            if (family != StandardProtocolFamily.INET6)
                throw new IllegalArgumentException("Only IPv6 sockets can join IPv6 multicast group");
        } else {
            throw new IllegalArgumentException("Address type not supported");
        }

        // check source address
        if (source != null) {
            if (source.isAnyLocalAddress())
                throw new IllegalArgumentException("Source address is a wildcard address");
            if (source.isMulticastAddress())
                throw new IllegalArgumentException("Source address is multicast address");
            if (source.getClass() != group.getClass())
                throw new IllegalArgumentException("Source address is different type to group");
        }

        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkMulticast(group);

        synchronized (stateLock) {
            ensureOpen();

            // check the registry to see if we are already a member of the group
            if (registry == null) {
                registry = new MembershipRegistry();
            } else {
                // return existing membership key
                MembershipKey key = registry.checkMembership(group, interf, source);
                if (key != null)
                    return key;
            }

            MembershipKeyImpl key;
            if ((family == StandardProtocolFamily.INET6) &&
                ((group instanceof Inet6Address) || Net.canJoin6WithIPv4Group()))
            {
                int index = interf.getIndex();
                if (index == -1)
                    throw new IOException("Network interface cannot be identified");

                // need multicast and source address as byte arrays
                byte[] groupAddress = Net.inet6AsByteArray(group);
                byte[] sourceAddress = (source == null) ? null :
                    Net.inet6AsByteArray(source);

                // join the group
                int n = Net.join6(fd, groupAddress, index, sourceAddress);
                if (n == IOStatus.UNAVAILABLE)
                    throw new UnsupportedOperationException();

                key = new MembershipKeyImpl.Type6(this, group, interf, source,
                                                  groupAddress, index, sourceAddress);

            } else {
                // need IPv4 address to identify interface
                Inet4Address target = Net.anyInet4Address(interf);
                if (target == null)
                    throw new IOException("Network interface not configured for IPv4");

                int groupAddress = Net.inet4AsInt(group);
                int targetAddress = Net.inet4AsInt(target);
                int sourceAddress = (source == null) ? 0 : Net.inet4AsInt(source);

                // join the group
                int n = Net.join4(fd, groupAddress, targetAddress, sourceAddress);
                if (n == IOStatus.UNAVAILABLE)
                    throw new UnsupportedOperationException();

                key = new MembershipKeyImpl.Type4(this, group, interf, source,
                                                  groupAddress, targetAddress, sourceAddress);
            }

            registry.add(key);
            return key;
        }
    }

    @Override
    public MembershipKey join(InetAddress group,
                              NetworkInterface interf)
        throws IOException
    {
        return innerJoin(group, interf, null);
    }

    @Override
    public MembershipKey join(InetAddress group,
                              NetworkInterface interf,
                              InetAddress source)
        throws IOException
    {
        Objects.requireNonNull(source);
        return innerJoin(group, interf, source);
    }

    // package-private
    void drop(MembershipKeyImpl key) {
        assert key.channel() == this;

        synchronized (stateLock) {
            if (!key.isValid())
                return;

            try {
                if (key instanceof MembershipKeyImpl.Type6) {
                    MembershipKeyImpl.Type6 key6 =
                        (MembershipKeyImpl.Type6)key;
                    Net.drop6(fd, key6.groupAddress(), key6.index(), key6.source());
                } else {
                    MembershipKeyImpl.Type4 key4 = (MembershipKeyImpl.Type4)key;
                    Net.drop4(fd, key4.groupAddress(), key4.interfaceAddress(),
                        key4.source());
                }
            } catch (IOException ioe) {
                // should not happen
                throw new AssertionError(ioe);
            }

            key.invalidate();
            registry.remove(key);
        }
    }

    /**
     * Block datagrams from given source if a memory to receive all
     * datagrams.
     */
    void block(MembershipKeyImpl key, InetAddress source)
        throws IOException
    {
        assert key.channel() == this;
        assert key.sourceAddress() == null;

        synchronized (stateLock) {
            if (!key.isValid())
                throw new IllegalStateException("key is no longer valid");
            if (source.isAnyLocalAddress())
                throw new IllegalArgumentException("Source address is a wildcard address");
            if (source.isMulticastAddress())
                throw new IllegalArgumentException("Source address is multicast address");
            if (source.getClass() != key.group().getClass())
                throw new IllegalArgumentException("Source address is different type to group");

            int n;
            if (key instanceof MembershipKeyImpl.Type6) {
                 MembershipKeyImpl.Type6 key6 =
                    (MembershipKeyImpl.Type6)key;
                n = Net.block6(fd, key6.groupAddress(), key6.index(),
                               Net.inet6AsByteArray(source));
            } else {
                MembershipKeyImpl.Type4 key4 =
                    (MembershipKeyImpl.Type4)key;
                n = Net.block4(fd, key4.groupAddress(), key4.interfaceAddress(),
                               Net.inet4AsInt(source));
            }
            if (n == IOStatus.UNAVAILABLE) {
                // ancient kernel
                throw new UnsupportedOperationException();
            }
        }
    }

    /**
     * Unblock given source.
     */
    void unblock(MembershipKeyImpl key, InetAddress source) {
        assert key.channel() == this;
        assert key.sourceAddress() == null;

        synchronized (stateLock) {
            if (!key.isValid())
                throw new IllegalStateException("key is no longer valid");

            try {
                if (key instanceof MembershipKeyImpl.Type6) {
                    MembershipKeyImpl.Type6 key6 =
                        (MembershipKeyImpl.Type6)key;
                    Net.unblock6(fd, key6.groupAddress(), key6.index(),
                                 Net.inet6AsByteArray(source));
                } else {
                    MembershipKeyImpl.Type4 key4 =
                        (MembershipKeyImpl.Type4)key;
                    Net.unblock4(fd, key4.groupAddress(), key4.interfaceAddress(),
                                 Net.inet4AsInt(source));
                }
            } catch (IOException ioe) {
                // should not happen
                throw new AssertionError(ioe);
            }
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

        boolean blocking;
        boolean interrupted = false;

        // set state to ST_CLOSING and invalid membership keys
        synchronized (stateLock) {
            assert state < ST_CLOSING;
            blocking = isBlocking();
            state = ST_CLOSING;

            // if member of any multicast groups then invalidate the keys
            if (registry != null)
                registry.invalidateAll();
        }

        // wait for any outstanding I/O operations to complete
        if (blocking) {
            synchronized (stateLock) {
                assert state == ST_CLOSING;
                long reader = readerThread;
                long writer = writerThread;
                if (reader != 0 || writer != 0) {
                    nd.preClose(fd);

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
                try {
                    nd.close(fd);
                } finally {
                    // notify resource manager
                    ResourceManager.afterUdpClose();
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    protected void finalize() throws IOException {
        // fd is null if constructor threw exception
        if (fd != null)
            close();
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
            ((intOps & SelectionKey.OP_READ) != 0))
            newOps |= SelectionKey.OP_READ;

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
                beginRead(blocking, false);
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
     * Translates an interest operation set into a native poll event set
     */
    public int translateInterestOps(int ops) {
        int newOps = 0;
        if ((ops & SelectionKey.OP_READ) != 0)
            newOps |= Net.POLLIN;
        if ((ops & SelectionKey.OP_WRITE) != 0)
            newOps |= Net.POLLOUT;
        if ((ops & SelectionKey.OP_CONNECT) != 0)
            newOps |= Net.POLLIN;
        return newOps;
    }

    public FileDescriptor getFD() {
        return fd;
    }

    public int getFDVal() {
        return fdVal;
    }


    // -- Native methods --

    private static native void initIDs();

    private static native void disconnect0(FileDescriptor fd, boolean isIPv6)
        throws IOException;

    private native int receive0(FileDescriptor fd, long address, int len,
                                boolean connected)
        throws IOException;

    private native int send0(boolean preferIPv6, FileDescriptor fd, long address,
                             int len, InetAddress addr, int port)
        throws IOException;

    static {
        IOUtil.load();
        initIDs();
    }
}
