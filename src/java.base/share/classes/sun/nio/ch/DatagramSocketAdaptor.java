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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Set;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// Make a datagram-socket channel look like a datagram socket.
//
// The methods in this class are defined in exactly the same order as in
// java.net.DatagramSocket so as to simplify tracking future changes to that
// class.
//

class DatagramSocketAdaptor
    extends DatagramSocket
{
    // The channel being adapted
    private final DatagramChannelImpl dc;

    // Timeout "option" value for receives
    private volatile int timeout;

    // create DatagramSocket with useless impl
    private DatagramSocketAdaptor(DatagramChannelImpl dc) {
        super(new DummyDatagramSocketImpl());
        this.dc = dc;
    }

    static DatagramSocket create(DatagramChannelImpl dc) {
        return new DatagramSocketAdaptor(dc);
    }

    private void connectInternal(SocketAddress remote) throws SocketException {
        try {
            dc.connect(remote, false); // skips check for already connected
        } catch (ClosedChannelException e) {
            // ignore
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    @Override
    public void bind(SocketAddress local) throws SocketException {
        if (local != null) {
            local = Net.asInetSocketAddress(local);
        } else {
            local = new InetSocketAddress(0);
        }
        try {
            dc.bind(local);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    @Override
    public void connect(InetAddress address, int port) {
        if (address == null)
            throw new IllegalArgumentException("Address can't be null");
        try {
            connectInternal(new InetSocketAddress(address, port));
        } catch (SocketException x) {
            throw new Error(x);
        }
    }

    @Override
    public void connect(SocketAddress remote) throws SocketException {
        if (remote == null)
            throw new IllegalArgumentException("Address can't be null");
        connectInternal(Net.asInetSocketAddress(remote));
    }

    @Override
    public void disconnect() {
        try {
            dc.disconnect();
        } catch (IOException x) {
            throw new Error(x);
        }
    }

    @Override
    public boolean isBound() {
        return dc.localAddress() != null;
    }

    @Override
    public boolean isConnected() {
        return dc.remoteAddress() != null;
    }

    @Override
    public InetAddress getInetAddress() {
        InetSocketAddress remote = dc.remoteAddress();
        return (remote != null) ? remote.getAddress() : null;
    }

    @Override
    public int getPort() {
        InetSocketAddress remote = dc.remoteAddress();
        return (remote != null) ? remote.getPort() : -1;
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return dc.remoteAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        try {
            return dc.getLocalAddress();
        } catch (ClosedChannelException e) {
            return null;
        } catch (Exception x) {
            throw new Error(x);
        }
    }

    @Override
    public void send(DatagramPacket p) throws IOException {
        ByteBuffer bb = null;
        try {
            InetSocketAddress target;
            synchronized (p) {
                // copy bytes to temporary direct buffer
                int len = p.getLength();
                bb = Util.getTemporaryDirectBuffer(len);
                bb.put(p.getData(), p.getOffset(), len);
                bb.flip();

                // target address
                if (p.getAddress() == null) {
                    InetSocketAddress remote = dc.remoteAddress();
                    if (remote == null) {
                        // not specified by DatagramSocket
                        throw new IllegalArgumentException("Address not set");
                    }
                    // set address/port to maintain compatibility with DatagramSocket
                    p.setAddress(remote.getAddress());
                    p.setPort(remote.getPort());
                    target = remote;
                } else {
                    // throws IllegalArgumentException if port not set
                    target = (InetSocketAddress) p.getSocketAddress();
                }
            }
            // send datagram
            try {
                dc.blockingSend(bb, target);
            } catch (AlreadyConnectedException e) {
                throw new IllegalArgumentException("Connected and packet address differ");
            } catch (ClosedChannelException e) {
                var exc = new SocketException("Socket closed");
                exc.initCause(e);
                throw exc;
            }
        } finally {
            if (bb != null) {
                Util.offerFirstTemporaryDirectBuffer(bb);
            }
        }
    }

    @Override
    public void receive(DatagramPacket p) throws IOException {
        // get temporary direct buffer with a capacity of p.bufLength
        int bufLength = DatagramPackets.getBufLength(p);
        ByteBuffer bb = Util.getTemporaryDirectBuffer(bufLength);
        try {
            long nanos = MILLISECONDS.toNanos(timeout);
            SocketAddress sender = dc.blockingReceive(bb, nanos);
            bb.flip();
            synchronized (p) {
                // copy bytes to the DatagramPacket and set length
                int len = Math.min(bb.limit(), DatagramPackets.getBufLength(p));
                bb.get(p.getData(), p.getOffset(), len);
                DatagramPackets.setLength(p, len);

                // sender address
                p.setSocketAddress(sender);
            }
        } catch (ClosedChannelException e) {
            var exc = new SocketException("Socket closed");
            exc.initCause(e);
            throw exc;
        } finally {
            Util.offerFirstTemporaryDirectBuffer(bb);
        }
    }

    @Override
    public InetAddress getLocalAddress() {
        if (isClosed())
            return null;
        InetSocketAddress local = dc.localAddress();
        if (local == null)
            local = new InetSocketAddress(0);
        InetAddress result = local.getAddress();
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkConnect(result.getHostAddress(), -1);
            } catch (SecurityException x) {
                return new InetSocketAddress(0).getAddress();
            }
        }
        return result;
    }

    @Override
    public int getLocalPort() {
        if (isClosed())
            return -1;
        InetSocketAddress local = dc.localAddress();
        if (local != null) {
            return local.getPort();
        }
        return 0;
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (timeout < 0)
            throw new IllegalArgumentException("timeout < 0");
        this.timeout = timeout;
    }

    @Override
    public int getSoTimeout() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return timeout;
    }

    private void setBooleanOption(SocketOption<Boolean> name, boolean value)
        throws SocketException
    {
        try {
            dc.setOption(name, value);
        } catch (IOException x) {
            Net.translateToSocketException(x);
        }
    }

    private void setIntOption(SocketOption<Integer> name, int value)
        throws SocketException
    {
        try {
            dc.setOption(name, value);
        } catch (IOException x) {
            Net.translateToSocketException(x);
        }
    }

    private boolean getBooleanOption(SocketOption<Boolean> name) throws SocketException {
        try {
            return dc.getOption(name).booleanValue();
        } catch (IOException x) {
            Net.translateToSocketException(x);
            return false;       // keep compiler happy
        }
    }

    private int getIntOption(SocketOption<Integer> name) throws SocketException {
        try {
            return dc.getOption(name).intValue();
        } catch (IOException x) {
            Net.translateToSocketException(x);
            return -1;          // keep compiler happy
        }
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        if (size <= 0)
            throw new IllegalArgumentException("Invalid send size");
        setIntOption(StandardSocketOptions.SO_SNDBUF, size);
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        return getIntOption(StandardSocketOptions.SO_SNDBUF);
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0)
            throw new IllegalArgumentException("Invalid receive size");
        setIntOption(StandardSocketOptions.SO_RCVBUF, size);
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        return getIntOption(StandardSocketOptions.SO_RCVBUF);
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        setBooleanOption(StandardSocketOptions.SO_REUSEADDR, on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return getBooleanOption(StandardSocketOptions.SO_REUSEADDR);
    }

    @Override
    public void setBroadcast(boolean on) throws SocketException {
        setBooleanOption(StandardSocketOptions.SO_BROADCAST, on);
    }

    @Override
    public boolean getBroadcast() throws SocketException {
        return getBooleanOption(StandardSocketOptions.SO_BROADCAST);
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        setIntOption(StandardSocketOptions.IP_TOS, tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return getIntOption(StandardSocketOptions.IP_TOS);
    }

    @Override
    public void close() {
        try {
            dc.close();
        } catch (IOException x) {
            throw new Error(x);
        }
    }

    @Override
    public boolean isClosed() {
        return !dc.isOpen();
    }

    @Override
    public DatagramChannel getChannel() {
        return dc;
    }

    @Override
    public <T> DatagramSocket setOption(SocketOption<T> name, T value) throws IOException {
        dc.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return dc.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return dc.supportedOptions();
    }


    /**
     * DatagramSocketImpl implementation where all methods throw an error.
     */
    private static class DummyDatagramSocketImpl extends DatagramSocketImpl {
        private static <T> T shouldNotGetHere() {
            throw new InternalError("Should not get here");
        }

        @Override
        protected void create() {
            shouldNotGetHere();
        }

        @Override
        protected void bind(int lport, InetAddress laddr) {
            shouldNotGetHere();
        }

        @Override
        protected void send(DatagramPacket p) {
            shouldNotGetHere();
        }

        @Override
        protected int peek(InetAddress address) {
            return shouldNotGetHere();
        }

        @Override
        protected int peekData(DatagramPacket p) {
            return shouldNotGetHere();
        }

        @Override
        protected void receive(DatagramPacket p) {
            shouldNotGetHere();
        }

        @Deprecated
        protected void setTTL(byte ttl) {
            shouldNotGetHere();
        }

        @Deprecated
        protected byte getTTL() {
            return shouldNotGetHere();
        }

        @Override
        protected void setTimeToLive(int ttl) {
            shouldNotGetHere();
        }

        @Override
        protected int getTimeToLive() {
            return shouldNotGetHere();
        }

        @Override
        protected void join(InetAddress group) {
            shouldNotGetHere();
        }

        @Override
        protected void leave(InetAddress inetaddr) {
            shouldNotGetHere();
        }

        @Override
        protected void joinGroup(SocketAddress group, NetworkInterface netIf) {
            shouldNotGetHere();
        }

        @Override
        protected void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf) {
            shouldNotGetHere();
        }

        @Override
        protected void close() {
            shouldNotGetHere();
        }

        @Override
        public Object getOption(int optID) {
            return shouldNotGetHere();
        }

        @Override
        public void setOption(int optID, Object value) {
            shouldNotGetHere();
        }

        @Override
        protected <T> void setOption(SocketOption<T> name, T value) {
            shouldNotGetHere();
        }

        @Override
        protected <T> T getOption(SocketOption<T> name) {
            return shouldNotGetHere();
        }

        @Override
        protected Set<SocketOption<?>> supportedOptions() {
            return shouldNotGetHere();
        }
    }

    /**
     * Defines static methods to get/set DatagramPacket fields and workaround
     * DatagramPacket deficiencies.
     */
    private static class DatagramPackets {
        private static final VarHandle LENGTH;
        private static final VarHandle BUF_LENGTH;
        static {
            try {
                PrivilegedAction<Lookup> pa = () -> {
                    try {
                        return MethodHandles.privateLookupIn(DatagramPacket.class, MethodHandles.lookup());
                    } catch (Exception e) {
                        throw new ExceptionInInitializerError(e);
                    }
                };
                MethodHandles.Lookup l = AccessController.doPrivileged(pa);
                LENGTH = l.findVarHandle(DatagramPacket.class, "length", int.class);
                BUF_LENGTH = l.findVarHandle(DatagramPacket.class, "bufLength", int.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        /**
         * Sets the DatagramPacket.length field. DatagramPacket.setLength cannot be
         * used at this time because it sets both the length and bufLength fields.
         */
        static void setLength(DatagramPacket p, int value) {
            synchronized (p) {
                LENGTH.set(p, value);
            }
        }

        /**
         * Returns the value of the DatagramPacket.bufLength field.
         */
        static int getBufLength(DatagramPacket p) {
            synchronized (p) {
                return (int) BUF_LENGTH.get(p);
            }
        }
    }
}