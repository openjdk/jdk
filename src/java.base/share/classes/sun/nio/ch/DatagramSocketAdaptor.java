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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.util.Objects;
import java.util.Set;


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

    // ## super will create a useless impl
    private DatagramSocketAdaptor(DatagramChannelImpl dc) {
        // Invoke the DatagramSocketAdaptor(SocketAddress) constructor,
        // passing a dummy DatagramSocketImpl object to avoid any native
        // resource allocation in super class and invoking our bind method
        // before the dc field is initialized.
        super(dummyDatagramSocket);
        this.dc = dc;
    }

    static DatagramSocket create(DatagramChannelImpl dc) {
        return new DatagramSocketAdaptor(dc);
    }

    private void connectInternal(SocketAddress remote)
        throws SocketException
    {
        InetSocketAddress isa = Net.asInetSocketAddress(remote);
        int port = isa.getPort();
        if (port < 0 || port > 0xFFFF)
            throw new IllegalArgumentException("connect: " + port);
        if (remote == null)
            throw new IllegalArgumentException("connect: null address");
        try {
            dc.connect(remote);
        } catch (ClosedChannelException e) {
            // ignore
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    @Override
    public void bind(SocketAddress local) throws SocketException {
        try {
            if (local == null)
                local = new InetSocketAddress(0);
            dc.bind(local);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    @Override
    public void connect(InetAddress address, int port) {
        try {
            connectInternal(new InetSocketAddress(address, port));
        } catch (SocketException x) {
            // Yes, j.n.DatagramSocket really does this
        }
    }

    @Override
    public void connect(SocketAddress remote) throws SocketException {
        Objects.requireNonNull(remote, "Address can't be null");
        connectInternal(remote);
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
        return dc.localAddress();
    }

    @Override
    public void send(DatagramPacket p) throws IOException {
        synchronized (dc.blockingLock()) {
            if (!dc.isBlocking())
                throw new IllegalBlockingModeException();
            try {
                synchronized (p) {
                    ByteBuffer bb = ByteBuffer.wrap(p.getData(),
                                                    p.getOffset(),
                                                    p.getLength());
                    if (dc.isConnected()) {
                        if (p.getAddress() == null) {
                            // Legacy DatagramSocket will send in this case
                            // and set address and port of the packet
                            InetSocketAddress isa = dc.remoteAddress();
                            p.setPort(isa.getPort());
                            p.setAddress(isa.getAddress());
                            dc.write(bb);
                        } else {
                            // Target address may not match connected address
                            dc.send(bb, p.getSocketAddress());
                        }
                    } else {
                        // Not connected so address must be valid or throw
                        dc.send(bb, p.getSocketAddress());
                    }
                }
            } catch (IOException x) {
                Net.translateException(x);
            }
        }
    }

    private SocketAddress receive(ByteBuffer bb) throws IOException {
        assert Thread.holdsLock(dc.blockingLock()) && dc.isBlocking();

        long to = this.timeout;
        if (to == 0) {
            return dc.receive(bb);
        } else {
            for (;;) {
                if (!dc.isOpen())
                    throw new ClosedChannelException();
                long st = System.currentTimeMillis();
                if (dc.pollRead(to)) {
                    return dc.receive(bb);
                }
                to -= System.currentTimeMillis() - st;
                if (to <= 0)
                    throw new SocketTimeoutException();
            }
        }
    }

    @Override
    public void receive(DatagramPacket p) throws IOException {
        synchronized (dc.blockingLock()) {
            if (!dc.isBlocking())
                throw new IllegalBlockingModeException();
            try {
                synchronized (p) {
                    ByteBuffer bb = ByteBuffer.wrap(p.getData(),
                                                    p.getOffset(),
                                                    p.getLength());
                    SocketAddress sender = receive(bb);
                    p.setSocketAddress(sender);
                    p.setLength(bb.position() - p.getOffset());
                }
            } catch (IOException x) {
                Net.translateException(x);
            }
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
        try {
            InetSocketAddress local = dc.localAddress();
            if (local != null) {
                return local.getPort();
            }
        } catch (Exception x) {
        }
        return 0;
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        if (!dc.isOpen())
            throw new SocketException("Socket is closed");
        if (timeout < 0)
            throw new IllegalArgumentException("timeout < 0");
        this.timeout = timeout;
    }

    @Override
    public int getSoTimeout() throws SocketException {
        if (!dc.isOpen())
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

   /*
    * A dummy implementation of DatagramSocketImpl that can be passed to the
    * DatagramSocket constructor so that no native resources are allocated in
    * super class.
    */
   private static final DatagramSocketImpl dummyDatagramSocket
       = new DatagramSocketImpl()
   {
       protected void create() throws SocketException {}

       protected void bind(int lport, InetAddress laddr) throws SocketException {}

       protected void send(DatagramPacket p) throws IOException {}

       protected int peek(InetAddress i) throws IOException { return 0; }

       protected int peekData(DatagramPacket p) throws IOException { return 0; }

       protected void receive(DatagramPacket p) throws IOException {}

       @Deprecated
       protected void setTTL(byte ttl) throws IOException {}

       @Deprecated
       protected byte getTTL() throws IOException { return 0; }

       protected void setTimeToLive(int ttl) throws IOException {}

       protected int getTimeToLive() throws IOException { return 0;}

       protected void join(InetAddress inetaddr) throws IOException {}

       protected void leave(InetAddress inetaddr) throws IOException {}

       protected void joinGroup(SocketAddress mcastaddr,
                                 NetworkInterface netIf) throws IOException {}

       protected void leaveGroup(SocketAddress mcastaddr,
                                 NetworkInterface netIf) throws IOException {}

       protected void close() {}

       public Object getOption(int optID) throws SocketException { return null;}

       public void setOption(int optID, Object value) throws SocketException {}
   };
}
