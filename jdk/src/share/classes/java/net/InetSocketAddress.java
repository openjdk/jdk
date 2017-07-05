/*
 * Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;

/**
 *
 * This class implements an IP Socket Address (IP address + port number)
 * It can also be a pair (hostname + port number), in which case an attempt
 * will be made to resolve the hostname. If resolution fails then the address
 * is said to be <I>unresolved</I> but can still be used on some circumstances
 * like connecting through a proxy.
 * <p>
 * It provides an immutable object used by sockets for binding, connecting, or
 * as returned values.
 * <p>
 * The <i>wildcard</i> is a special local IP address. It usually means "any"
 * and can only be used for <code>bind</code> operations.
 *
 * @see java.net.Socket
 * @see java.net.ServerSocket
 * @since 1.4
 */
public class InetSocketAddress extends SocketAddress {
    /* The hostname of the Socket Address
     * @serial
     */
    private String hostname = null;
    /* The IP address of the Socket Address
     * @serial
     */
    private InetAddress addr = null;
    /* The port number of the Socket Address
     * @serial
     */
    private int port;

    private static final long serialVersionUID = 5076001401234631237L;

    private InetSocketAddress() {
    }

    /**
     * Creates a socket address where the IP address is the wildcard address
     * and the port number a specified value.
     * <p>
     * A valid port value is between 0 and 65535.
     * A port number of <code>zero</code> will let the system pick up an
     * ephemeral port in a <code>bind</code> operation.
     * <p>
     * @param   port    The port number
     * @throws IllegalArgumentException if the port parameter is outside the specified
     * range of valid port values.
     */
    public InetSocketAddress(int port) {
        this(InetAddress.anyLocalAddress(), port);
    }

    /**
     *
     * Creates a socket address from an IP address and a port number.
     * <p>
     * A valid port value is between 0 and 65535.
     * A port number of <code>zero</code> will let the system pick up an
     * ephemeral port in a <code>bind</code> operation.
     * <P>
     * A <code>null</code> address will assign the <i>wildcard</i> address.
     * <p>
     * @param   addr    The IP address
     * @param   port    The port number
     * @throws IllegalArgumentException if the port parameter is outside the specified
     * range of valid port values.
     */
    public InetSocketAddress(InetAddress addr, int port) {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("port out of range:" + port);
        }
        this.port = port;
        if (addr == null)
            this.addr = InetAddress.anyLocalAddress();
        else
            this.addr = addr;
    }

    /**
     *
     * Creates a socket address from a hostname and a port number.
     * <p>
     * An attempt will be made to resolve the hostname into an InetAddress.
     * If that attempt fails, the address will be flagged as <I>unresolved</I>.
     * <p>
     * If there is a security manager, its <code>checkConnect</code> method
     * is called with the host name as its argument to check the permissiom
     * to resolve it. This could result in a SecurityException.
     * <P>
     * A valid port value is between 0 and 65535.
     * A port number of <code>zero</code> will let the system pick up an
     * ephemeral port in a <code>bind</code> operation.
     * <P>
     * @param   hostname the Host name
     * @param   port    The port number
     * @throws IllegalArgumentException if the port parameter is outside the range
     * of valid port values, or if the hostname parameter is <TT>null</TT>.
     * @throws SecurityException if a security manager is present and
     *                           permission to resolve the host name is
     *                           denied.
     * @see     #isUnresolved()
     */
    public InetSocketAddress(String hostname, int port) {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("port out of range:" + port);
        }
        if (hostname == null) {
            throw new IllegalArgumentException("hostname can't be null");
        }
        try {
            addr = InetAddress.getByName(hostname);
        } catch(UnknownHostException e) {
            this.hostname = hostname;
            addr = null;
        }
        this.port = port;
    }

    /**
     *
     * Creates an unresolved socket address from a hostname and a port number.
     * <p>
     * No attempt will be made to resolve the hostname into an InetAddress.
     * The address will be flagged as <I>unresolved</I>.
     * <p>
     * A valid port value is between 0 and 65535.
     * A port number of <code>zero</code> will let the system pick up an
     * ephemeral port in a <code>bind</code> operation.
     * <P>
     * @param   host    the Host name
     * @param   port    The port number
     * @throws IllegalArgumentException if the port parameter is outside
     *                  the range of valid port values, or if the hostname
     *                  parameter is <TT>null</TT>.
     * @see     #isUnresolved()
     * @return  a <code>InetSocketAddress</code> representing the unresolved
     *          socket address
     * @since 1.5
     */
    public static InetSocketAddress createUnresolved(String host, int port) {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("port out of range:" + port);
        }
        if (host == null) {
            throw new IllegalArgumentException("hostname can't be null");
        }
        InetSocketAddress s = new InetSocketAddress();
        s.port = port;
        s.hostname = host;
        s.addr = null;
        return s;
    }

    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Check that our invariants are satisfied
        if (port < 0 || port > 0xFFFF) {
            throw new InvalidObjectException("port out of range:" + port);
        }

        if (hostname == null && addr == null) {
            throw new InvalidObjectException("hostname and addr " +
                                             "can't both be null");
        }
    }

    /**
     * Gets the port number.
     *
     * @return the port number.
     */
    public final int getPort() {
        return port;
    }

    /**
     *
     * Gets the <code>InetAddress</code>.
     *
     * @return the InetAdress or <code>null</code> if it is unresolved.
     */
    public final InetAddress getAddress() {
        return addr;
    }

    /**
     * Gets the <code>hostname</code>.
     * Note: This method may trigger a name service reverse lookup if the
     * address was created with a literal IP address.
     *
     * @return  the hostname part of the address.
     */
    public final String getHostName() {
        if (hostname != null)
            return hostname;
        if (addr != null)
            return addr.getHostName();
        return null;
    }

    /**
     * Returns the hostname, or the String form of the address if it
     * doesn't have a hostname (it was created using a literal).
     * This has the benefit of <b>not</b> attemptimg a reverse lookup.
     *
     * @return the hostname, or String representation of the address.
     * @since 1.7
     */
    public final String getHostString() {
        if (hostname != null)
            return hostname;
        if (addr != null) {
            if (addr.hostName != null)
                return addr.hostName;
            else
                return addr.getHostAddress();
        }
        return null;
    }

    /**
     * Checks whether the address has been resolved or not.
     *
     * @return <code>true</code> if the hostname couldn't be resolved into
     *          an <code>InetAddress</code>.
     */
    public final boolean isUnresolved() {
        return addr == null;
    }

    /**
     * Constructs a string representation of this InetSocketAddress.
     * This String is constructed by calling toString() on the InetAddress
     * and concatenating the port number (with a colon). If the address
     * is unresolved then the part before the colon will only contain the hostname.
     *
     * @return  a string representation of this object.
     */
    public String toString() {
        if (isUnresolved()) {
            return hostname + ":" + port;
        } else {
            return addr.toString() + ":" + port;
        }
    }

    /**
     * Compares this object against the specified object.
     * The result is <code>true</code> if and only if the argument is
     * not <code>null</code> and it represents the same address as
     * this object.
     * <p>
     * Two instances of <code>InetSocketAddress</code> represent the same
     * address if both the InetAddresses (or hostnames if it is unresolved) and port
     * numbers are equal.
     * If both addresses are unresolved, then the hostname & the port number
     * are compared.
     *
     * Note: Hostnames are case insensitive. e.g. "FooBar" and "foobar" are
     * considered equal.
     *
     * @param   obj   the object to compare against.
     * @return  <code>true</code> if the objects are the same;
     *          <code>false</code> otherwise.
     * @see java.net.InetAddress#equals(java.lang.Object)
     */
    public final boolean equals(Object obj) {
        if (obj == null || !(obj instanceof InetSocketAddress))
            return false;
        InetSocketAddress sockAddr = (InetSocketAddress) obj;
        boolean sameIP = false;
        if (this.addr != null)
            sameIP = this.addr.equals(sockAddr.addr);
        else if (this.hostname != null)
            sameIP = (sockAddr.addr == null) &&
                this.hostname.equalsIgnoreCase(sockAddr.hostname);
        else
            sameIP = (sockAddr.addr == null) && (sockAddr.hostname == null);
        return sameIP && (this.port == sockAddr.port);
    }

    /**
     * Returns a hashcode for this socket address.
     *
     * @return  a hash code value for this socket address.
     */
    public final int hashCode() {
        if (addr != null)
            return addr.hashCode() + port;
        if (hostname != null)
            return hostname.toLowerCase().hashCode() + port;
        return port;
    }
}
