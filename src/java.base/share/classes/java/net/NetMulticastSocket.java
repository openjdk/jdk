/*
 * Copyright (c) 1995, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;

/**
 * A multicast datagram socket that delegates socket operations to a
 * {@link DatagramSocketImpl}.
 *
 * This class overrides every public method defined by {@link DatagramSocket}
 * and {@link MulticastSocket}.
 */
final class NetMulticastSocket extends MulticastSocket {
    /**
     * Various states of this socket.
     */
    private boolean bound = false;
    private boolean closed = false;
    private volatile boolean created;
    private final Object closeLock = new Object();

    /*
     * The implementation of this DatagramSocket.
     */
    private final DatagramSocketImpl impl;

    /**
     * Set when a socket is ST_CONNECTED until we are certain
     * that any packets which might have been received prior
     * to calling connect() but not read by the application
     * have been read. During this time we check the source
     * address of all packets received to be sure they are from
     * the connected destination. Other packets are read but
     * silently dropped.
     */
    private boolean explicitFilter = false;
    private int bytesLeftToFilter;
    /*
     * Connection state:
     * ST_NOT_CONNECTED = socket not connected
     * ST_CONNECTED = socket connected
     */
    static final int ST_NOT_CONNECTED = 0;
    static final int ST_CONNECTED = 1;

    int connectState = ST_NOT_CONNECTED;

    /*
     * Connected address & port
     */
    InetAddress connectedAddress = null;
    int connectedPort = -1;

    /**
     * This constructor is also used by {@link DatagramSocket#DatagramSocket(DatagramSocketImpl)}.
     * @param impl The impl used in this instance.
     */
    NetMulticastSocket(DatagramSocketImpl impl) {
        super((MulticastSocket) null);
        this.impl = Objects.requireNonNull(impl);
    }

    /**
     * Connects this socket to a remote socket address (IP address + port number).
     * Binds socket if not already bound.
     *
     * @param   address The remote address.
     * @param   port    The remote port
     * @throws  SocketException if binding the socket fails.
     */
    private synchronized void connectInternal(InetAddress address, int port) throws SocketException {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("connect: " + port);
        }
        if (address == null) {
            throw new IllegalArgumentException("connect: null address");
        }
        checkAddress(address, "connect");
        if (isClosed())
            return;

        if (port == 0) {
            throw new SocketException("Can't connect to port 0");
        }
        if (!isBound())
            bind(new InetSocketAddress(0));

        getImpl().connect(address, port);

        // socket is now connected by the impl
        connectState = ST_CONNECTED;
        // Do we need to filter some packets?
        int avail = getImpl().dataAvailable();
        if (avail == -1) {
            throw new SocketException();
        }
        explicitFilter = avail > 0;
        if (explicitFilter) {
            bytesLeftToFilter = getReceiveBufferSize();
        }

        connectedAddress = address;
        connectedPort = port;
    }

    /**
     * Return the {@code DatagramSocketImpl} attached to this socket,
     * creating the socket if not already created.
     *
     * @return  the {@code DatagramSocketImpl} attached to that
     *          DatagramSocket
     * @throws SocketException if creating the socket fails
     * @since 1.4
     */
    final DatagramSocketImpl getImpl() throws SocketException {
        if (!created) {
            synchronized (this) {
                if (!created)  {
                    impl.create();
                    created = true;
                }
            }
        }
        return impl;
    }

    @Override
    public synchronized void bind(SocketAddress addr) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (isBound())
            throw new SocketException("already bound");
        if (addr == null)
            addr = new InetSocketAddress(0);
        if (!(addr instanceof InetSocketAddress epoint))
            throw new IllegalArgumentException("Unsupported address type!");
        if (epoint.isUnresolved())
            throw new SocketException("Unresolved address");
        InetAddress iaddr = epoint.getAddress();
        int port = epoint.getPort();
        checkAddress(iaddr, "bind");

        try {
            getImpl().bind(port, iaddr);
        } catch (SocketException e) {
            getImpl().close();
            throw e;
        }
        bound = true;
    }

    static void checkAddress(InetAddress addr, String op) {
        if (addr == null) {
            return;
        }
        if (!(addr instanceof Inet4Address || addr instanceof Inet6Address)) {
            throw new IllegalArgumentException(op + ": invalid address type");
        }
    }

    @Override
    public void connect(InetAddress address, int port) {
        try {
            connectInternal(address, port);
        } catch (SocketException se) {
            throw new UncheckedIOException("connect failed", se);
        }
    }

    @Override
    public void connect(SocketAddress addr) throws SocketException {
        if (addr == null)
            throw new IllegalArgumentException("Address can't be null");
        if (!(addr instanceof InetSocketAddress epoint))
            throw new IllegalArgumentException("Unsupported address type");
        if (epoint.isUnresolved())
            throw new SocketException("Unresolved address");
        connectInternal(epoint.getAddress(), epoint.getPort());
    }

    @Override
    public void disconnect() {
        synchronized (this) {
            if (isClosed())
                return;
            if (connectState == ST_CONNECTED) {
                impl.disconnect();
            }
            connectedAddress = null;
            connectedPort = -1;
            connectState = ST_NOT_CONNECTED;
            explicitFilter = false;
        }
    }

    @Override
    public boolean isBound() {
        return bound;
    }

    @Override
    public boolean isConnected() {
        return connectState != ST_NOT_CONNECTED;
    }

    @Override
    public InetAddress getInetAddress() {
        return connectedAddress;
    }

    @Override
    public int getPort() {
        return connectedPort;
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        if (!isConnected())
            return null;
        return new InetSocketAddress(getInetAddress(), getPort());
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        if (isClosed())
            return null;
        if (!isBound())
            return null;
        return new InetSocketAddress(getLocalAddress(), getLocalPort());
    }

    @Override
    public void send(DatagramPacket p) throws IOException {
        synchronized (p) {
            if (isClosed())
                throw new SocketException("Socket is closed");
            InetAddress packetAddress = p.getAddress();
            int packetPort = p.getPort();
            checkAddress(packetAddress, "send");
            if (connectState == ST_NOT_CONNECTED) {
                if (packetAddress == null) {
                    throw new IllegalArgumentException("Address not set");
                }
                if (packetPort < 0 || packetPort > 0xFFFF)
                    throw new IllegalArgumentException("port out of range: " + packetPort);

                if (packetPort == 0) {
                    throw new SocketException("Can't send to port 0");
                }
            } else {
                // we're connected
                if (packetAddress == null) {
                    p.setAddress(connectedAddress);
                    p.setPort(connectedPort);
                } else if ((!packetAddress.equals(connectedAddress)) ||
                        packetPort != connectedPort) {
                    throw new IllegalArgumentException("connected address " +
                            "and packet address" +
                            " differ");
                }
            }
            // Check whether the socket is bound
            if (!isBound())
                bind(new InetSocketAddress(0));
            // call the  method to send
            getImpl().send(p);
        }
    }

    @Override
    public synchronized void receive(DatagramPacket p) throws IOException {
        synchronized (p) {
            if (!isBound())
                bind(new InetSocketAddress(0));
            DatagramPacket tmp = null;
            // explicitFilter may be set to 'true' at connect() time and will
            // be set to 'false' in disconnect() - or when there's no more
            // pending packets to filter. If explicitFilter is true,
            // it means we're connected.
            if (explicitFilter) {
                assert connectState == ST_CONNECTED;
                // We have to do the filtering the old fashioned way since
                // the native impl doesn't support connect or the connect
                // via the impl failed, or .. "explicitFilter" may be set when
                // a socket is connected via the impl, for a period of time
                // when packets from other sources might be queued on socket.
                boolean stop = false;
                while (!stop) {
                    // peek at the packet to see who it is from.
                    DatagramPacket peekPacket = new DatagramPacket(new byte[1], 1);
                    int peekPort = getImpl().peekData(peekPacket);
                    InetAddress peekAddress = peekPacket.getAddress();
                    if ((!connectedAddress.equals(peekAddress)) || (connectedPort != peekPort)) {
                        // throw the packet away and silently continue
                        tmp = new DatagramPacket(
                                new byte[1024], 1024);
                        getImpl().receive(tmp);
                        if (explicitFilter) {
                            if (checkFiltering(tmp)) {
                                stop = true;
                            }
                        }
                    } else {
                        stop = true;
                    }
                }
            }
            // receive the packet
            getImpl().receive(p);
            if (explicitFilter && tmp == null) {
                // packet was not filtered, account for it here
                checkFiltering(p);
            }
        }
    }

    private boolean checkFiltering(DatagramPacket p) throws SocketException {
        bytesLeftToFilter -= p.getLength();
        if (bytesLeftToFilter <= 0 || getImpl().dataAvailable() <= 0) {
            explicitFilter = false;
            return true;
        }
        return false;
    }

    @Override
    public InetAddress getLocalAddress() {
        if (isClosed())
            return null;
        InetAddress in;
        try {
            in = (InetAddress) getImpl().getOption(SocketOptions.SO_BINDADDR);
            if (in.isAnyLocalAddress()) {
                in = InetAddress.anyLocalAddress();
            }
        } catch (Exception e) {
            in = InetAddress.anyLocalAddress(); // "0.0.0.0"
        }
        return in;
    }

    @Override
    public int getLocalPort() {
        if (isClosed())
            return -1;
        try {
            return getImpl().getLocalPort();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (timeout < 0)
            throw new IllegalArgumentException("timeout < 0");
        getImpl().setOption(SocketOptions.SO_TIMEOUT, timeout);
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (getImpl() == null)
            return 0;
        Object o = getImpl().getOption(SocketOptions.SO_TIMEOUT);
        /* extra type safety */
        if (o instanceof Integer) {
            return ((Integer) o).intValue();
        } else {
            return 0;
        }
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        if (!(size > 0)) {
            throw new IllegalArgumentException("negative send size");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_SNDBUF, size);
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        int result = 0;
        Object o = getImpl().getOption(SocketOptions.SO_SNDBUF);
        if (o instanceof Integer) {
            result = ((Integer) o).intValue();
        }
        return result;
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("invalid receive size");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_RCVBUF, size);
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        int result = 0;
        Object o = getImpl().getOption(SocketOptions.SO_RCVBUF);
        if (o instanceof Integer) {
            result = ((Integer) o).intValue();
        }
        return result;
    }

    @Override
    public synchronized void setReuseAddress(boolean on) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_REUSEADDR, Boolean.valueOf(on));
    }

    @Override
    public synchronized boolean getReuseAddress() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        Object o = getImpl().getOption(SocketOptions.SO_REUSEADDR);
        return ((Boolean) o).booleanValue();
    }

    @Override
    public synchronized void setBroadcast(boolean on) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_BROADCAST, Boolean.valueOf(on));
    }

    @Override
    public synchronized boolean getBroadcast() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return ((Boolean) (getImpl().getOption(SocketOptions.SO_BROADCAST))).booleanValue();
    }

    @Override
    public synchronized void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255)
            throw new IllegalArgumentException("tc is not in range 0 -- 255");

        if (isClosed())
            throw new SocketException("Socket is closed");
        try {
            getImpl().setOption(SocketOptions.IP_TOS, tc);
        } catch (SocketException se) {
            // not supported if socket already connected
            // Solaris returns error in such cases
            if (!isConnected())
                throw se;
        }
    }

    @Override
    public synchronized int getTrafficClass() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return ((Integer) (getImpl().getOption(SocketOptions.IP_TOS))).intValue();
    }

    @Override
    public void close() {
        synchronized (closeLock) {
            if (isClosed())
                return;
            impl.close();
            closed = true;
        }
    }

    @Override
    public boolean isClosed() {
        synchronized (closeLock) {
            return closed;
        }
    }

    @Override
    public  <T> DatagramSocket setOption(SocketOption<T> name, T value)
            throws IOException
    {
        Objects.requireNonNull(name);
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        Objects.requireNonNull(name);
        if (isClosed())
            throw new SocketException("Socket is closed");
        return getImpl().getOption(name);
    }

    private volatile Set<SocketOption<?>> options;
    private final Object optionsLock = new Object();

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        Set<SocketOption<?>> options = this.options;
        if (options != null)
            return options;
        synchronized (optionsLock) {
            options = this.options;
            if (options != null) {
                return options;
            }
            try {
                DatagramSocketImpl impl = getImpl();
                options = Collections.unmodifiableSet(impl.supportedOptions());
            } catch (IOException e) {
                options = Collections.emptySet();
            }
            return this.options = options;
        }
    }

    // Multicast socket support

    /**
     * Used on some platforms to record if an outgoing interface
     * has been set for this socket.
     */
    private boolean interfaceSet;

    /**
     * The lock on the socket's TTL. This is for set/getTTL and
     * send(packet,ttl).
     */
    private final Object ttlLock = new Object();

    /**
     * The lock on the socket's interface - used by setInterface
     * and getInterface
     */
    private final Object infLock = new Object();

    /**
     * The "last" interface set by setInterface on this MulticastSocket
     */
    private InetAddress infAddress = null;

    @Override
    @SuppressWarnings("removal")
    public void setTTL(byte ttl) throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setTTL(ttl);
    }

    @Override
    public void setTimeToLive(int ttl) throws IOException {
        if (ttl < 0 || ttl > 255) {
            throw new IllegalArgumentException("ttl out of range");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setTimeToLive(ttl);
    }

    @Override
    @SuppressWarnings("removal")
    public byte getTTL() throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return getImpl().getTTL();
    }

    @Override
    public int getTimeToLive() throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return getImpl().getTimeToLive();
    }

    @Override
    @Deprecated
    public void joinGroup(InetAddress mcastaddr) throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }

        checkAddress(mcastaddr, "joinGroup");

        if (!mcastaddr.isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        /**
         * required for some platforms where it's not possible to join
         * a group without setting the interface first.
         */
        NetworkInterface defaultInterface = NetworkInterface.getDefault();

        if (!interfaceSet && defaultInterface != null) {
            setNetworkInterface(defaultInterface);
        }

        getImpl().join(mcastaddr);
    }

    @Override
    @Deprecated
    public void leaveGroup(InetAddress mcastaddr) throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }

        checkAddress(mcastaddr, "leaveGroup");

        if (!mcastaddr.isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        getImpl().leave(mcastaddr);
    }

    @Override
    public void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf)
            throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");

        if (!(mcastaddr instanceof InetSocketAddress addr))
            throw new IllegalArgumentException("Unsupported address type");

        checkAddress(addr.getAddress(), "joinGroup");

        if (!addr.getAddress().isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        getImpl().joinGroup(mcastaddr, netIf);
    }

    @Override
    public void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf)
            throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");

        if (!(mcastaddr instanceof InetSocketAddress addr))
            throw new IllegalArgumentException("Unsupported address type");

        checkAddress(addr.getAddress(), "leaveGroup");

        if (!addr.getAddress().isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        getImpl().leaveGroup(mcastaddr, netIf);
    }

    @Override
    @Deprecated
    public void setInterface(InetAddress inf) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        checkAddress(inf, "setInterface");
        synchronized (infLock) {
            getImpl().setOption(SocketOptions.IP_MULTICAST_IF, inf);
            infAddress = inf;
            interfaceSet = true;
        }
    }

    @Override
    @Deprecated
    public InetAddress getInterface() throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        synchronized (infLock) {
            InetAddress ia =
                    (InetAddress)getImpl().getOption(SocketOptions.IP_MULTICAST_IF);

            /**
             * No previous setInterface or interface can be
             * set using setNetworkInterface
             */
            if (infAddress == null) {
                return ia;
            }

            /**
             * Same interface set with setInterface?
             */
            if (ia.equals(infAddress)) {
                return ia;
            }

            /**
             * Different InetAddress from what we set with setInterface
             * so enumerate the current interface to see if the
             * address set by setInterface is bound to this interface.
             */
            try {
                NetworkInterface ni = NetworkInterface.getByInetAddress(ia);
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.equals(infAddress)) {
                        return infAddress;
                    }
                }

                /**
                 * No match so reset infAddress to indicate that the
                 * interface has changed via means
                 */
                infAddress = null;
                return ia;
            } catch (Exception e) {
                return ia;
            }
        }
    }

    @Override
    public void setNetworkInterface(NetworkInterface netIf)
            throws SocketException {

        synchronized (infLock) {
            getImpl().setOption(SocketOptions.IP_MULTICAST_IF2, netIf);
            infAddress = null;
            interfaceSet = true;
        }
    }

    @Override
    public NetworkInterface getNetworkInterface() throws SocketException {
        NetworkInterface ni
                = (NetworkInterface)getImpl().getOption(SocketOptions.IP_MULTICAST_IF2);
        if (ni == null) {
            InetAddress[] addrs = new InetAddress[1];
            addrs[0] = InetAddress.anyLocalAddress();
            return new NetworkInterface(addrs[0].getHostName(), 0, addrs);
        } else {
            return ni;
        }
    }

    @Override
    @Deprecated
    public void setLoopbackMode(boolean disable) throws SocketException {
        getImpl().setOption(SocketOptions.IP_MULTICAST_LOOP, Boolean.valueOf(disable));
    }

    @Override
    @Deprecated
    public boolean getLoopbackMode() throws SocketException {
        return ((Boolean)getImpl().getOption(SocketOptions.IP_MULTICAST_LOOP)).booleanValue();
    }

    @SuppressWarnings("removal")
    @Override
    public void send(DatagramPacket p, byte ttl)
            throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        synchronized(ttlLock) {
            synchronized(p) {
                InetAddress packetAddress = p.getAddress();
                checkAddress(packetAddress, "send");
                if (connectState == ST_NOT_CONNECTED) {
                    if (packetAddress == null) {
                        throw new IllegalArgumentException("Address not set");
                    }
                } else {
                    // we're connected
                    if (packetAddress == null) {
                        p.setAddress(connectedAddress);
                        p.setPort(connectedPort);
                    } else if ((!packetAddress.equals(connectedAddress)) ||
                            p.getPort() != connectedPort) {
                        throw new IllegalArgumentException("connected address and packet address" +
                                " differ");
                    }
                }
                byte dttl = getTTL();
                try {
                    if (ttl != dttl) {
                        // set the ttl
                        getImpl().setTTL(ttl);
                    }
                    if (p.getPort() == 0) {
                        throw new SocketException("Can't send to port 0");
                    }
                    // call the datagram method to send
                    getImpl().send(p);
                } finally {
                    // set it back to default
                    if (ttl != dttl) {
                        getImpl().setTTL(dttl);
                    }
                }
            } // synch p
        }  //synch ttl
    } //method
}
