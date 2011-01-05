/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;


// Make a datagram-socket channel look like a datagram socket.
//
// The methods in this class are defined in exactly the same order as in
// java.net.DatagramSocket so as to simplify tracking future changes to that
// class.
//

public class DatagramSocketAdaptor
    extends DatagramSocket
{

    // The channel being adapted
    private final DatagramChannelImpl dc;

    // Timeout "option" value for receives
    private volatile int timeout = 0;

    // ## super will create a useless impl
    private DatagramSocketAdaptor(DatagramChannelImpl dc) throws IOException {
        // Invoke the DatagramSocketAdaptor(SocketAddress) constructor,
        // passing a dummy DatagramSocketImpl object to aovid any native
        // resource allocation in super class and invoking our bind method
        // before the dc field is initialized.
        super(dummyDatagramSocket);
        this.dc = dc;
    }

    public static DatagramSocket create(DatagramChannelImpl dc) {
        try {
            return new DatagramSocketAdaptor(dc);
        } catch (IOException x) {
            throw new Error(x);
        }
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
        if (isClosed())
            return;
        try {
            dc.connect(remote);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public void bind(SocketAddress local) throws SocketException {
        try {
            if (local == null)
                local = new InetSocketAddress(0);
            dc.bind(local);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public void connect(InetAddress address, int port) {
        try {
            connectInternal(new InetSocketAddress(address, port));
        } catch (SocketException x) {
            // Yes, j.n.DatagramSocket really does this
        }
    }

    public void connect(SocketAddress remote) throws SocketException {
        if (remote == null)
            throw new IllegalArgumentException("Address can't be null");
        connectInternal(remote);
    }

    public void disconnect() {
        try {
            dc.disconnect();
        } catch (IOException x) {
            throw new Error(x);
        }
    }

    public boolean isBound() {
        return dc.localAddress() != null;
    }

    public boolean isConnected() {
        return dc.remoteAddress() != null;
    }

    public InetAddress getInetAddress() {
        return (isConnected()
                ? Net.asInetSocketAddress(dc.remoteAddress()).getAddress()
                : null);
    }

    public int getPort() {
        return (isConnected()
                ? Net.asInetSocketAddress(dc.remoteAddress()).getPort()
                : -1);
    }

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
                            InetSocketAddress isa = (InetSocketAddress)
                                                    dc.remoteAddress();
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

    // Must hold dc.blockingLock()
    //
    private SocketAddress receive(ByteBuffer bb) throws IOException {
        if (timeout == 0) {
            return dc.receive(bb);
        }

        // Implement timeout with a selector
        SelectionKey sk = null;
        Selector sel = null;
        dc.configureBlocking(false);
        try {
            int n;
            SocketAddress sender;
            if ((sender = dc.receive(bb)) != null)
                return sender;
            sel = Util.getTemporarySelector(dc);
            sk = dc.register(sel, SelectionKey.OP_READ);
            long to = timeout;
            for (;;) {
                if (!dc.isOpen())
                     throw new ClosedChannelException();
                long st = System.currentTimeMillis();
                int ns = sel.select(to);
                if (ns > 0 && sk.isReadable()) {
                    if ((sender = dc.receive(bb)) != null)
                        return sender;
                }
                sel.selectedKeys().remove(sk);
                to -= System.currentTimeMillis() - st;
                if (to <= 0)
                    throw new SocketTimeoutException();

            }
        } finally {
            if (sk != null)
                sk.cancel();
            if (dc.isOpen())
                dc.configureBlocking(true);
            if (sel != null)
                Util.releaseTemporarySelector(sel);
        }
    }

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

    public InetAddress getLocalAddress() {
        if (isClosed())
            return null;
        SocketAddress local = dc.localAddress();
        if (local == null)
            local = new InetSocketAddress(0);
        InetAddress result = ((InetSocketAddress)local).getAddress();
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

    public int getLocalPort() {
        if (isClosed())
            return -1;
        try {
            SocketAddress local = dc.getLocalAddress();
            if (local != null) {
                return ((InetSocketAddress)local).getPort();
            }
        } catch (Exception x) {
        }
        return 0;
    }

    public void setSoTimeout(int timeout) throws SocketException {
        this.timeout = timeout;
    }

    public int getSoTimeout() throws SocketException {
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

    public void setSendBufferSize(int size) throws SocketException {
        if (size <= 0)
            throw new IllegalArgumentException("Invalid send size");
        setIntOption(StandardSocketOption.SO_SNDBUF, size);
    }

    public int getSendBufferSize() throws SocketException {
        return getIntOption(StandardSocketOption.SO_SNDBUF);
    }

    public void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0)
            throw new IllegalArgumentException("Invalid receive size");
        setIntOption(StandardSocketOption.SO_RCVBUF, size);
    }

    public int getReceiveBufferSize() throws SocketException {
        return getIntOption(StandardSocketOption.SO_RCVBUF);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        setBooleanOption(StandardSocketOption.SO_REUSEADDR, on);
    }

    public boolean getReuseAddress() throws SocketException {
        return getBooleanOption(StandardSocketOption.SO_REUSEADDR);

    }

    public void setBroadcast(boolean on) throws SocketException {
        setBooleanOption(StandardSocketOption.SO_BROADCAST, on);
    }

    public boolean getBroadcast() throws SocketException {
        return getBooleanOption(StandardSocketOption.SO_BROADCAST);
    }

    public void setTrafficClass(int tc) throws SocketException {
        setIntOption(StandardSocketOption.IP_TOS, tc);
    }

    public int getTrafficClass() throws SocketException {
        return getIntOption(StandardSocketOption.IP_TOS);
    }

    public void close() {
        try {
            dc.close();
        } catch (IOException x) {
            throw new Error(x);
        }
    }

    public boolean isClosed() {
        return !dc.isOpen();
    }

    public DatagramChannel getChannel() {
        return dc;
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

       protected void setTTL(byte ttl) throws IOException {}

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
