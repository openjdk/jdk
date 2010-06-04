/*
 * Copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.DatagramChannel;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * This class represents a socket for sending and receiving datagram packets.
 *
 * <p>A datagram socket is the sending or receiving point for a packet
 * delivery service. Each packet sent or received on a datagram socket
 * is individually addressed and routed. Multiple packets sent from
 * one machine to another may be routed differently, and may arrive in
 * any order.
 *
 * <p> Where possible, a newly constructed {@code DatagramSocket} has the
 * {@link SocketOptions#SO_BROADCAST SO_BROADCAST} socket option enabled so as
 * to allow the transmission of broadcast datagrams. In order to receive
 * broadcast packets a DatagramSocket should be bound to the wildcard address.
 * In some implementations, broadcast packets may also be received when
 * a DatagramSocket is bound to a more specific address.
 * <p>
 * Example:
 * <code>
 *              DatagramSocket s = new DatagramSocket(null);
 *              s.bind(new InetSocketAddress(8888));
 * </code>
 * Which is equivalent to:
 * <code>
 *              DatagramSocket s = new DatagramSocket(8888);
 * </code>
 * Both cases will create a DatagramSocket able to receive broadcasts on
 * UDP port 8888.
 *
 * @author  Pavani Diwanji
 * @see     java.net.DatagramPacket
 * @see     java.nio.channels.DatagramChannel
 * @since JDK1.0
 */
public
class DatagramSocket implements java.io.Closeable {
    /**
     * Various states of this socket.
     */
    private boolean created = false;
    private boolean bound = false;
    private boolean closed = false;
    private Object closeLock = new Object();

    /*
     * The implementation of this DatagramSocket.
     */
    DatagramSocketImpl impl;

    /**
     * Are we using an older DatagramSocketImpl?
     */
    boolean oldImpl = false;

    /*
     * Connection state:
     * ST_NOT_CONNECTED = socket not connected
     * ST_CONNECTED = socket connected
     * ST_CONNECTED_NO_IMPL = socket connected but not at impl level
     */
    static final int ST_NOT_CONNECTED = 0;
    static final int ST_CONNECTED = 1;
    static final int ST_CONNECTED_NO_IMPL = 2;

    int connectState = ST_NOT_CONNECTED;

    /*
     * Connected address & port
     */
    InetAddress connectedAddress = null;
    int connectedPort = -1;

    /**
     * Connects this socket to a remote socket address (IP address + port number).
     * Binds socket if not already bound.
     * <p>
     * @param   addr    The remote address.
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
        checkAddress (address, "connect");
        if (isClosed())
            return;
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            if (address.isMulticastAddress()) {
                security.checkMulticast(address);
            } else {
                security.checkConnect(address.getHostAddress(), port);
                security.checkAccept(address.getHostAddress(), port);
            }
        }

        if (!isBound())
          bind(new InetSocketAddress(0));

        // old impls do not support connect/disconnect
        if (oldImpl) {
            connectState = ST_CONNECTED_NO_IMPL;
        } else {
            try {
                getImpl().connect(address, port);

                // socket is now connected by the impl
                connectState = ST_CONNECTED;
            } catch (SocketException se) {

                // connection will be emulated by DatagramSocket
                connectState = ST_CONNECTED_NO_IMPL;
            }
        }

        connectedAddress = address;
        connectedPort = port;
    }


    /**
     * Constructs a datagram socket and binds it to any available port
     * on the local host machine.  The socket will be bound to the
     * {@link InetAddress#isAnyLocalAddress wildcard} address,
     * an IP address chosen by the kernel.
     *
     * <p>If there is a security manager,
     * its <code>checkListen</code> method is first called
     * with 0 as its argument to ensure the operation is allowed.
     * This could result in a SecurityException.
     *
     * @exception  SocketException  if the socket could not be opened,
     *               or the socket could not bind to the specified local port.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkListen</code> method doesn't allow the operation.
     *
     * @see SecurityManager#checkListen
     */
    public DatagramSocket() throws SocketException {
        // create a datagram socket.
        createImpl();
        try {
            bind(new InetSocketAddress(0));
        } catch (SocketException se) {
            throw se;
        } catch(IOException e) {
            throw new SocketException(e.getMessage());
        }
    }

    /**
     * Creates an unbound datagram socket with the specified
     * DatagramSocketImpl.
     *
     * @param impl an instance of a <B>DatagramSocketImpl</B>
     *        the subclass wishes to use on the DatagramSocket.
     * @since   1.4
     */
    protected DatagramSocket(DatagramSocketImpl impl) {
        if (impl == null)
            throw new NullPointerException();
        this.impl = impl;
        checkOldImpl();
    }

    /**
     * Creates a datagram socket, bound to the specified local
     * socket address.
     * <p>
     * If, if the address is <code>null</code>, creates an unbound socket.
     * <p>
     * <p>If there is a security manager,
     * its <code>checkListen</code> method is first called
     * with the port from the socket address
     * as its argument to ensure the operation is allowed.
     * This could result in a SecurityException.
     *
     * @param bindaddr local socket address to bind, or <code>null</code>
     *                 for an unbound socket.
     *
     * @exception  SocketException  if the socket could not be opened,
     *               or the socket could not bind to the specified local port.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkListen</code> method doesn't allow the operation.
     *
     * @see SecurityManager#checkListen
     * @since   1.4
     */
    public DatagramSocket(SocketAddress bindaddr) throws SocketException {
        // create a datagram socket.
        createImpl();
        if (bindaddr != null) {
            bind(bindaddr);
        }
    }

    /**
     * Constructs a datagram socket and binds it to the specified port
     * on the local host machine.  The socket will be bound to the
     * {@link InetAddress#isAnyLocalAddress wildcard} address,
     * an IP address chosen by the kernel.
     *
     * <p>If there is a security manager,
     * its <code>checkListen</code> method is first called
     * with the <code>port</code> argument
     * as its argument to ensure the operation is allowed.
     * This could result in a SecurityException.
     *
     * @param      port port to use.
     * @exception  SocketException  if the socket could not be opened,
     *               or the socket could not bind to the specified local port.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkListen</code> method doesn't allow the operation.
     *
     * @see SecurityManager#checkListen
     */
    public DatagramSocket(int port) throws SocketException {
        this(port, null);
    }

    /**
     * Creates a datagram socket, bound to the specified local
     * address.  The local port must be between 0 and 65535 inclusive.
     * If the IP address is 0.0.0.0, the socket will be bound to the
     * {@link InetAddress#isAnyLocalAddress wildcard} address,
     * an IP address chosen by the kernel.
     *
     * <p>If there is a security manager,
     * its <code>checkListen</code> method is first called
     * with the <code>port</code> argument
     * as its argument to ensure the operation is allowed.
     * This could result in a SecurityException.
     *
     * @param port local port to use
     * @param laddr local address to bind
     *
     * @exception  SocketException  if the socket could not be opened,
     *               or the socket could not bind to the specified local port.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkListen</code> method doesn't allow the operation.
     *
     * @see SecurityManager#checkListen
     * @since   JDK1.1
     */
    public DatagramSocket(int port, InetAddress laddr) throws SocketException {
        this(new InetSocketAddress(laddr, port));
    }

    private void checkOldImpl() {
        if (impl == null)
            return;
        // DatagramSocketImpl.peekdata() is a protected method, therefore we need to use
        // getDeclaredMethod, therefore we need permission to access the member
        try {
            AccessController.doPrivileged(
                new PrivilegedExceptionAction<Void>() {
                    public Void run() throws NoSuchMethodException {
                        Class[] cl = new Class[1];
                        cl[0] = DatagramPacket.class;
                        impl.getClass().getDeclaredMethod("peekData", cl);
                        return null;
                    }
                });
        } catch (java.security.PrivilegedActionException e) {
            oldImpl = true;
        }
    }

    static Class implClass = null;

    void createImpl() throws SocketException {
        if (impl == null) {
            if (factory != null) {
                impl = factory.createDatagramSocketImpl();
                checkOldImpl();
            } else {
                boolean isMulticast = (this instanceof MulticastSocket) ? true : false;
                impl = DefaultDatagramSocketImplFactory.createDatagramSocketImpl(isMulticast);

                checkOldImpl();
            }
        }
        // creates a udp socket
        impl.create();
        created = true;
    }

    /**
     * Get the <code>DatagramSocketImpl</code> attached to this socket,
     * creating it if necessary.
     *
     * @return  the <code>DatagramSocketImpl</code> attached to that
     *          DatagramSocket
     * @throws SocketException if creation fails.
     * @since 1.4
     */
    DatagramSocketImpl getImpl() throws SocketException {
        if (!created)
            createImpl();
        return impl;
    }

    /**
     * Binds this DatagramSocket to a specific address & port.
     * <p>
     * If the address is <code>null</code>, then the system will pick up
     * an ephemeral port and a valid local address to bind the socket.
     *<p>
     * @param   addr The address & port to bind to.
     * @throws  SocketException if any error happens during the bind, or if the
     *          socket is already bound.
     * @throws  SecurityException  if a security manager exists and its
     *             <code>checkListen</code> method doesn't allow the operation.
     * @throws IllegalArgumentException if addr is a SocketAddress subclass
     *         not supported by this socket.
     * @since 1.4
     */
    public synchronized void bind(SocketAddress addr) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (isBound())
            throw new SocketException("already bound");
        if (addr == null)
            addr = new InetSocketAddress(0);
        if (!(addr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type!");
        InetSocketAddress epoint = (InetSocketAddress) addr;
        if (epoint.isUnresolved())
            throw new SocketException("Unresolved address");
        InetAddress iaddr = epoint.getAddress();
        int port = epoint.getPort();
        checkAddress(iaddr, "bind");
        SecurityManager sec = System.getSecurityManager();
        if (sec != null) {
            sec.checkListen(port);
        }
        try {
            getImpl().bind(port, iaddr);
        } catch (SocketException e) {
            getImpl().close();
            throw e;
        }
        bound = true;
    }

    void checkAddress (InetAddress addr, String op) {
        if (addr == null) {
            return;
        }
        if (!(addr instanceof Inet4Address || addr instanceof Inet6Address)) {
            throw new IllegalArgumentException(op + ": invalid address type");
        }
    }

    /**
     * Connects the socket to a remote address for this socket. When a
     * socket is connected to a remote address, packets may only be
     * sent to or received from that address. By default a datagram
     * socket is not connected.
     *
     * <p>If the remote destination to which the socket is connected does not
     * exist, or is otherwise unreachable, and if an ICMP destination unreachable
     * packet has been received for that address, then a subsequent call to
     * send or receive may throw a PortUnreachableException. Note, there is no
     * guarantee that the exception will be thrown.
     *
     * <p> If a security manager has been installed then it is invoked to check
     * access to the remote address. Specifically, if the given {@code address}
     * is a {@link InetAddress#isMulticastAddress multicast address},
     * the security manager's {@link
     * java.lang.SecurityManager#checkMulticast(InetAddress)
     * checkMulticast} method is invoked with the given {@code address}.
     * Otherwise, the security manager's {@link
     * java.lang.SecurityManager#checkConnect(String,int) checkConnect}
     * and {@link java.lang.SecurityManager#checkAccept checkAccept} methods
     * are invoked, with the given {@code address} and {@code port}, to
     * verify that datagrams are permitted to be sent and received
     * respectively.
     *
     * <p> When a socket is connected, {@link #receive receive} and
     * {@link #send send} <b>will not perform any security checks</b>
     * on incoming and outgoing packets, other than matching the packet's
     * and the socket's address and port. On a send operation, if the
     * packet's address is set and the packet's address and the socket's
     * address do not match, an {@code IllegalArgumentException} will be
     * thrown. A socket connected to a multicast address may only be used
     * to send packets.
     *
     * @param address the remote address for the socket
     *
     * @param port the remote port for the socket.
     *
     * @throws IllegalArgumentException
     *         if the address is null, or the port is out of range.
     *
     * @throws SecurityException
     *         if a security manager has been installed and it does
     *         not permit access to the given remote address
     *
     * @see #disconnect
     */
    public void connect(InetAddress address, int port) {
        try {
            connectInternal(address, port);
        } catch (SocketException se) {
            throw new Error("connect failed", se);
        }
    }

    /**
     * Connects this socket to a remote socket address (IP address + port number).
     *
     * <p> If given an {@link InetSocketAddress InetSocketAddress}, this method
     * behaves as if invoking {@link #connect(InetAddress,int) connect(InetAddress,int)}
     * with the the given socket addresses IP address and port number.
     *
     * @param   addr    The remote address.
     *
     * @throws  SocketException
     *          if the connect fails
     *
     * @throws IllegalArgumentException
     *         if {@code addr} is {@code null}, or {@code addr} is a SocketAddress
     *         subclass not supported by this socket
     *
     * @throws SecurityException
     *         if a security manager has been installed and it does
     *         not permit access to the given remote address
     *
     * @since 1.4
     */
    public void connect(SocketAddress addr) throws SocketException {
        if (addr == null)
            throw new IllegalArgumentException("Address can't be null");
        if (!(addr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");
        InetSocketAddress epoint = (InetSocketAddress) addr;
        if (epoint.isUnresolved())
            throw new SocketException("Unresolved address");
        connectInternal(epoint.getAddress(), epoint.getPort());
    }

    /**
     * Disconnects the socket. If the socket is closed or not connected,
     * then this method has no effect.
     *
     * @see #connect
     */
    public void disconnect() {
        synchronized (this) {
            if (isClosed())
                return;
            if (connectState == ST_CONNECTED) {
                impl.disconnect ();
            }
            connectedAddress = null;
            connectedPort = -1;
            connectState = ST_NOT_CONNECTED;
        }
    }

    /**
     * Returns the binding state of the socket.
     * <p>
     * If the socket was bound prior to being {@link #close closed},
     * then this method will continue to return <code>true</code>
     * after the socket is closed.
     *
     * @return true if the socket successfully bound to an address
     * @since 1.4
     */
    public boolean isBound() {
        return bound;
    }

    /**
     * Returns the connection state of the socket.
     * <p>
     * If the socket was connected prior to being {@link #close closed},
     * then this method will continue to return <code>true</code>
     * after the socket is closed.
     *
     * @return true if the socket successfully connected to a server
     * @since 1.4
     */
    public boolean isConnected() {
        return connectState != ST_NOT_CONNECTED;
    }

    /**
     * Returns the address to which this socket is connected. Returns
     * <code>null</code> if the socket is not connected.
     * <p>
     * If the socket was connected prior to being {@link #close closed},
     * then this method will continue to return the connected address
     * after the socket is closed.
     *
     * @return the address to which this socket is connected.
     */
    public InetAddress getInetAddress() {
        return connectedAddress;
    }

    /**
     * Returns the port number to which this socket is connected.
     * Returns <code>-1</code> if the socket is not connected.
     * <p>
     * If the socket was connected prior to being {@link #close closed},
     * then this method will continue to return the connected port number
     * after the socket is closed.
     *
     * @return the port number to which this socket is connected.
     */
    public int getPort() {
        return connectedPort;
    }

    /**
     * Returns the address of the endpoint this socket is connected to, or
     * <code>null</code> if it is unconnected.
     * <p>
     * If the socket was connected prior to being {@link #close closed},
     * then this method will continue to return the connected address
     * after the socket is closed.
     *
     * @return a <code>SocketAddress</code> representing the remote
     *         endpoint of this socket, or <code>null</code> if it is
     *         not connected yet.
     * @see #getInetAddress()
     * @see #getPort()
     * @see #connect(SocketAddress)
     * @since 1.4
     */
    public SocketAddress getRemoteSocketAddress() {
        if (!isConnected())
            return null;
        return new InetSocketAddress(getInetAddress(), getPort());
    }

    /**
     * Returns the address of the endpoint this socket is bound to.
     *
     * @return a <code>SocketAddress</code> representing the local endpoint of this
     *         socket, or <code>null</code> if it is closed or not bound yet.
     * @see #getLocalAddress()
     * @see #getLocalPort()
     * @see #bind(SocketAddress)
     * @since 1.4
     */

    public SocketAddress getLocalSocketAddress() {
        if (isClosed())
            return null;
        if (!isBound())
            return null;
        return new InetSocketAddress(getLocalAddress(), getLocalPort());
    }

    /**
     * Sends a datagram packet from this socket. The
     * <code>DatagramPacket</code> includes information indicating the
     * data to be sent, its length, the IP address of the remote host,
     * and the port number on the remote host.
     *
     * <p>If there is a security manager, and the socket is not currently
     * connected to a remote address, this method first performs some
     * security checks. First, if <code>p.getAddress().isMulticastAddress()</code>
     * is true, this method calls the
     * security manager's <code>checkMulticast</code> method
     * with <code>p.getAddress()</code> as its argument.
     * If the evaluation of that expression is false,
     * this method instead calls the security manager's
     * <code>checkConnect</code> method with arguments
     * <code>p.getAddress().getHostAddress()</code> and
     * <code>p.getPort()</code>. Each call to a security manager method
     * could result in a SecurityException if the operation is not allowed.
     *
     * @param      p   the <code>DatagramPacket</code> to be sent.
     *
     * @exception  IOException  if an I/O error occurs.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkMulticast</code> or <code>checkConnect</code>
     *             method doesn't allow the send.
     * @exception  PortUnreachableException may be thrown if the socket is connected
     *             to a currently unreachable destination. Note, there is no
     *             guarantee that the exception will be thrown.
     * @exception  java.nio.channels.IllegalBlockingModeException
     *             if this socket has an associated channel,
     *             and the channel is in non-blocking mode.
     * @exception  IllegalArgumentException if the socket is connected,
     *             and connected address and packet address differ.
     *
     * @see        java.net.DatagramPacket
     * @see        SecurityManager#checkMulticast(InetAddress)
     * @see        SecurityManager#checkConnect
     * @revised 1.4
     * @spec JSR-51
     */
    public void send(DatagramPacket p) throws IOException  {
        InetAddress packetAddress = null;
        synchronized (p) {
            if (isClosed())
                throw new SocketException("Socket is closed");
            checkAddress (p.getAddress(), "send");
            if (connectState == ST_NOT_CONNECTED) {
                // check the address is ok wiht the security manager on every send.
                SecurityManager security = System.getSecurityManager();

                // The reason you want to synchronize on datagram packet
                // is because you dont want an applet to change the address
                // while you are trying to send the packet for example
                // after the security check but before the send.
                if (security != null) {
                    if (p.getAddress().isMulticastAddress()) {
                        security.checkMulticast(p.getAddress());
                    } else {
                        security.checkConnect(p.getAddress().getHostAddress(),
                                              p.getPort());
                    }
                }
            } else {
                // we're connected
                packetAddress = p.getAddress();
                if (packetAddress == null) {
                    p.setAddress(connectedAddress);
                    p.setPort(connectedPort);
                } else if ((!packetAddress.equals(connectedAddress)) ||
                           p.getPort() != connectedPort) {
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

    /**
     * Receives a datagram packet from this socket. When this method
     * returns, the <code>DatagramPacket</code>'s buffer is filled with
     * the data received. The datagram packet also contains the sender's
     * IP address, and the port number on the sender's machine.
     * <p>
     * This method blocks until a datagram is received. The
     * <code>length</code> field of the datagram packet object contains
     * the length of the received message. If the message is longer than
     * the packet's length, the message is truncated.
     * <p>
     * If there is a security manager, a packet cannot be received if the
     * security manager's <code>checkAccept</code> method
     * does not allow it.
     *
     * @param      p   the <code>DatagramPacket</code> into which to place
     *                 the incoming data.
     * @exception  IOException  if an I/O error occurs.
     * @exception  SocketTimeoutException  if setSoTimeout was previously called
     *                 and the timeout has expired.
     * @exception  PortUnreachableException may be thrown if the socket is connected
     *             to a currently unreachable destination. Note, there is no guarantee that the
     *             exception will be thrown.
     * @exception  java.nio.channels.IllegalBlockingModeException
     *             if this socket has an associated channel,
     *             and the channel is in non-blocking mode.
     * @see        java.net.DatagramPacket
     * @see        java.net.DatagramSocket
     * @revised 1.4
     * @spec JSR-51
     */
    public synchronized void receive(DatagramPacket p) throws IOException {
        synchronized (p) {
            if (!isBound())
                bind(new InetSocketAddress(0));
            if (connectState == ST_NOT_CONNECTED) {
                // check the address is ok with the security manager before every recv.
                SecurityManager security = System.getSecurityManager();
                if (security != null) {
                    while(true) {
                        String peekAd = null;
                        int peekPort = 0;
                        // peek at the packet to see who it is from.
                        if (!oldImpl) {
                            // We can use the new peekData() API
                            DatagramPacket peekPacket = new DatagramPacket(new byte[1], 1);
                            peekPort = getImpl().peekData(peekPacket);
                            peekAd = peekPacket.getAddress().getHostAddress();
                        } else {
                            InetAddress adr = new InetAddress();
                            peekPort = getImpl().peek(adr);
                            peekAd = adr.getHostAddress();
                        }
                        try {
                            security.checkAccept(peekAd, peekPort);
                            // security check succeeded - so now break
                            // and recv the packet.
                            break;
                        } catch (SecurityException se) {
                            // Throw away the offending packet by consuming
                            // it in a tmp buffer.
                            DatagramPacket tmp = new DatagramPacket(new byte[1], 1);
                            getImpl().receive(tmp);

                            // silently discard the offending packet
                            // and continue: unknown/malicious
                            // entities on nets should not make
                            // runtime throw security exception and
                            // disrupt the applet by sending random
                            // datagram packets.
                            continue;
                        }
                    } // end of while
                }
            }
            if (connectState == ST_CONNECTED_NO_IMPL) {
                // We have to do the filtering the old fashioned way since
                // the native impl doesn't support connect or the connect
                // via the impl failed.
                boolean stop = false;
                while (!stop) {
                    // peek at the packet to see who it is from.
                    InetAddress peekAddress = new InetAddress();
                    int peekPort = getImpl().peek(peekAddress);
                    if ((!connectedAddress.equals(peekAddress)) ||
                        (connectedPort != peekPort)) {
                        // throw the packet away and silently continue
                        DatagramPacket tmp = new DatagramPacket(new byte[1], 1);
                        getImpl().receive(tmp);
                    } else {
                        stop = true;
                    }
                }
            }
            // If the security check succeeds, or the datagram is
            // connected then receive the packet
            getImpl().receive(p);
        }
    }

    /**
     * Gets the local address to which the socket is bound.
     *
     * <p>If there is a security manager, its
     * <code>checkConnect</code> method is first called
     * with the host address and <code>-1</code>
     * as its arguments to see if the operation is allowed.
     *
     * @see SecurityManager#checkConnect
     * @return  the local address to which the socket is bound,
     *          <code>null</code> if the socket is closed, or
     *          an <code>InetAddress</code> representing
     *          {@link InetAddress#isAnyLocalAddress wildcard}
     *          address if either the socket is not bound, or
     *          the security manager <code>checkConnect</code>
     *          method does not allow the operation
     * @since   1.1
     */
    public InetAddress getLocalAddress() {
        if (isClosed())
            return null;
        InetAddress in = null;
        try {
            in = (InetAddress) getImpl().getOption(SocketOptions.SO_BINDADDR);
            if (in.isAnyLocalAddress()) {
                in = InetAddress.anyLocalAddress();
            }
            SecurityManager s = System.getSecurityManager();
            if (s != null) {
                s.checkConnect(in.getHostAddress(), -1);
            }
        } catch (Exception e) {
            in = InetAddress.anyLocalAddress(); // "0.0.0.0"
        }
        return in;
    }

    /**
     * Returns the port number on the local host to which this socket
     * is bound.
     *
     * @return  the port number on the local host to which this socket is bound,
                <code>-1</code> if the socket is closed, or
                <code>0</code> if it is not bound yet.
     */
    public int getLocalPort() {
        if (isClosed())
            return -1;
        try {
            return getImpl().getLocalPort();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Enable/disable SO_TIMEOUT with the specified timeout, in
     *  milliseconds. With this option set to a non-zero timeout,
     *  a call to receive() for this DatagramSocket
     *  will block for only this amount of time.  If the timeout expires,
     *  a <B>java.net.SocketTimeoutException</B> is raised, though the
     *  DatagramSocket is still valid.  The option <B>must</B> be enabled
     *  prior to entering the blocking operation to have effect.  The
     *  timeout must be > 0.
     *  A timeout of zero is interpreted as an infinite timeout.
     *
     * @param timeout the specified timeout in milliseconds.
     * @throws SocketException if there is an error in the underlying protocol, such as an UDP error.
     * @since   JDK1.1
     * @see #getSoTimeout()
     */
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_TIMEOUT, new Integer(timeout));
    }

    /**
     * Retrieve setting for SO_TIMEOUT.  0 returns implies that the
     * option is disabled (i.e., timeout of infinity).
     *
     * @return the setting for SO_TIMEOUT
     * @throws SocketException if there is an error in the underlying protocol, such as an UDP error.
     * @since   JDK1.1
     * @see #setSoTimeout(int)
     */
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

    /**
     * Sets the SO_SNDBUF option to the specified value for this
     * <tt>DatagramSocket</tt>. The SO_SNDBUF option is used by the
     * network implementation as a hint to size the underlying
     * network I/O buffers. The SO_SNDBUF setting may also be used
     * by the network implementation to determine the maximum size
     * of the packet that can be sent on this socket.
     * <p>
     * As SO_SNDBUF is a hint, applications that want to verify
     * what size the buffer is should call {@link #getSendBufferSize()}.
     * <p>
     * Increasing the buffer size may allow multiple outgoing packets
     * to be queued by the network implementation when the send rate
     * is high.
     * <p>
     * Note: If {@link #send(DatagramPacket)} is used to send a
     * <code>DatagramPacket</code> that is larger than the setting
     * of SO_SNDBUF then it is implementation specific if the
     * packet is sent or discarded.
     *
     * @param size the size to which to set the send buffer
     * size. This value must be greater than 0.
     *
     * @exception SocketException if there is an error
     * in the underlying protocol, such as an UDP error.
     * @exception IllegalArgumentException if the value is 0 or is
     * negative.
     * @see #getSendBufferSize()
     */
    public synchronized void setSendBufferSize(int size)
    throws SocketException{
        if (!(size > 0)) {
            throw new IllegalArgumentException("negative send size");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_SNDBUF, new Integer(size));
    }

    /**
     * Get value of the SO_SNDBUF option for this <tt>DatagramSocket</tt>, that is the
     * buffer size used by the platform for output on this <tt>DatagramSocket</tt>.
     *
     * @return the value of the SO_SNDBUF option for this <tt>DatagramSocket</tt>
     * @exception SocketException if there is an error in
     * the underlying protocol, such as an UDP error.
     * @see #setSendBufferSize
     */
    public synchronized int getSendBufferSize() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        int result = 0;
        Object o = getImpl().getOption(SocketOptions.SO_SNDBUF);
        if (o instanceof Integer) {
            result = ((Integer)o).intValue();
        }
        return result;
    }

    /**
     * Sets the SO_RCVBUF option to the specified value for this
     * <tt>DatagramSocket</tt>. The SO_RCVBUF option is used by the
     * the network implementation as a hint to size the underlying
     * network I/O buffers. The SO_RCVBUF setting may also be used
     * by the network implementation to determine the maximum size
     * of the packet that can be received on this socket.
     * <p>
     * Because SO_RCVBUF is a hint, applications that want to
     * verify what size the buffers were set to should call
     * {@link #getReceiveBufferSize()}.
     * <p>
     * Increasing SO_RCVBUF may allow the network implementation
     * to buffer multiple packets when packets arrive faster than
     * are being received using {@link #receive(DatagramPacket)}.
     * <p>
     * Note: It is implementation specific if a packet larger
     * than SO_RCVBUF can be received.
     *
     * @param size the size to which to set the receive buffer
     * size. This value must be greater than 0.
     *
     * @exception SocketException if there is an error in
     * the underlying protocol, such as an UDP error.
     * @exception IllegalArgumentException if the value is 0 or is
     * negative.
     * @see #getReceiveBufferSize()
     */
    public synchronized void setReceiveBufferSize(int size)
    throws SocketException{
        if (size <= 0) {
            throw new IllegalArgumentException("invalid receive size");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_RCVBUF, new Integer(size));
    }

    /**
     * Get value of the SO_RCVBUF option for this <tt>DatagramSocket</tt>, that is the
     * buffer size used by the platform for input on this <tt>DatagramSocket</tt>.
     *
     * @return the value of the SO_RCVBUF option for this <tt>DatagramSocket</tt>
     * @exception SocketException if there is an error in the underlying protocol, such as an UDP error.
     * @see #setReceiveBufferSize(int)
     */
    public synchronized int getReceiveBufferSize()
    throws SocketException{
        if (isClosed())
            throw new SocketException("Socket is closed");
        int result = 0;
        Object o = getImpl().getOption(SocketOptions.SO_RCVBUF);
        if (o instanceof Integer) {
            result = ((Integer)o).intValue();
        }
        return result;
    }

    /**
     * Enable/disable the SO_REUSEADDR socket option.
     * <p>
     * For UDP sockets it may be necessary to bind more than one
     * socket to the same socket address. This is typically for the
     * purpose of receiving multicast packets
     * (See {@link java.net.MulticastSocket}). The
     * <tt>SO_REUSEADDR</tt> socket option allows multiple
     * sockets to be bound to the same socket address if the
     * <tt>SO_REUSEADDR</tt> socket option is enabled prior
     * to binding the socket using {@link #bind(SocketAddress)}.
     * <p>
     * Note: This functionality is not supported by all existing platforms,
     * so it is implementation specific whether this option will be ignored
     * or not. However, if it is not supported then
     * {@link #getReuseAddress()} will always return <code>false</code>.
     * <p>
     * When a <tt>DatagramSocket</tt> is created the initial setting
     * of <tt>SO_REUSEADDR</tt> is disabled.
     * <p>
     * The behaviour when <tt>SO_REUSEADDR</tt> is enabled or
     * disabled after a socket is bound (See {@link #isBound()})
     * is not defined.
     *
     * @param on  whether to enable or disable the
     * @exception SocketException if an error occurs enabling or
     *            disabling the <tt>SO_RESUEADDR</tt> socket option,
     *            or the socket is closed.
     * @since 1.4
     * @see #getReuseAddress()
     * @see #bind(SocketAddress)
     * @see #isBound()
     * @see #isClosed()
     */
    public synchronized void setReuseAddress(boolean on) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        // Integer instead of Boolean for compatibility with older DatagramSocketImpl
        if (oldImpl)
            getImpl().setOption(SocketOptions.SO_REUSEADDR, new Integer(on?-1:0));
        else
            getImpl().setOption(SocketOptions.SO_REUSEADDR, Boolean.valueOf(on));
    }

    /**
     * Tests if SO_REUSEADDR is enabled.
     *
     * @return a <code>boolean</code> indicating whether or not SO_REUSEADDR is enabled.
     * @exception SocketException if there is an error
     * in the underlying protocol, such as an UDP error.
     * @since   1.4
     * @see #setReuseAddress(boolean)
     */
    public synchronized boolean getReuseAddress() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        Object o = getImpl().getOption(SocketOptions.SO_REUSEADDR);
        return ((Boolean)o).booleanValue();
    }

    /**
     * Enable/disable SO_BROADCAST.
     *
     * <p> Some operating systems may require that the Java virtual machine be
     * started with implementation specific privileges to enable this option or
     * send broadcast datagrams.
     *
     * @param  on
     *         whether or not to have broadcast turned on.
     *
     * @throws  SocketException
     *          if there is an error in the underlying protocol, such as an UDP
     *          error.
     *
     * @since 1.4
     * @see #getBroadcast()
     */
    public synchronized void setBroadcast(boolean on) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_BROADCAST, Boolean.valueOf(on));
    }

    /**
     * Tests if SO_BROADCAST is enabled.
     * @return a <code>boolean</code> indicating whether or not SO_BROADCAST is enabled.
     * @exception SocketException if there is an error
     * in the underlying protocol, such as an UDP error.
     * @since 1.4
     * @see #setBroadcast(boolean)
     */
    public synchronized boolean getBroadcast() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return ((Boolean)(getImpl().getOption(SocketOptions.SO_BROADCAST))).booleanValue();
    }

    /**
     * Sets traffic class or type-of-service octet in the IP
     * datagram header for datagrams sent from this DatagramSocket.
     * As the underlying network implementation may ignore this
     * value applications should consider it a hint.
     *
     * <P> The tc <B>must</B> be in the range <code> 0 <= tc <=
     * 255</code> or an IllegalArgumentException will be thrown.
     * <p>Notes:
     * <p>For Internet Protocol v4 the value consists of an
     * <code>integer</code>, the least significant 8 bits of which
     * represent the value of the TOS octet in IP packets sent by
     * the socket.
     * RFC 1349 defines the TOS values as follows:
     * <p>
     * <UL>
     * <LI><CODE>IPTOS_LOWCOST (0x02)</CODE></LI>
     * <LI><CODE>IPTOS_RELIABILITY (0x04)</CODE></LI>
     * <LI><CODE>IPTOS_THROUGHPUT (0x08)</CODE></LI>
     * <LI><CODE>IPTOS_LOWDELAY (0x10)</CODE></LI>
     * </UL>
     * The last low order bit is always ignored as this
     * corresponds to the MBZ (must be zero) bit.
     * <p>
     * Setting bits in the precedence field may result in a
     * SocketException indicating that the operation is not
     * permitted.
     * <p>
     * for Internet Protocol v6 <code>tc</code> is the value that
     * would be placed into the sin6_flowinfo field of the IP header.
     *
     * @param tc        an <code>int</code> value for the bitset.
     * @throws SocketException if there is an error setting the
     * traffic class or type-of-service
     * @since 1.4
     * @see #getTrafficClass
     */
    public synchronized void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255)
            throw new IllegalArgumentException("tc is not in range 0 -- 255");

        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.IP_TOS, new Integer(tc));
    }

    /**
     * Gets traffic class or type-of-service in the IP datagram
     * header for packets sent from this DatagramSocket.
     * <p>
     * As the underlying network implementation may ignore the
     * traffic class or type-of-service set using {@link #setTrafficClass(int)}
     * this method may return a different value than was previously
     * set using the {@link #setTrafficClass(int)} method on this
     * DatagramSocket.
     *
     * @return the traffic class or type-of-service already set
     * @throws SocketException if there is an error obtaining the
     * traffic class or type-of-service value.
     * @since 1.4
     * @see #setTrafficClass(int)
     */
    public synchronized int getTrafficClass() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return ((Integer)(getImpl().getOption(SocketOptions.IP_TOS))).intValue();
    }

    /**
     * Closes this datagram socket.
     * <p>
     * Any thread currently blocked in {@link #receive} upon this socket
     * will throw a {@link SocketException}.
     *
     * <p> If this socket has an associated channel then the channel is closed
     * as well.
     *
     * @revised 1.4
     * @spec JSR-51
     */
    public void close() {
        synchronized(closeLock) {
            if (isClosed())
                return;
            impl.close();
            closed = true;
        }
    }

    /**
     * Returns whether the socket is closed or not.
     *
     * @return true if the socket has been closed
     * @since 1.4
     */
    public boolean isClosed() {
        synchronized(closeLock) {
            return closed;
        }
    }

    /**
     * Returns the unique {@link java.nio.channels.DatagramChannel} object
     * associated with this datagram socket, if any.
     *
     * <p> A datagram socket will have a channel if, and only if, the channel
     * itself was created via the {@link java.nio.channels.DatagramChannel#open
     * DatagramChannel.open} method.
     *
     * @return  the datagram channel associated with this datagram socket,
     *          or <tt>null</tt> if this socket was not created for a channel
     *
     * @since 1.4
     * @spec JSR-51
     */
    public DatagramChannel getChannel() {
        return null;
    }

    /**
     * User defined factory for all datagram sockets.
     */
    static DatagramSocketImplFactory factory;

    /**
     * Sets the datagram socket implementation factory for the
     * application. The factory can be specified only once.
     * <p>
     * When an application creates a new datagram socket, the socket
     * implementation factory's <code>createDatagramSocketImpl</code> method is
     * called to create the actual datagram socket implementation.
     * <p>
     * Passing <code>null</code> to the method is a no-op unless the factory
     * was already set.
     *
     * <p>If there is a security manager, this method first calls
     * the security manager's <code>checkSetFactory</code> method
     * to ensure the operation is allowed.
     * This could result in a SecurityException.
     *
     * @param      fac   the desired factory.
     * @exception  IOException  if an I/O error occurs when setting the
     *              datagram socket factory.
     * @exception  SocketException  if the factory is already defined.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkSetFactory</code> method doesn't allow the
     operation.
     * @see
     java.net.DatagramSocketImplFactory#createDatagramSocketImpl()
     * @see       SecurityManager#checkSetFactory
     * @since 1.3
     */
    public static synchronized void
    setDatagramSocketImplFactory(DatagramSocketImplFactory fac)
       throws IOException
    {
        if (factory != null) {
            throw new SocketException("factory already defined");
        }
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkSetFactory();
        }
        factory = fac;
    }
}
