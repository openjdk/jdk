/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Native;

/**
 * Interface of methods to get/set socket options.  This interface is
 * implemented by {@link SocketImpl} and {@link DatagramSocketImpl}.
 * Subclasses of these two classes should override the {@link #getOption(int)} and
 * {@link #setOption(int, Object)} methods of this interface in order to support their own options.
 * <P>
 * The methods and constants defined in this interface are
 * for implementation only. If you're not subclassing {@code SocketImpl} or
 * {@code DatagramSocketImpl}, then you won't use these directly. There are
 * type-safe methods to get/set each of these options in {@link Socket}, {@link ServerSocket},
 *  {@link DatagramSocket} and {@link MulticastSocket}.
 *
 * @author David Brown
 * @since 1.1
 */


public interface SocketOptions {

    /**
     * Enable/disable the option specified by {@code optID}. If the option
     * is to be enabled, and it takes an option-specific "value", this is passed in {@code value}.
     * The actual type of {@code value} is option-specific, and it is an error to pass something
     * that isn't of the expected type:
     * {@snippet lang=java :
     * SocketImpl s;
     * ...
     * s.setOption(SO_LINGER, Integer.valueOf(10));
     *    // OK - set SO_LINGER w/ timeout of 10 sec.
     * s.setOption(SO_LINGER, Double.valueOf(10));
     *    // ERROR - expects java.lang.Integer
     *}
     * If the requested option is binary, it can be set using this method by a {@link Boolean}:
     * {@snippet lang=java :
     * s.setOption(TCP_NODELAY, Boolean.TRUE);
     *    // OK - enables TCP_NODELAY, a binary option
     * }
     * Any option can be disabled using this method with a {@link Boolean#FALSE}:
     * {@snippet lang=java :
     * s.setOption(TCP_NODELAY, Boolean.FALSE);
     *    // OK - disables TCP_NODELAY
     * s.setOption(SO_LINGER, Boolean.FALSE);
     *    // OK - disables SO_LINGER
     * }
     * For an option that has a notion of on and off, and requires a non-boolean parameter, setting
     * its value to anything other than {@link Boolean#FALSE} implicitly enables it.
     *
     * @param  optID identifies the option
     * @param  value the parameter of the socket option
     * @throws SocketException if the option is unrecognized, the socket is closed, or some
     *                         low-level error occurred
     * @see    #getOption(int)
     */
    public void setOption(int optID, Object value) throws SocketException;

    /**
     * Fetch the value of an option. Binary options will return {@link Boolean#TRUE} if enabled,
     * {@link Boolean#FALSE} if disabled, e.g.:
     * {@snippet lang=java :
     * SocketImpl s;
     * ...
     * Boolean noDelay = (Boolean)(s.getOption(TCP_NODELAY));
     * if (noDelay.booleanValue()) {
     *     // true if TCP_NODELAY is enabled...
     * ...
     * }
     * }
     * <P>
     * For options that take a particular type as a parameter, this method will return the
     * parameter's value, else it will return {@link Boolean#FALSE}:
     * {@snippet lang=java :
     * Object o = s.getOption(SO_LINGER);
     * if (o instanceof Integer) {
     *     System.out.print("Linger time is " + ((Integer)o).intValue());
     * } else {
     *   // the true type of o is java.lang.Boolean.FALSE;
     * }
     * }
     *
     * @param  optID an {@code int} identifying the option to fetch
     * @return the value of the option
     * @throws SocketException if the socket is closed or if {@code optID} is unknown along the
     *                         protocol stack
     * @see #setOption(int, java.lang.Object)
     */
    public Object getOption(int optID) throws SocketException;

    // Java supported BSD-style options follow

    /**
     * See {@link StandardSocketOptions#TCP_NODELAY} for description of this socket option.
     *
     * @see Socket#setTcpNoDelay
     * @see Socket#getTcpNoDelay
     */
    @Native public static final int TCP_NODELAY = 0x0001;

    /**
     * Fetch the local address binding of a socket. This option cannot be set and can only be
     * fetched. The default local address of a socket is
     * {@link InetAddress#isAnyLocalAddress() INADDR_ANY}, meaning any local
     * address on a multi-homed host. A multi-homed host can use this option to accept
     * connections to only one of its addresses (in the case of a
     * {@link ServerSocket} or {@link DatagramSocket}), or to specify its return address
     * to the peer (for a {@link Socket} or {@link DatagramSocket}). The type of this option's
     * value is an {@link InetAddress}.
     *
     * @see Socket#getLocalAddress
     * @see DatagramSocket#getLocalAddress
     */
    @Native public static final int SO_BINDADDR = 0x000F;

    /**
     * See {@link StandardSocketOptions#SO_REUSEADDR} for description of this socket option.
     */
    @Native public static final int SO_REUSEADDR = 0x04;

    /**
     * See {@link StandardSocketOptions#SO_REUSEPORT} for description of this socket option.
     * @since 9
     */
    @Native public static final int SO_REUSEPORT = 0x0E;

    /**
     * See {@link StandardSocketOptions#SO_BROADCAST} for description of this socket option.
     * @since 1.4
     */
    @Native public static final int SO_BROADCAST = 0x0020;

    /**
     * See {@link StandardSocketOptions#IP_MULTICAST_IF} for description of this socket option.
     *
     * @see MulticastSocket#setInterface(InetAddress)
     * @see MulticastSocket#getInterface()
     */
    @Native public static final int IP_MULTICAST_IF = 0x10;

    /**
     * This option is used to both set and fetch the outgoing interface on which the multicast
     * packets are sent. Useful on hosts with multiple network interfaces, where applications
     * want to use other than the system default. This option supports setting outgoing interfaces
     * with either IPv4 and IPv6 addresses.
     *
     * @see MulticastSocket#setNetworkInterface(NetworkInterface)
     * @see MulticastSocket#getNetworkInterface()
     * @since 1.4
     */
    @Native public static final int IP_MULTICAST_IF2 = 0x1f;

    /**
     * See {@link StandardSocketOptions#IP_MULTICAST_LOOP} for description of this socket option.
     * @since 1.4
     */
    @Native public static final int IP_MULTICAST_LOOP = 0x12;

    /**
     * See {@link StandardSocketOptions#IP_TOS} for description of this socket option.
     * @since 1.4
     */
    @Native public static final int IP_TOS = 0x3;

    /**
     * See {@link StandardSocketOptions#SO_LINGER} for description of this socket option.
     * <p>
     * Set the value to {@code Boolean.FALSE} or an integer less than {@code 0} with
     * {@link #setOption(int, Object)} to disable this option. An integer greater than or equal to
     * {@code 0} will enable the option and will represent the linger interval.
     * <p>
     * If this option is enabled then {@link #getOption(int)} will return an integer value
     * representing the linger interval, else the return value will be {@code Boolean.FALSE}.
     *
     * @see Socket#setSoLinger
     * @see Socket#getSoLinger
     */
    @Native public static final int SO_LINGER = 0x0080;

    /**
     * This option is used to both set and fetch a timeout value on blocking
     * {@code Socket} operations:
     * <ul>
     *     <li>{@linkplain ServerSocket#accept() ServerSocket.accept()}</li>
     *     <li>{@linkplain Socket#getInputStream()  Socket InputStream.read()}</li>
     *     <li>{@linkplain DatagramSocket#receive(DatagramPacket) DatagramSocket.receive()}</li>
     * </ul>
     *
     * <P>
     * This option must be set prior to entering a blocking operation to take effect. If the
     * timeout expires and the operation would continue to block, then
     * {@link java.io.InterruptedIOException} is raised. The {@code Socket} is not closed
     * in such cases.
     *
     * @see Socket#setSoTimeout
     * @see ServerSocket#setSoTimeout
     * @see DatagramSocket#setSoTimeout
     */
    @Native public static final int SO_TIMEOUT = 0x1006;

    /**
     * See {@link StandardSocketOptions#SO_SNDBUF} for description of this socket option.
     *
     * @see Socket#setSendBufferSize
     * @see Socket#getSendBufferSize
     * @see DatagramSocket#setSendBufferSize
     * @see DatagramSocket#getSendBufferSize
     */
    @Native public static final int SO_SNDBUF = 0x1001;

    /**
     * See {@link StandardSocketOptions#SO_RCVBUF} for description of this socket option.
     *
     * @see Socket#setReceiveBufferSize
     * @see Socket#getReceiveBufferSize
     * @see DatagramSocket#setReceiveBufferSize
     * @see DatagramSocket#getReceiveBufferSize
     */
    @Native public static final int SO_RCVBUF = 0x1002;

    /**
     * See {@link StandardSocketOptions#SO_KEEPALIVE} for description of this socket option.
     *
     * @see Socket#setKeepAlive
     * @see Socket#getKeepAlive
     */
    @Native public static final int SO_KEEPALIVE = 0x0008;

    /**
     * When this option is set, any TCP urgent data received on the socket will be received
     * through the socket input stream. When the option is disabled (which is the default)
     * urgent data is silently discarded.
     *
     * @see Socket#setOOBInline
     * @see Socket#getOOBInline
     */
    @Native public static final int SO_OOBINLINE = 0x1003;
}
