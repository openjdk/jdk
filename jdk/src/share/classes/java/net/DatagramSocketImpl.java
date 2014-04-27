/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.HashSet;

/**
 * Abstract datagram and multicast socket implementation base class.
 * @author Pavani Diwanji
 * @since  JDK1.1
 */

public abstract class DatagramSocketImpl implements SocketOptions {

    /**
     * The local port number.
     */
    protected int localPort;

    /**
     * The file descriptor object.
     */
    protected FileDescriptor fd;

    /**
     * The DatagramSocket or MulticastSocket
     * that owns this impl
     */
    DatagramSocket socket;

    void setDatagramSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    DatagramSocket getDatagramSocket() {
        return socket;
    }

    /**
     * Creates a datagram socket.
     * @exception SocketException if there is an error in the
     * underlying protocol, such as a TCP error.
     */
    protected abstract void create() throws SocketException;

    /**
     * Binds a datagram socket to a local port and address.
     * @param lport the local port
     * @param laddr the local address
     * @exception SocketException if there is an error in the
     * underlying protocol, such as a TCP error.
     */
    protected abstract void bind(int lport, InetAddress laddr) throws SocketException;

    /**
     * Sends a datagram packet. The packet contains the data and the
     * destination address to send the packet to.
     * @param p the packet to be sent.
     * @exception IOException if an I/O exception occurs while sending the
     * datagram packet.
     * @exception  PortUnreachableException may be thrown if the socket is connected
     * to a currently unreachable destination. Note, there is no guarantee that
     * the exception will be thrown.
     */
    protected abstract void send(DatagramPacket p) throws IOException;

    /**
     * Connects a datagram socket to a remote destination. This associates the remote
     * address with the local socket so that datagrams may only be sent to this destination
     * and received from this destination. This may be overridden to call a native
     * system connect.
     *
     * <p>If the remote destination to which the socket is connected does not
     * exist, or is otherwise unreachable, and if an ICMP destination unreachable
     * packet has been received for that address, then a subsequent call to
     * send or receive may throw a PortUnreachableException.
     * Note, there is no guarantee that the exception will be thrown.
     * @param address the remote InetAddress to connect to
     * @param port the remote port number
     * @exception   SocketException may be thrown if the socket cannot be
     * connected to the remote destination
     * @since 1.4
     */
    protected void connect(InetAddress address, int port) throws SocketException {}

    /**
     * Disconnects a datagram socket from its remote destination.
     * @since 1.4
     */
    protected void disconnect() {}

    /**
     * Peek at the packet to see who it is from. Updates the specified {@code InetAddress}
     * to the address which the packet came from.
     * @param i an InetAddress object
     * @return the port number which the packet came from.
     * @exception IOException if an I/O exception occurs
     * @exception  PortUnreachableException may be thrown if the socket is connected
     *       to a currently unreachable destination. Note, there is no guarantee that the
     *       exception will be thrown.
     */
    protected abstract int peek(InetAddress i) throws IOException;

    /**
     * Peek at the packet to see who it is from. The data is copied into the specified
     * {@code DatagramPacket}. The data is returned,
     * but not consumed, so that a subsequent peekData/receive operation
     * will see the same data.
     * @param p the Packet Received.
     * @return the port number which the packet came from.
     * @exception IOException if an I/O exception occurs
     * @exception  PortUnreachableException may be thrown if the socket is connected
     *       to a currently unreachable destination. Note, there is no guarantee that the
     *       exception will be thrown.
     * @since 1.4
     */
    protected abstract int peekData(DatagramPacket p) throws IOException;
    /**
     * Receive the datagram packet.
     * @param p the Packet Received.
     * @exception IOException if an I/O exception occurs
     * while receiving the datagram packet.
     * @exception  PortUnreachableException may be thrown if the socket is connected
     *       to a currently unreachable destination. Note, there is no guarantee that the
     *       exception will be thrown.
     */
    protected abstract void receive(DatagramPacket p) throws IOException;

    /**
     * Set the TTL (time-to-live) option.
     * @param ttl a byte specifying the TTL value
     *
     * @deprecated use setTimeToLive instead.
     * @exception IOException if an I/O exception occurs while setting
     * the time-to-live option.
     * @see #getTTL()
     */
    @Deprecated
    protected abstract void setTTL(byte ttl) throws IOException;

    /**
     * Retrieve the TTL (time-to-live) option.
     *
     * @exception IOException if an I/O exception occurs
     * while retrieving the time-to-live option
     * @deprecated use getTimeToLive instead.
     * @return a byte representing the TTL value
     * @see #setTTL(byte)
     */
    @Deprecated
    protected abstract byte getTTL() throws IOException;

    /**
     * Set the TTL (time-to-live) option.
     * @param ttl an {@code int} specifying the time-to-live value
     * @exception IOException if an I/O exception occurs
     * while setting the time-to-live option.
     * @see #getTimeToLive()
     */
    protected abstract void setTimeToLive(int ttl) throws IOException;

    /**
     * Retrieve the TTL (time-to-live) option.
     * @exception IOException if an I/O exception occurs
     * while retrieving the time-to-live option
     * @return an {@code int} representing the time-to-live value
     * @see #setTimeToLive(int)
     */
    protected abstract int getTimeToLive() throws IOException;

    /**
     * Join the multicast group.
     * @param inetaddr multicast address to join.
     * @exception IOException if an I/O exception occurs
     * while joining the multicast group.
     */
    protected abstract void join(InetAddress inetaddr) throws IOException;

    /**
     * Leave the multicast group.
     * @param inetaddr multicast address to leave.
     * @exception IOException if an I/O exception occurs
     * while leaving the multicast group.
     */
    protected abstract void leave(InetAddress inetaddr) throws IOException;

    /**
     * Join the multicast group.
     * @param mcastaddr address to join.
     * @param netIf specifies the local interface to receive multicast
     *        datagram packets
     * @throws IOException if an I/O exception occurs while joining
     * the multicast group
     * @since 1.4
     */
    protected abstract void joinGroup(SocketAddress mcastaddr,
                                      NetworkInterface netIf)
        throws IOException;

    /**
     * Leave the multicast group.
     * @param mcastaddr address to leave.
     * @param netIf specified the local interface to leave the group at
     * @throws IOException if an I/O exception occurs while leaving
     * the multicast group
     * @since 1.4
     */
    protected abstract void leaveGroup(SocketAddress mcastaddr,
                                       NetworkInterface netIf)
        throws IOException;

    /**
     * Close the socket.
     */
    protected abstract void close();

    /**
     * Gets the local port.
     * @return an {@code int} representing the local port value
     */
    protected int getLocalPort() {
        return localPort;
    }

    /**
     * Gets the datagram socket file descriptor.
     * @return a {@code FileDescriptor} object representing the datagram socket
     * file descriptor
     */
    protected FileDescriptor getFileDescriptor() {
        return fd;
    }

    /**
     * Called to set a socket option.
     *
     * @param name The socket option
     *
     * @param value The value of the socket option. A value of {@code null}
     *              may be valid for some options.
     *
     * @throws UnsupportedOperationException if the DatagramSocketImpl does not
     *         support the option
     *
     * @throws NullPointerException if name is {@code null}
     *
     * @since 1.9
     */
    protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
        if (name == StandardSocketOptions.SO_SNDBUF) {
            setOption(SocketOptions.SO_SNDBUF, value);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            setOption(SocketOptions.SO_RCVBUF, value);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            setOption(SocketOptions.SO_REUSEADDR, value);
        } else if (name == StandardSocketOptions.IP_TOS) {
            setOption(SocketOptions.IP_TOS, value);
        } else if (name == StandardSocketOptions.IP_MULTICAST_IF &&
            (getDatagramSocket() instanceof MulticastSocket)) {
            setOption(SocketOptions.IP_MULTICAST_IF2, value);
        } else if (name == StandardSocketOptions.IP_MULTICAST_TTL &&
            (getDatagramSocket() instanceof MulticastSocket)) {
            if (! (value instanceof Integer)) {
                throw new IllegalArgumentException("not an integer");
            }
            setTimeToLive((Integer)value);
        } else if (name == StandardSocketOptions.IP_MULTICAST_LOOP &&
            (getDatagramSocket() instanceof MulticastSocket)) {
            setOption(SocketOptions.IP_MULTICAST_LOOP, value);
        } else {
            throw new UnsupportedOperationException("unsupported option");
        }
    }

    /**
     * Called to get a socket option.
     *
     * @param name The socket option
     *
     * @throws UnsupportedOperationException if the DatagramSocketImpl does not
     *         support the option
     *
     * @throws NullPointerException if name is {@code null}
     *
     * @since 1.9
     */
    @SuppressWarnings("unchecked")
    protected <T> T getOption(SocketOption<T> name) throws IOException {
        if (name == StandardSocketOptions.SO_SNDBUF) {
            return (T) getOption(SocketOptions.SO_SNDBUF);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            return (T) getOption(SocketOptions.SO_RCVBUF);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            return (T) getOption(SocketOptions.SO_REUSEADDR);
        } else if (name == StandardSocketOptions.IP_TOS) {
            return (T) getOption(SocketOptions.IP_TOS);
        } else if (name == StandardSocketOptions.IP_MULTICAST_IF &&
            (getDatagramSocket() instanceof MulticastSocket)) {
            return (T) getOption(SocketOptions.IP_MULTICAST_IF2);
        } else if (name == StandardSocketOptions.IP_MULTICAST_TTL &&
            (getDatagramSocket() instanceof MulticastSocket)) {
            Integer ttl = getTimeToLive();
            return (T)ttl;
        } else if (name == StandardSocketOptions.IP_MULTICAST_LOOP &&
            (getDatagramSocket() instanceof MulticastSocket)) {
            return (T) getOption(SocketOptions.IP_MULTICAST_LOOP);
        } else {
            throw new UnsupportedOperationException("unsupported option");
        }
    }

    private static final  Set<SocketOption<?>> dgSocketOptions =
        new HashSet<>();

    private static final  Set<SocketOption<?>> mcSocketOptions =
        new HashSet<>();

    static {
        dgSocketOptions.add(StandardSocketOptions.SO_SNDBUF);
        dgSocketOptions.add(StandardSocketOptions.SO_RCVBUF);
        dgSocketOptions.add(StandardSocketOptions.SO_REUSEADDR);
        dgSocketOptions.add(StandardSocketOptions.IP_TOS);

        mcSocketOptions.add(StandardSocketOptions.SO_SNDBUF);
        mcSocketOptions.add(StandardSocketOptions.SO_RCVBUF);
        mcSocketOptions.add(StandardSocketOptions.SO_REUSEADDR);
        mcSocketOptions.add(StandardSocketOptions.IP_TOS);
        mcSocketOptions.add(StandardSocketOptions.IP_MULTICAST_IF);
        mcSocketOptions.add(StandardSocketOptions.IP_MULTICAST_TTL);
        mcSocketOptions.add(StandardSocketOptions.IP_MULTICAST_LOOP);
    };

    /**
     * Returns a set of SocketOptions supported by this impl
     * and by this impl's socket (DatagramSocket or MulticastSocket)
     *
     * @return a Set of SocketOptions
     */
    protected Set<SocketOption<?>> supportedOptions() {
        if (getDatagramSocket() instanceof MulticastSocket) {
            return mcSocketOptions;
        } else {
            return dgSocketOptions;
        }
    }
}
