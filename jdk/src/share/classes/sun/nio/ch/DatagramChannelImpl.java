/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;


/**
 * An implementation of DatagramChannels.
 */

class DatagramChannelImpl
    extends DatagramChannel
    implements SelChImpl
{

    // Used to make native read and write calls
    private static NativeDispatcher nd = new DatagramDispatcher();

    // Our file descriptor
    private final FileDescriptor fd;

    // fd value needed for dev/poll. This value will remain valid
    // even after the value in the file descriptor object has been set to -1
    private final int fdVal;

    // The protocol family of the socket
    private final ProtocolFamily family;

    // IDs of native threads doing reads and writes, for signalling
    private volatile long readerThread = 0;
    private volatile long writerThread = 0;

    // Cached InetAddress and port for unconnected DatagramChannels
    // used by receive0
    private InetAddress cachedSenderInetAddress;
    private int cachedSenderPort;

    // Lock held by current reading or connecting thread
    private final Object readLock = new Object();

    // Lock held by current writing or connecting thread
    private final Object writeLock = new Object();

    // Lock held by any thread that modifies the state fields declared below
    // DO NOT invoke a blocking I/O operation while holding this lock!
    private final Object stateLock = new Object();

    // -- The following fields are protected by stateLock

    // State (does not necessarily increase monotonically)
    private static final int ST_UNINITIALIZED = -1;
    private static final int ST_UNCONNECTED = 0;
    private static final int ST_CONNECTED = 1;
    private static final int ST_KILLED = 2;
    private int state = ST_UNINITIALIZED;

    // Binding
    private SocketAddress localAddress;
    private SocketAddress remoteAddress;

    // Our socket adaptor, if any
    private DatagramSocket socket;

    // Multicast support
    private MembershipRegistry registry;

    // -- End of fields protected by stateLock


    public DatagramChannelImpl(SelectorProvider sp)
        throws IOException
    {
        super(sp);
        this.family = Net.isIPv6Available() ?
            StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
        this.fd = Net.socket(family, false);
        this.fdVal = IOUtil.fdVal(fd);
        this.state = ST_UNCONNECTED;
    }

    public DatagramChannelImpl(SelectorProvider sp, ProtocolFamily family) {
        super(sp);
        if ((family != StandardProtocolFamily.INET) &&
            (family != StandardProtocolFamily.INET6))
        {
            if (family == null)
                throw new NullPointerException("'family' is null");
            else
                throw new UnsupportedOperationException("Protocol family not supported");
        }
        if (family == StandardProtocolFamily.INET6) {
            if (!Net.isIPv6Available()) {
                throw new UnsupportedOperationException("IPv6 not available");
            }
        }
        this.family = family;
        this.fd = Net.socket(family, false);
        this.fdVal = IOUtil.fdVal(fd);
        this.state = ST_UNCONNECTED;
    }

    public DatagramChannelImpl(SelectorProvider sp, FileDescriptor fd)
        throws IOException
    {
        super(sp);
        this.family = Net.isIPv6Available() ?
            StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
        this.fd = fd;
        this.fdVal = IOUtil.fdVal(fd);
        this.state = ST_UNCONNECTED;
        this.localAddress = Net.localAddress(fd);
    }

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
            if (!isOpen())
                throw new ClosedChannelException();
            return localAddress;
        }
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            return remoteAddress;
        }
    }

    @Override
    public <T> DatagramChannel setOption(SocketOption<T> name, T value)
        throws IOException
    {
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOption.IP_TOS) {
                // IPv4 only; no-op for IPv6
                if (family == StandardProtocolFamily.INET) {
                    Net.setSocketOption(fd, family, name, value);
                }
                return this;
            }

            if (name == StandardSocketOption.IP_MULTICAST_TTL ||
                name == StandardSocketOption.IP_MULTICAST_LOOP)
            {
                // options are protocol dependent
                Net.setSocketOption(fd, family, name, value);
                return this;
            }

            if (name == StandardSocketOption.IP_MULTICAST_IF) {
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
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOption.IP_TOS) {
                // IPv4 only; always return 0 on IPv6
                if (family == StandardProtocolFamily.INET) {
                    return (T) Net.getSocketOption(fd, family, name);
                } else {
                    return (T) Integer.valueOf(0);
                }
            }

            if (name == StandardSocketOption.IP_MULTICAST_TTL ||
                name == StandardSocketOption.IP_MULTICAST_LOOP)
            {
                return (T) Net.getSocketOption(fd, family, name);
            }

            if (name == StandardSocketOption.IP_MULTICAST_IF) {
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

            // no special handling
            return (T) Net.getSocketOption(fd, Net.UNSPEC, name);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<SocketOption<?>>(8);
            set.add(StandardSocketOption.SO_SNDBUF);
            set.add(StandardSocketOption.SO_RCVBUF);
            set.add(StandardSocketOption.SO_REUSEADDR);
            set.add(StandardSocketOption.SO_BROADCAST);
            set.add(StandardSocketOption.IP_TOS);
            set.add(StandardSocketOption.IP_MULTICAST_IF);
            set.add(StandardSocketOption.IP_MULTICAST_TTL);
            set.add(StandardSocketOption.IP_MULTICAST_LOOP);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    private SocketAddress sender;       // Set by receive0 (## ugh)

    public SocketAddress receive(ByteBuffer dst) throws IOException {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        if (dst == null)
            throw new NullPointerException();
        synchronized (readLock) {
            ensureOpen();
            // Socket was not bound before attempting receive
            if (localAddress() == null)
                bind(null);
            int n = 0;
            ByteBuffer bb = null;
            try {
                begin();
                if (!isOpen())
                    return null;
                SecurityManager security = System.getSecurityManager();
                readerThread = NativeThread.current();
                if (isConnected() || (security == null)) {
                    do {
                        n = receive(fd, dst);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                    if (n == IOStatus.UNAVAILABLE)
                        return null;
                } else {
                    bb = Util.getTemporaryDirectBuffer(dst.remaining());
                    for (;;) {
                        do {
                            n = receive(fd, bb);
                        } while ((n == IOStatus.INTERRUPTED) && isOpen());
                        if (n == IOStatus.UNAVAILABLE)
                            return null;
                        InetSocketAddress isa = (InetSocketAddress)sender;
                        try {
                            security.checkAccept(
                                isa.getAddress().getHostAddress(),
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
                return sender;
            } finally {
                if (bb != null)
                    Util.releaseTemporaryDirectBuffer(bb);
                readerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    private int receive(FileDescriptor fd, ByteBuffer dst)
        throws IOException
    {
        int pos = dst.position();
        int lim = dst.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        if (dst instanceof DirectBuffer && rem > 0)
            return receiveIntoNativeBuffer(fd, dst, rem, pos);

        // Substitute a native buffer. If the supplied buffer is empty
        // we must instead use a nonempty buffer, otherwise the call
        // will not block waiting for a datagram on some platforms.
        int newSize = Math.max(rem, 1);
        ByteBuffer bb = null;
        try {
            bb = Util.getTemporaryDirectBuffer(newSize);
            int n = receiveIntoNativeBuffer(fd, bb, newSize, 0);
            bb.flip();
            if (n > 0 && rem > 0)
                dst.put(bb);
            return n;
        } finally {
            Util.releaseTemporaryDirectBuffer(bb);
        }
    }

    private int receiveIntoNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                        int rem, int pos)
        throws IOException
    {
        int n = receive0(fd, ((DirectBuffer)bb).address() + pos, rem,
                         isConnected());
        if (n > 0)
            bb.position(pos + n);
        return n;
    }

    public int send(ByteBuffer src, SocketAddress target)
        throws IOException
    {
        if (src == null)
            throw new NullPointerException();

        synchronized (writeLock) {
            ensureOpen();
            InetSocketAddress isa = (InetSocketAddress)target;
            InetAddress ia = isa.getAddress();
            if (ia == null)
                throw new IOException("Target address not resolved");
            synchronized (stateLock) {
                if (!isConnected()) {
                    if (target == null)
                        throw new NullPointerException();
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        if (ia.isMulticastAddress()) {
                            sm.checkMulticast(isa.getAddress());
                        } else {
                            sm.checkConnect(isa.getAddress().getHostAddress(),
                                            isa.getPort());
                        }
                    }
                } else { // Connected case; Check address then write
                    if (!target.equals(remoteAddress)) {
                        throw new IllegalArgumentException(
                            "Connected address not equal to target address");
                    }
                    return write(src);
                }
            }

            int n = 0;
            try {
                begin();
                if (!isOpen())
                    return 0;
                writerThread = NativeThread.current();
                do {
                    n = send(fd, src, target);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());

                synchronized (stateLock) {
                    if (isOpen() && (localAddress == null)) {
                        localAddress = Net.localAddress(fd);
                    }
                }
                return IOStatus.normalize(n);
            } finally {
                writerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    private int send(FileDescriptor fd, ByteBuffer src, SocketAddress target)
        throws IOException
    {
        if (src instanceof DirectBuffer)
            return sendFromNativeBuffer(fd, src, target);

        // Substitute a native buffer
        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        ByteBuffer bb = null;
        try {
            bb = Util.getTemporaryDirectBuffer(rem);
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
                                            SocketAddress target)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        boolean preferIPv6 = (family != StandardProtocolFamily.INET);
        int written = send0(preferIPv6, fd, ((DirectBuffer)bb).address() + pos,
                            rem, target);
        if (written > 0)
            bb.position(pos + written);
        return written;
    }

    public int read(ByteBuffer buf) throws IOException {
        if (buf == null)
            throw new NullPointerException();
        synchronized (readLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected())
                    throw new NotYetConnectedException();
            }
            int n = 0;
            try {
                begin();
                if (!isOpen())
                    return 0;
                readerThread = NativeThread.current();
                do {
                    n = IOUtil.read(fd, buf, -1, nd, readLock);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                readerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    private long read0(ByteBuffer[] bufs) throws IOException {
        if (bufs == null)
            throw new NullPointerException();
        synchronized (readLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected())
                    throw new NotYetConnectedException();
            }
            long n = 0;
            try {
                begin();
                if (!isOpen())
                    return 0;
                readerThread = NativeThread.current();
                do {
                    n = IOUtil.read(fd, bufs, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                readerThread = 0;
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
        // ## Fix IOUtil.write so that we can avoid this array copy
        return read0(Util.subsequence(dsts, offset, length));
    }

    public int write(ByteBuffer buf) throws IOException {
        if (buf == null)
            throw new NullPointerException();
        synchronized (writeLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected())
                    throw new NotYetConnectedException();
            }
            int n = 0;
            try {
                begin();
                if (!isOpen())
                    return 0;
                writerThread = NativeThread.current();
                do {
                    n = IOUtil.write(fd, buf, -1, nd, writeLock);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                writerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    private long write0(ByteBuffer[] bufs) throws IOException {
        if (bufs == null)
            throw new NullPointerException();
        synchronized (writeLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected())
                    throw new NotYetConnectedException();
            }
            long n = 0;
            try {
                begin();
                if (!isOpen())
                    return 0;
                writerThread = NativeThread.current();
                do {
                    n = IOUtil.write(fd, bufs, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                writerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException
    {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length))
            throw new IndexOutOfBoundsException();
        // ## Fix IOUtil.write so that we can avoid this array copy
        return write0(Util.subsequence(srcs, offset, length));
    }

    protected void implConfigureBlocking(boolean block) throws IOException {
        IOUtil.configureBlocking(fd, block);
    }

    public SocketAddress localAddress() {
        synchronized (stateLock) {
            return localAddress;
        }
    }

    public SocketAddress remoteAddress() {
        synchronized (stateLock) {
            return remoteAddress;
        }
    }

    @Override
    public DatagramChannel bind(SocketAddress local) throws IOException {
        synchronized (readLock) {
            synchronized (writeLock) {
                synchronized (stateLock) {
                    ensureOpen();
                    if (localAddress != null)
                        throw new AlreadyBoundException();
                    InetSocketAddress isa;
                    if (local == null) {
                        isa = new InetSocketAddress(0);
                    } else {
                        isa = Net.checkAddress(local);

                        // only Inet4Address allowed with IPv4 socket
                        if (family == StandardProtocolFamily.INET) {
                            InetAddress addr = isa.getAddress();
                            if (!(addr instanceof Inet4Address))
                                throw new UnsupportedAddressTypeException();
                        }
                    }
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkListen(isa.getPort());
                    }
                    Net.bind(family, fd, isa.getAddress(), isa.getPort());
                    localAddress = Net.localAddress(fd);
                }
            }
        }
        return this;
    }

    public boolean isConnected() {
        synchronized (stateLock) {
            return (state == ST_CONNECTED);
        }
    }

    void ensureOpenAndUnconnected() throws IOException { // package-private
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (state != ST_UNCONNECTED)
                throw new IllegalStateException("Connect already invoked");
        }
    }

    public DatagramChannel connect(SocketAddress sa) throws IOException {
        int localPort = 0;

        synchronized(readLock) {
            synchronized(writeLock) {
                synchronized (stateLock) {
                    ensureOpenAndUnconnected();
                    InetSocketAddress isa = Net.checkAddress(sa);
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null)
                        sm.checkConnect(isa.getAddress().getHostAddress(),
                                        isa.getPort());
                    int n = Net.connect(family,
                                        fd,
                                        isa.getAddress(),
                                        isa.getPort());
                    if (n <= 0)
                        throw new Error();      // Can't happen

                    // Connection succeeded; disallow further invocation
                    state = ST_CONNECTED;
                    remoteAddress = sa;
                    sender = isa;
                    cachedSenderInetAddress = isa.getAddress();
                    cachedSenderPort = isa.getPort();

                    // Socket was not bound before connecting,
                    if (localAddress == null) {
                        localAddress = Net.localAddress(fd);
                    }
                }
            }
        }
        return this;
    }

    public DatagramChannel disconnect() throws IOException {
        synchronized(readLock) {
            synchronized(writeLock) {
                synchronized (stateLock) {
                    if (!isConnected() || !isOpen())
                        return this;
                    InetSocketAddress isa = (InetSocketAddress)remoteAddress;
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null)
                        sm.checkConnect(isa.getAddress().getHostAddress(),
                                        isa.getPort());
                    disconnect0(fd);
                    remoteAddress = null;
                    state = ST_UNCONNECTED;
                }
            }
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
        if (!(group instanceof Inet4Address)) {
            if (family == StandardProtocolFamily.INET)
                throw new IllegalArgumentException("Group is not IPv4 address");
            if (!(group instanceof Inet6Address))
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
            if (!isOpen())
                throw new ClosedChannelException();

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
            if (family == StandardProtocolFamily.INET6) {
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
        if (source == null)
            throw new NullPointerException("source address is null");
        return innerJoin(group, interf, source);
    }

    // package-private
    void drop(MembershipKeyImpl key) {
        assert key.channel() == this;

        synchronized (stateLock) {
            if (!key.isValid())
                return;

            try {
                if (family == StandardProtocolFamily.INET6) {
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
            if (family == StandardProtocolFamily.INET6) {
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
                if (family == StandardProtocolFamily.INET6) {
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

    protected void implCloseSelectableChannel() throws IOException {
        synchronized (stateLock) {
            nd.preClose(fd);

            // if member of mulitcast group then invalidate all keys
            if (registry != null)
                registry.invalidateAll();

            long th;
            if ((th = readerThread) != 0)
                NativeThread.signal(th);
            if ((th = writerThread) != 0)
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

    protected void finalize() throws IOException {
        // fd is null if constructor threw exception
        if (fd != null)
            close();
    }

    /**
     * Translates native poll revent set into a ready operation set
     */
    public boolean translateReadyOps(int ops, int initialOps,
                                     SelectionKeyImpl sk) {
        int intOps = sk.nioInterestOps(); // Do this just once, it synchronizes
        int oldOps = sk.nioReadyOps();
        int newOps = initialOps;

        if ((ops & PollArrayWrapper.POLLNVAL) != 0) {
            // This should only happen if this channel is pre-closed while a
            // selection operation is in progress
            // ## Throw an error if this channel has not been pre-closed
            return false;
        }

        if ((ops & (PollArrayWrapper.POLLERR
                    | PollArrayWrapper.POLLHUP)) != 0) {
            newOps = intOps;
            sk.nioReadyOps(newOps);
            return (newOps & ~oldOps) != 0;
        }

        if (((ops & PollArrayWrapper.POLLIN) != 0) &&
            ((intOps & SelectionKey.OP_READ) != 0))
            newOps |= SelectionKey.OP_READ;

        if (((ops & PollArrayWrapper.POLLOUT) != 0) &&
            ((intOps & SelectionKey.OP_WRITE) != 0))
            newOps |= SelectionKey.OP_WRITE;

        sk.nioReadyOps(newOps);
        return (newOps & ~oldOps) != 0;
    }

    public boolean translateAndUpdateReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, sk.nioReadyOps(), sk);
    }

    public boolean translateAndSetReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, 0, sk);
    }

    /**
     * Translates an interest operation set into a native poll event set
     */
    public void translateAndSetInterestOps(int ops, SelectionKeyImpl sk) {
        int newOps = 0;

        if ((ops & SelectionKey.OP_READ) != 0)
            newOps |= PollArrayWrapper.POLLIN;
        if ((ops & SelectionKey.OP_WRITE) != 0)
            newOps |= PollArrayWrapper.POLLOUT;
        if ((ops & SelectionKey.OP_CONNECT) != 0)
            newOps |= PollArrayWrapper.POLLIN;
        sk.selector.putEventOps(sk, newOps);
    }

    public FileDescriptor getFD() {
        return fd;
    }

    public int getFDVal() {
        return fdVal;
    }


    // -- Native methods --

    private static native void initIDs();

    private static native void disconnect0(FileDescriptor fd)
        throws IOException;

    private native int receive0(FileDescriptor fd, long address, int len,
                                boolean connected)
        throws IOException;

    private native int send0(boolean preferIPv6, FileDescriptor fd, long address, int len,
                             SocketAddress sa)
        throws IOException;

    static {
        Util.load();
        initIDs();
    }

}
