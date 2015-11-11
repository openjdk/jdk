/*
 * Copyright (c) 1995, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.NavigableSet;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.ServiceLoader;
import java.security.AccessController;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInputStream.GetField;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import jdk.internal.misc.JavaNetInetAddressAccess;
import jdk.internal.misc.SharedSecrets;
import sun.security.action.*;
import sun.net.InetAddressCachePolicy;
import sun.net.util.IPAddressUtil;
import sun.net.spi.nameservice.*;

/**
 * This class represents an Internet Protocol (IP) address.
 *
 * <p> An IP address is either a 32-bit or 128-bit unsigned number
 * used by IP, a lower-level protocol on which protocols like UDP and
 * TCP are built. The IP address architecture is defined by <a
 * href="http://www.ietf.org/rfc/rfc790.txt"><i>RFC&nbsp;790:
 * Assigned Numbers</i></a>, <a
 * href="http://www.ietf.org/rfc/rfc1918.txt"> <i>RFC&nbsp;1918:
 * Address Allocation for Private Internets</i></a>, <a
 * href="http://www.ietf.org/rfc/rfc2365.txt"><i>RFC&nbsp;2365:
 * Administratively Scoped IP Multicast</i></a>, and <a
 * href="http://www.ietf.org/rfc/rfc2373.txt"><i>RFC&nbsp;2373: IP
 * Version 6 Addressing Architecture</i></a>. An instance of an
 * InetAddress consists of an IP address and possibly its
 * corresponding host name (depending on whether it is constructed
 * with a host name or whether it has already done reverse host name
 * resolution).
 *
 * <h3> Address types </h3>
 *
 * <blockquote><table cellspacing=2 summary="Description of unicast and multicast address types">
 *   <tr><th valign=top><i>unicast</i></th>
 *       <td>An identifier for a single interface. A packet sent to
 *         a unicast address is delivered to the interface identified by
 *         that address.
 *
 *         <p> The Unspecified Address -- Also called anylocal or wildcard
 *         address. It must never be assigned to any node. It indicates the
 *         absence of an address. One example of its use is as the target of
 *         bind, which allows a server to accept a client connection on any
 *         interface, in case the server host has multiple interfaces.
 *
 *         <p> The <i>unspecified</i> address must not be used as
 *         the destination address of an IP packet.
 *
 *         <p> The <i>Loopback</i> Addresses -- This is the address
 *         assigned to the loopback interface. Anything sent to this
 *         IP address loops around and becomes IP input on the local
 *         host. This address is often used when testing a
 *         client.</td></tr>
 *   <tr><th valign=top><i>multicast</i></th>
 *       <td>An identifier for a set of interfaces (typically belonging
 *         to different nodes). A packet sent to a multicast address is
 *         delivered to all interfaces identified by that address.</td></tr>
 * </table></blockquote>
 *
 * <h4> IP address scope </h4>
 *
 * <p> <i>Link-local</i> addresses are designed to be used for addressing
 * on a single link for purposes such as auto-address configuration,
 * neighbor discovery, or when no routers are present.
 *
 * <p> <i>Site-local</i> addresses are designed to be used for addressing
 * inside of a site without the need for a global prefix.
 *
 * <p> <i>Global</i> addresses are unique across the internet.
 *
 * <h4> Textual representation of IP addresses </h4>
 *
 * The textual representation of an IP address is address family specific.
 *
 * <p>
 *
 * For IPv4 address format, please refer to <A
 * HREF="Inet4Address.html#format">Inet4Address#format</A>; For IPv6
 * address format, please refer to <A
 * HREF="Inet6Address.html#format">Inet6Address#format</A>.
 *
 * <P>There is a <a href="doc-files/net-properties.html#Ipv4IPv6">couple of
 * System Properties</a> affecting how IPv4 and IPv6 addresses are used.</P>
 *
 * <h4> Host Name Resolution </h4>
 *
 * Host name-to-IP address <i>resolution</i> is accomplished through
 * the use of a combination of local machine configuration information
 * and network naming services such as the Domain Name System (DNS)
 * and Network Information Service(NIS). The particular naming
 * services(s) being used is by default the local machine configured
 * one. For any host name, its corresponding IP address is returned.
 *
 * <p> <i>Reverse name resolution</i> means that for any IP address,
 * the host associated with the IP address is returned.
 *
 * <p> The InetAddress class provides methods to resolve host names to
 * their IP addresses and vice versa.
 *
 * <h4> InetAddress Caching </h4>
 *
 * The InetAddress class has a cache to store successful as well as
 * unsuccessful host name resolutions.
 *
 * <p> By default, when a security manager is installed, in order to
 * protect against DNS spoofing attacks,
 * the result of positive host name resolutions are
 * cached forever. When a security manager is not installed, the default
 * behavior is to cache entries for a finite (implementation dependent)
 * period of time. The result of unsuccessful host
 * name resolution is cached for a very short period of time (10
 * seconds) to improve performance.
 *
 * <p> If the default behavior is not desired, then a Java security property
 * can be set to a different Time-to-live (TTL) value for positive
 * caching. Likewise, a system admin can configure a different
 * negative caching TTL value when needed.
 *
 * <p> Two Java security properties control the TTL values used for
 *  positive and negative host name resolution caching:
 *
 * <blockquote>
 * <dl>
 * <dt><b>networkaddress.cache.ttl</b></dt>
 * <dd>Indicates the caching policy for successful name lookups from
 * the name service. The value is specified as an integer to indicate
 * the number of seconds to cache the successful lookup. The default
 * setting is to cache for an implementation specific period of time.
 * <p>
 * A value of -1 indicates "cache forever".
 * </dd>
 * <dt><b>networkaddress.cache.negative.ttl</b> (default: 10)</dt>
 * <dd>Indicates the caching policy for un-successful name lookups
 * from the name service. The value is specified as an integer to
 * indicate the number of seconds to cache the failure for
 * un-successful lookups.
 * <p>
 * A value of 0 indicates "never cache".
 * A value of -1 indicates "cache forever".
 * </dd>
 * </dl>
 * </blockquote>
 *
 * @author  Chris Warth
 * @see     java.net.InetAddress#getByAddress(byte[])
 * @see     java.net.InetAddress#getByAddress(java.lang.String, byte[])
 * @see     java.net.InetAddress#getAllByName(java.lang.String)
 * @see     java.net.InetAddress#getByName(java.lang.String)
 * @see     java.net.InetAddress#getLocalHost()
 * @since 1.0
 */
public
class InetAddress implements java.io.Serializable {
    /**
     * Specify the address family: Internet Protocol, Version 4
     * @since 1.4
     */
    static final int IPv4 = 1;

    /**
     * Specify the address family: Internet Protocol, Version 6
     * @since 1.4
     */
    static final int IPv6 = 2;

    /* Specify address family preference */
    static transient boolean preferIPv6Address = false;

    static class InetAddressHolder {
        /**
         * Reserve the original application specified hostname.
         *
         * The original hostname is useful for domain-based endpoint
         * identification (see RFC 2818 and RFC 6125).  If an address
         * was created with a raw IP address, a reverse name lookup
         * may introduce endpoint identification security issue via
         * DNS forging.
         *
         * Oracle JSSE provider is using this original hostname, via
         * jdk.internal.misc.JavaNetAccess, for SSL/TLS endpoint identification.
         *
         * Note: May define a new public method in the future if necessary.
         */
        String originalHostName;

        InetAddressHolder() {}

        InetAddressHolder(String hostName, int address, int family) {
            this.originalHostName = hostName;
            this.hostName = hostName;
            this.address = address;
            this.family = family;
        }

        void init(String hostName, int family) {
            this.originalHostName = hostName;
            this.hostName = hostName;
            if (family != -1) {
                this.family = family;
            }
        }

        String hostName;

        String getHostName() {
            return hostName;
        }

        String getOriginalHostName() {
            return originalHostName;
        }

        /**
         * Holds a 32-bit IPv4 address.
         */
        int address;

        int getAddress() {
            return address;
        }

        /**
         * Specifies the address family type, for instance, '1' for IPv4
         * addresses, and '2' for IPv6 addresses.
         */
        int family;

        int getFamily() {
            return family;
        }
    }

    /* Used to store the serializable fields of InetAddress */
    final transient InetAddressHolder holder;

    InetAddressHolder holder() {
        return holder;
    }

    /* Used to store the name service provider */
    private static List<NameService> nameServices = null;

    /* Used to store the best available hostname */
    private transient String canonicalHostName = null;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 3286316764910316507L;

    /*
     * Load net library into runtime, and perform initializations.
     */
    static {
        preferIPv6Address = java.security.AccessController.doPrivileged(
            new GetBooleanAction("java.net.preferIPv6Addresses")).booleanValue();
        AccessController.doPrivileged(
            new java.security.PrivilegedAction<>() {
                public Void run() {
                    System.loadLibrary("net");
                    return null;
                }
            });
        SharedSecrets.setJavaNetInetAddressAccess(
                new JavaNetInetAddressAccess() {
                    public String getOriginalHostName(InetAddress ia) {
                        return ia.holder.getOriginalHostName();
                    }
                }
        );
        init();
    }

    /**
     * Constructor for the Socket.accept() method.
     * This creates an empty InetAddress, which is filled in by
     * the accept() method.  This InetAddress, however, is not
     * put in the address cache, since it is not created by name.
     */
    InetAddress() {
        holder = new InetAddressHolder();
    }

    /**
     * Replaces the de-serialized object with an Inet4Address object.
     *
     * @return the alternate object to the de-serialized object.
     *
     * @throws ObjectStreamException if a new object replacing this
     * object could not be created
     */
    private Object readResolve() throws ObjectStreamException {
        // will replace the deserialized 'this' object
        return new Inet4Address(holder().getHostName(), holder().getAddress());
    }

    /**
     * Utility routine to check if the InetAddress is an
     * IP multicast address.
     * @return a {@code boolean} indicating if the InetAddress is
     * an IP multicast address
     * @since   1.1
     */
    public boolean isMulticastAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress in a wildcard address.
     * @return a {@code boolean} indicating if the Inetaddress is
     *         a wildcard address.
     * @since 1.4
     */
    public boolean isAnyLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a loopback address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a loopback address; or false otherwise.
     * @since 1.4
     */
    public boolean isLoopbackAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is an link local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a link local address; or false if address is not a link local unicast address.
     * @since 1.4
     */
    public boolean isLinkLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a site local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a site local address; or false if address is not a site local unicast address.
     * @since 1.4
     */
    public boolean isSiteLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has global scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of global scope, false if it is not
     *         of global scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCGlobal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has node scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of node-local scope, false if it is not
     *         of node-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCNodeLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has link scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of link-local scope, false if it is not
     *         of link-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCLinkLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has site scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of site-local scope, false if it is not
     *         of site-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCSiteLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has organization scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of organization-local scope,
     *         false if it is not of organization-local scope
     *         or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCOrgLocal() {
        return false;
    }


    /**
     * Test whether that address is reachable. Best effort is made by the
     * implementation to try to reach the host, but firewalls and server
     * configuration may block requests resulting in a unreachable status
     * while some specific ports may be accessible.
     * A typical implementation will use ICMP ECHO REQUESTs if the
     * privilege can be obtained, otherwise it will try to establish
     * a TCP connection on port 7 (Echo) of the destination host.
     * <p>
     * The timeout value, in milliseconds, indicates the maximum amount of time
     * the try should take. If the operation times out before getting an
     * answer, the host is deemed unreachable. A negative value will result
     * in an IllegalArgumentException being thrown.
     *
     * @param   timeout the time, in milliseconds, before the call aborts
     * @return a {@code boolean} indicating if the address is reachable.
     * @throws IOException if a network error occurs
     * @throws  IllegalArgumentException if {@code timeout} is negative.
     * @since 1.5
     */
    public boolean isReachable(int timeout) throws IOException {
        return isReachable(null, 0 , timeout);
    }

    /**
     * Test whether that address is reachable. Best effort is made by the
     * implementation to try to reach the host, but firewalls and server
     * configuration may block requests resulting in a unreachable status
     * while some specific ports may be accessible.
     * A typical implementation will use ICMP ECHO REQUESTs if the
     * privilege can be obtained, otherwise it will try to establish
     * a TCP connection on port 7 (Echo) of the destination host.
     * <p>
     * The {@code network interface} and {@code ttl} parameters
     * let the caller specify which network interface the test will go through
     * and the maximum number of hops the packets should go through.
     * A negative value for the {@code ttl} will result in an
     * IllegalArgumentException being thrown.
     * <p>
     * The timeout value, in milliseconds, indicates the maximum amount of time
     * the try should take. If the operation times out before getting an
     * answer, the host is deemed unreachable. A negative value will result
     * in an IllegalArgumentException being thrown.
     *
     * @param   netif   the NetworkInterface through which the
     *                    test will be done, or null for any interface
     * @param   ttl     the maximum numbers of hops to try or 0 for the
     *                  default
     * @param   timeout the time, in milliseconds, before the call aborts
     * @throws  IllegalArgumentException if either {@code timeout}
     *                          or {@code ttl} are negative.
     * @return a {@code boolean}indicating if the address is reachable.
     * @throws IOException if a network error occurs
     * @since 1.5
     */
    public boolean isReachable(NetworkInterface netif, int ttl,
                               int timeout) throws IOException {
        if (ttl < 0)
            throw new IllegalArgumentException("ttl can't be negative");
        if (timeout < 0)
            throw new IllegalArgumentException("timeout can't be negative");

        return impl.isReachable(this, timeout, netif, ttl);
    }

    /**
     * Gets the host name for this IP address.
     *
     * <p>If this InetAddress was created with a host name,
     * this host name will be remembered and returned;
     * otherwise, a reverse name lookup will be performed
     * and the result will be returned based on the system
     * configured name lookup service. If a lookup of the name service
     * is required, call
     * {@link #getCanonicalHostName() getCanonicalHostName}.
     *
     * <p>If there is a security manager, its
     * {@code checkConnect} method is first called
     * with the hostname and {@code -1}
     * as its arguments to see if the operation is allowed.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the host name for this IP address, or if the operation
     *    is not allowed by the security check, the textual
     *    representation of the IP address.
     *
     * @see InetAddress#getCanonicalHostName
     * @see SecurityManager#checkConnect
     */
    public String getHostName() {
        return getHostName(true);
    }

    /**
     * Returns the hostname for this address.
     * If the host is equal to null, then this address refers to any
     * of the local machine's available network addresses.
     * this is package private so SocketPermission can make calls into
     * here without a security check.
     *
     * <p>If there is a security manager, this method first
     * calls its {@code checkConnect} method
     * with the hostname and {@code -1}
     * as its arguments to see if the calling code is allowed to know
     * the hostname for this IP address, i.e., to connect to the host.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the host name for this IP address, or if the operation
     *    is not allowed by the security check, the textual
     *    representation of the IP address.
     *
     * @param check make security check if true
     *
     * @see SecurityManager#checkConnect
     */
    String getHostName(boolean check) {
        if (holder().getHostName() == null) {
            holder().hostName = InetAddress.getHostFromNameService(this, check);
        }
        return holder().getHostName();
    }

    /**
     * Gets the fully qualified domain name for this IP address.
     * Best effort method, meaning we may not be able to return
     * the FQDN depending on the underlying system configuration.
     *
     * <p>If there is a security manager, this method first
     * calls its {@code checkConnect} method
     * with the hostname and {@code -1}
     * as its arguments to see if the calling code is allowed to know
     * the hostname for this IP address, i.e., to connect to the host.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the fully qualified domain name for this IP address,
     *    or if the operation is not allowed by the security check,
     *    the textual representation of the IP address.
     *
     * @see SecurityManager#checkConnect
     *
     * @since 1.4
     */
    public String getCanonicalHostName() {
        if (canonicalHostName == null) {
            canonicalHostName =
                InetAddress.getHostFromNameService(this, true);
        }
        return canonicalHostName;
    }

    /**
     * Returns the hostname for this address.
     *
     * <p>If there is a security manager, this method first
     * calls its {@code checkConnect} method
     * with the hostname and {@code -1}
     * as its arguments to see if the calling code is allowed to know
     * the hostname for this IP address, i.e., to connect to the host.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the host name for this IP address, or if the operation
     *    is not allowed by the security check, the textual
     *    representation of the IP address.
     *
     * @param check make security check if true
     *
     * @see SecurityManager#checkConnect
     */
    private static String getHostFromNameService(InetAddress addr, boolean check) {
        String host = null;
        for (NameService nameService : nameServices) {
            try {
                // first lookup the hostname
                host = nameService.getHostByAddr(addr.getAddress());

                /* check to see if calling code is allowed to know
                 * the hostname for this IP address, ie, connect to the host
                 */
                if (check) {
                    SecurityManager sec = System.getSecurityManager();
                    if (sec != null) {
                        sec.checkConnect(host, -1);
                    }
                }

                /* now get all the IP addresses for this hostname,
                 * and make sure one of them matches the original IP
                 * address. We do this to try and prevent spoofing.
                 */

                InetAddress[] arr = InetAddress.getAllByName0(host, check);
                boolean ok = false;

                if(arr != null) {
                    for(int i = 0; !ok && i < arr.length; i++) {
                        ok = addr.equals(arr[i]);
                    }
                }

                //XXX: if it looks a spoof just return the address?
                if (!ok) {
                    host = addr.getHostAddress();
                    return host;
                }

                break;

            } catch (SecurityException e) {
                host = addr.getHostAddress();
                break;
            } catch (UnknownHostException e) {
                host = addr.getHostAddress();
                // let next provider resolve the hostname
            }
        }

        return host;
    }

    /**
     * Returns the raw IP address of this {@code InetAddress}
     * object. The result is in network byte order: the highest order
     * byte of the address is in {@code getAddress()[0]}.
     *
     * @return  the raw IP address of this object.
     */
    public byte[] getAddress() {
        return null;
    }

    /**
     * Returns the IP address string in textual presentation.
     *
     * @return  the raw IP address in a string format.
     * @since   1.0.2
     */
    public String getHostAddress() {
        return null;
     }

    /**
     * Returns a hashcode for this IP address.
     *
     * @return  a hash code value for this IP address.
     */
    public int hashCode() {
        return -1;
    }

    /**
     * Compares this object against the specified object.
     * The result is {@code true} if and only if the argument is
     * not {@code null} and it represents the same IP address as
     * this object.
     * <p>
     * Two instances of {@code InetAddress} represent the same IP
     * address if the length of the byte arrays returned by
     * {@code getAddress} is the same for both, and each of the
     * array components is the same for the byte arrays.
     *
     * @param   obj   the object to compare against.
     * @return  {@code true} if the objects are the same;
     *          {@code false} otherwise.
     * @see     java.net.InetAddress#getAddress()
     */
    public boolean equals(Object obj) {
        return false;
    }

    /**
     * Converts this IP address to a {@code String}. The
     * string returned is of the form: hostname / literal IP
     * address.
     *
     * If the host name is unresolved, no reverse name service lookup
     * is performed. The hostname part will be represented by an empty string.
     *
     * @return  a string representation of this IP address.
     */
    public String toString() {
        String hostName = holder().getHostName();
        return ((hostName != null) ? hostName : "")
            + "/" + getHostAddress();
    }

    // mapping from host name to Addresses - either NameServiceAddresses (while
    // still being looked-up by NameService(s)) or CachedAddresses when cached
    private static final ConcurrentMap<String, Addresses> cache =
        new ConcurrentHashMap<>();

    // CachedAddresses that have to expire are kept ordered in this NavigableSet
    // which is scanned on each access
    private static final NavigableSet<CachedAddresses> expirySet =
        new ConcurrentSkipListSet<>();

    // common interface
    private interface Addresses {
        InetAddress[] get() throws UnknownHostException;
    }

    // a holder for cached addresses with required metadata
    private static final class CachedAddresses  implements Addresses, Comparable<CachedAddresses> {
        private static final AtomicLong seq = new AtomicLong();
        final String host;
        final InetAddress[] inetAddresses;
        final long expiryTime; // time of expiry (in terms of System.nanoTime())
        final long id = seq.incrementAndGet(); // each instance is unique

        CachedAddresses(String host, InetAddress[] inetAddresses, long expiryTime) {
            this.host = host;
            this.inetAddresses = inetAddresses;
            this.expiryTime = expiryTime;
        }

        @Override
        public InetAddress[] get() throws UnknownHostException {
            if (inetAddresses == null) {
                throw new UnknownHostException(host);
            }
            return inetAddresses;
        }

        @Override
        public int compareTo(CachedAddresses other) {
            // natural order is expiry time -
            // compare difference of expiry times rather than
            // expiry times directly, to avoid possible overflow.
            // (see System.nanoTime() recommendations...)
            long diff = this.expiryTime - other.expiryTime;
            if (diff < 0L) return -1;
            if (diff > 0L) return 1;
            // ties are broken using unique id
            return Long.compare(this.id, other.id);
        }
    }

    // a name service lookup based Addresses implementation which replaces itself
    // in cache when the result is obtained
    private static final class NameServiceAddresses implements Addresses {
        private final String host;
        private final InetAddress reqAddr;

        NameServiceAddresses(String host, InetAddress reqAddr) {
            this.host = host;
            this.reqAddr = reqAddr;
        }

        @Override
        public InetAddress[] get() throws UnknownHostException {
            Addresses addresses;
            // only one thread is doing lookup to name service
            // for particular host at any time.
            synchronized (this) {
                // re-check that we are still us + re-install us if slot empty
                addresses = cache.putIfAbsent(host, this);
                if (addresses == null) {
                    // this can happen when we were replaced by CachedAddresses in
                    // some other thread, then CachedAddresses expired and were
                    // removed from cache while we were waiting for lock...
                    addresses = this;
                }
                // still us ?
                if (addresses == this) {
                    // lookup name services
                    InetAddress[] inetAddresses;
                    UnknownHostException ex;
                    int cachePolicy;
                    try {
                        inetAddresses = getAddressesFromNameService(host, reqAddr);
                        ex = null;
                        cachePolicy = InetAddressCachePolicy.get();
                    } catch (UnknownHostException uhe) {
                        inetAddresses = null;
                        ex = uhe;
                        cachePolicy = InetAddressCachePolicy.getNegative();
                    }
                    // remove or replace us with cached addresses according to cachePolicy
                    if (cachePolicy == InetAddressCachePolicy.NEVER) {
                        cache.remove(host, this);
                    } else {
                        CachedAddresses cachedAddresses = new CachedAddresses(
                            host,
                            inetAddresses,
                            cachePolicy == InetAddressCachePolicy.FOREVER
                            ? 0L
                            // cachePolicy is in [s] - we need [ns]
                            : System.nanoTime() + 1000_000_000L * cachePolicy
                        );
                        if (cache.replace(host, this, cachedAddresses) &&
                            cachePolicy != InetAddressCachePolicy.FOREVER) {
                            // schedule expiry
                            expirySet.add(cachedAddresses);
                        }
                    }
                    if (inetAddresses == null) {
                        throw ex == null ? new UnknownHostException(host) : ex;
                    }
                    return inetAddresses;
                }
                // else addresses != this
            }
            // delegate to different addresses when we are already replaced
            // but outside of synchronized block to avoid any chance of dead-locking
            return addresses.get();
        }
    }

    static InetAddressImpl  impl;

    private static NameService createNSProvider(String provider) {
        if (provider == null)
            return null;

        NameService nameService = null;
        if (provider.equals("default")) {
            // initialize the default name service
            nameService = new NameService() {
                public InetAddress[] lookupAllHostAddr(String host)
                    throws UnknownHostException {
                    return impl.lookupAllHostAddr(host);
                }
                public String getHostByAddr(byte[] addr)
                    throws UnknownHostException {
                    return impl.getHostByAddr(addr);
                }
            };
        } else {
            final String providerName = provider;
            try {
                nameService = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction<>() {
                        public NameService run() {
                            Iterator<NameServiceDescriptor> itr =
                                ServiceLoader.load(NameServiceDescriptor.class)
                                    .iterator();
                            while (itr.hasNext()) {
                                NameServiceDescriptor nsd = itr.next();
                                if (providerName.
                                    equalsIgnoreCase(nsd.getType()+","
                                        +nsd.getProviderName())) {
                                    try {
                                        return nsd.createNameService();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        System.err.println(
                                            "Cannot create name service:"
                                             +providerName+": " + e);
                                    }
                                }
                            }

                            return null;
                        }
                    }
                );
            } catch (java.security.PrivilegedActionException e) {
            }
        }

        return nameService;
    }

    static {
        // create the impl
        impl = InetAddressImplFactory.create();

        // get name service if provided and requested
        String provider = null;;
        String propPrefix = "sun.net.spi.nameservice.provider.";
        int n = 1;
        nameServices = new ArrayList<>();
        provider = AccessController.doPrivileged(
                new GetPropertyAction(propPrefix + n));
        while (provider != null) {
            NameService ns = createNSProvider(provider);
            if (ns != null)
                nameServices.add(ns);

            n++;
            provider = AccessController.doPrivileged(
                    new GetPropertyAction(propPrefix + n));
        }

        // if not designate any name services provider,
        // create a default one
        if (nameServices.size() == 0) {
            NameService ns = createNSProvider("default");
            nameServices.add(ns);
        }
    }

    /**
     * Creates an InetAddress based on the provided host name and IP address.
     * No name service is checked for the validity of the address.
     *
     * <p> The host name can either be a machine name, such as
     * "{@code java.sun.com}", or a textual representation of its IP
     * address.
     * <p> No validity checking is done on the host name either.
     *
     * <p> If addr specifies an IPv4 address an instance of Inet4Address
     * will be returned; otherwise, an instance of Inet6Address
     * will be returned.
     *
     * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
     * must be 16 bytes long
     *
     * @param host the specified host
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @exception  UnknownHostException  if IP address is of illegal length
     * @since 1.4
     */
    public static InetAddress getByAddress(String host, byte[] addr)
        throws UnknownHostException {
        if (host != null && host.length() > 0 && host.charAt(0) == '[') {
            if (host.charAt(host.length()-1) == ']') {
                host = host.substring(1, host.length() -1);
            }
        }
        if (addr != null) {
            if (addr.length == Inet4Address.INADDRSZ) {
                return new Inet4Address(host, addr);
            } else if (addr.length == Inet6Address.INADDRSZ) {
                byte[] newAddr
                    = IPAddressUtil.convertFromIPv4MappedAddress(addr);
                if (newAddr != null) {
                    return new Inet4Address(host, newAddr);
                } else {
                    return new Inet6Address(host, addr);
                }
            }
        }
        throw new UnknownHostException("addr is of illegal length");
    }


    /**
     * Determines the IP address of a host, given the host's name.
     *
     * <p> The host name can either be a machine name, such as
     * "{@code java.sun.com}", or a textual representation of its
     * IP address. If a literal IP address is supplied, only the
     * validity of the address format is checked.
     *
     * <p> For {@code host} specified in literal IPv6 address,
     * either the form defined in RFC 2732 or the literal IPv6 address
     * format defined in RFC 2373 is accepted. IPv6 scoped addresses are also
     * supported. See <a href="Inet6Address.html#scoped">here</a> for a description of IPv6
     * scoped addresses.
     *
     * <p> If the host is {@code null} then an {@code InetAddress}
     * representing an address of the loopback interface is returned.
     * See <a href="http://www.ietf.org/rfc/rfc3330.txt">RFC&nbsp;3330</a>
     * section&nbsp;2 and <a href="http://www.ietf.org/rfc/rfc2373.txt">RFC&nbsp;2373</a>
     * section&nbsp;2.5.3. </p>
     *
     * @param      host   the specified host, or {@code null}.
     * @return     an IP address for the given host name.
     * @exception  UnknownHostException  if no IP address for the
     *               {@code host} could be found, or if a scope_id was specified
     *               for a global IPv6 address.
     * @exception  SecurityException if a security manager exists
     *             and its checkConnect method doesn't allow the operation
     */
    public static InetAddress getByName(String host)
        throws UnknownHostException {
        return InetAddress.getAllByName(host)[0];
    }

    // called from deployment cache manager
    private static InetAddress getByName(String host, InetAddress reqAddr)
        throws UnknownHostException {
        return InetAddress.getAllByName(host, reqAddr)[0];
    }

    /**
     * Given the name of a host, returns an array of its IP addresses,
     * based on the configured name service on the system.
     *
     * <p> The host name can either be a machine name, such as
     * "{@code java.sun.com}", or a textual representation of its IP
     * address. If a literal IP address is supplied, only the
     * validity of the address format is checked.
     *
     * <p> For {@code host} specified in <i>literal IPv6 address</i>,
     * either the form defined in RFC 2732 or the literal IPv6 address
     * format defined in RFC 2373 is accepted. A literal IPv6 address may
     * also be qualified by appending a scoped zone identifier or scope_id.
     * The syntax and usage of scope_ids is described
     * <a href="Inet6Address.html#scoped">here</a>.
     * <p> If the host is {@code null} then an {@code InetAddress}
     * representing an address of the loopback interface is returned.
     * See <a href="http://www.ietf.org/rfc/rfc3330.txt">RFC&nbsp;3330</a>
     * section&nbsp;2 and <a href="http://www.ietf.org/rfc/rfc2373.txt">RFC&nbsp;2373</a>
     * section&nbsp;2.5.3. </p>
     *
     * <p> If there is a security manager and {@code host} is not
     * null and {@code host.length() } is not equal to zero, the
     * security manager's
     * {@code checkConnect} method is called
     * with the hostname and {@code -1}
     * as its arguments to see if the operation is allowed.
     *
     * @param      host   the name of the host, or {@code null}.
     * @return     an array of all the IP addresses for a given host name.
     *
     * @exception  UnknownHostException  if no IP address for the
     *               {@code host} could be found, or if a scope_id was specified
     *               for a global IPv6 address.
     * @exception  SecurityException  if a security manager exists and its
     *               {@code checkConnect} method doesn't allow the operation.
     *
     * @see SecurityManager#checkConnect
     */
    public static InetAddress[] getAllByName(String host)
        throws UnknownHostException {
        return getAllByName(host, null);
    }

    private static InetAddress[] getAllByName(String host, InetAddress reqAddr)
        throws UnknownHostException {

        if (host == null || host.length() == 0) {
            InetAddress[] ret = new InetAddress[1];
            ret[0] = impl.loopbackAddress();
            return ret;
        }

        boolean ipv6Expected = false;
        if (host.charAt(0) == '[') {
            // This is supposed to be an IPv6 literal
            if (host.length() > 2 && host.charAt(host.length()-1) == ']') {
                host = host.substring(1, host.length() -1);
                ipv6Expected = true;
            } else {
                // This was supposed to be a IPv6 address, but it's not!
                throw new UnknownHostException(host + ": invalid IPv6 address");
            }
        }

        // if host is an IP address, we won't do further lookup
        if (Character.digit(host.charAt(0), 16) != -1
            || (host.charAt(0) == ':')) {
            byte[] addr = null;
            int numericZone = -1;
            String ifname = null;
            // see if it is IPv4 address
            addr = IPAddressUtil.textToNumericFormatV4(host);
            if (addr == null) {
                // This is supposed to be an IPv6 literal
                // Check if a numeric or string zone id is present
                int pos;
                if ((pos=host.indexOf ('%')) != -1) {
                    numericZone = checkNumericZone (host);
                    if (numericZone == -1) { /* remainder of string must be an ifname */
                        ifname = host.substring (pos+1);
                    }
                }
                if ((addr = IPAddressUtil.textToNumericFormatV6(host)) == null && host.contains(":")) {
                    throw new UnknownHostException(host + ": invalid IPv6 address");
                }
            } else if (ipv6Expected) {
                // Means an IPv4 litteral between brackets!
                throw new UnknownHostException("["+host+"]");
            }
            InetAddress[] ret = new InetAddress[1];
            if(addr != null) {
                if (addr.length == Inet4Address.INADDRSZ) {
                    ret[0] = new Inet4Address(null, addr);
                } else {
                    if (ifname != null) {
                        ret[0] = new Inet6Address(null, addr, ifname);
                    } else {
                        ret[0] = new Inet6Address(null, addr, numericZone);
                    }
                }
                return ret;
            }
        } else if (ipv6Expected) {
            // We were expecting an IPv6 Litteral, but got something else
            throw new UnknownHostException("["+host+"]");
        }
        return getAllByName0(host, reqAddr, true, true);
    }

    /**
     * Returns the loopback address.
     * <p>
     * The InetAddress returned will represent the IPv4
     * loopback address, 127.0.0.1, or the IPv6 loopback
     * address, ::1. The IPv4 loopback address returned
     * is only one of many in the form 127.*.*.*
     *
     * @return  the InetAddress loopback instance.
     * @since 1.7
     */
    public static InetAddress getLoopbackAddress() {
        return impl.loopbackAddress();
    }


    /**
     * check if the literal address string has %nn appended
     * returns -1 if not, or the numeric value otherwise.
     *
     * %nn may also be a string that represents the displayName of
     * a currently available NetworkInterface.
     */
    private static int checkNumericZone (String s) throws UnknownHostException {
        int percent = s.indexOf ('%');
        int slen = s.length();
        int digit, zone=0;
        if (percent == -1) {
            return -1;
        }
        for (int i=percent+1; i<slen; i++) {
            char c = s.charAt(i);
            if (c == ']') {
                if (i == percent+1) {
                    /* empty per-cent field */
                    return -1;
                }
                break;
            }
            if ((digit = Character.digit (c, 10)) < 0) {
                return -1;
            }
            zone = (zone * 10) + digit;
        }
        return zone;
    }

    private static InetAddress[] getAllByName0 (String host)
        throws UnknownHostException
    {
        return getAllByName0(host, true);
    }

    /**
     * package private so SocketPermission can call it
     */
    static InetAddress[] getAllByName0 (String host, boolean check)
        throws UnknownHostException  {
        return getAllByName0 (host, null, check, true);
    }

    /**
     * Designated lookup method.
     *
     * @param host host name to look up
     * @param reqAddr requested address to be the 1st in returned array
     * @param check perform security check
     * @param useCache use cached value if not expired else always
     *                 perform name service lookup (and cache the result)
     * @return array of InetAddress(es)
     * @throws UnknownHostException if host name is not found
     */
    private static InetAddress[] getAllByName0(String host,
                                               InetAddress reqAddr,
                                               boolean check,
                                               boolean useCache)
        throws UnknownHostException  {

        /* If it gets here it is presumed to be a hostname */

        /* make sure the connection to the host is allowed, before we
         * give out a hostname
         */
        if (check) {
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkConnect(host, -1);
            }
        }

        // remove expired addresses from cache - expirySet keeps them ordered
        // by expiry time so we only need to iterate the prefix of the NavigableSet...
        long now = System.nanoTime();
        for (CachedAddresses caddrs : expirySet) {
            // compare difference of time instants rather than
            // time instants directly, to avoid possible overflow.
            // (see System.nanoTime() recommendations...)
            if ((caddrs.expiryTime - now) < 0L) {
                // ConcurrentSkipListSet uses weakly consistent iterator,
                // so removing while iterating is OK...
                if (expirySet.remove(caddrs)) {
                    // ... remove from cache
                    cache.remove(caddrs.host, caddrs);
                }
            } else {
                // we encountered 1st element that expires in future
                break;
            }
        }

        // look-up or remove from cache
        Addresses addrs;
        if (useCache) {
            addrs = cache.get(host);
        } else {
            addrs = cache.remove(host);
            if (addrs != null) {
                if (addrs instanceof CachedAddresses) {
                    // try removing from expirySet too if CachedAddresses
                    expirySet.remove(addrs);
                }
                addrs = null;
            }
        }

        if (addrs == null) {
            // create a NameServiceAddresses instance which will look up
            // the name service and install it within cache...
            Addresses oldAddrs = cache.putIfAbsent(
                host,
                addrs = new NameServiceAddresses(host, reqAddr)
            );
            if (oldAddrs != null) { // lost putIfAbsent race
                addrs = oldAddrs;
            }
        }

        // ask Addresses to get an array of InetAddress(es) and clone it
        return addrs.get().clone();
    }

    static InetAddress[] getAddressesFromNameService(String host, InetAddress reqAddr)
        throws UnknownHostException
    {
        InetAddress[] addresses = null;
        UnknownHostException ex = null;

        for (NameService nameService : nameServices) {
            try {
                addresses = nameService.lookupAllHostAddr(host);
                break;
            } catch (UnknownHostException uhe) {
                if (host.equalsIgnoreCase("localhost")) {
                    addresses = new InetAddress[] { impl.loopbackAddress() };
                    break;
                }
                else {
                    ex = uhe;
                }
            }
        }

        if (addresses == null) {
            throw ex == null ? new UnknownHostException(host) : ex;
        }

        // More to do?
        if (reqAddr != null && addresses.length > 1 && !addresses[0].equals(reqAddr)) {
            // Find it?
            int i = 1;
            for (; i < addresses.length; i++) {
                if (addresses[i].equals(reqAddr)) {
                    break;
                }
            }
            // Rotate
            if (i < addresses.length) {
                InetAddress tmp, tmp2 = reqAddr;
                for (int j = 0; j < i; j++) {
                    tmp = addresses[j];
                    addresses[j] = tmp2;
                    tmp2 = tmp;
                }
                addresses[i] = tmp2;
            }
        }

        return addresses;
    }

    /**
     * Returns an {@code InetAddress} object given the raw IP address .
     * The argument is in network byte order: the highest order
     * byte of the address is in {@code getAddress()[0]}.
     *
     * <p> This method doesn't block, i.e. no reverse name service lookup
     * is performed.
     *
     * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
     * must be 16 bytes long
     *
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @exception  UnknownHostException  if IP address is of illegal length
     * @since 1.4
     */
    public static InetAddress getByAddress(byte[] addr)
        throws UnknownHostException {
        return getByAddress(null, addr);
    }

    private static final class CachedLocalHost {
        final String host;
        final InetAddress addr;
        final long expiryTime = System.nanoTime() + 5000_000_000L; // now + 5s;

        CachedLocalHost(String host, InetAddress addr) {
            this.host = host;
            this.addr = addr;
        }
    }

    private static volatile CachedLocalHost cachedLocalHost;

    /**
     * Returns the address of the local host. This is achieved by retrieving
     * the name of the host from the system, then resolving that name into
     * an {@code InetAddress}.
     *
     * <P>Note: The resolved address may be cached for a short period of time.
     * </P>
     *
     * <p>If there is a security manager, its
     * {@code checkConnect} method is called
     * with the local host name and {@code -1}
     * as its arguments to see if the operation is allowed.
     * If the operation is not allowed, an InetAddress representing
     * the loopback address is returned.
     *
     * @return     the address of the local host.
     *
     * @exception  UnknownHostException  if the local host name could not
     *             be resolved into an address.
     *
     * @see SecurityManager#checkConnect
     * @see java.net.InetAddress#getByName(java.lang.String)
     */
    public static InetAddress getLocalHost() throws UnknownHostException {

        SecurityManager security = System.getSecurityManager();
        try {
            // is cached data still valid?
            CachedLocalHost clh = cachedLocalHost;
            if (clh != null && (clh.expiryTime - System.nanoTime()) >= 0L) {
                if (security != null) {
                    security.checkConnect(clh.host, -1);
                }
                return clh.addr;
            }

            String local = impl.getLocalHostName();

            if (security != null) {
                security.checkConnect(local, -1);
            }

            InetAddress localAddr;
            if (local.equals("localhost")) {
                // shortcut for "localhost" host name
                localAddr = impl.loopbackAddress();
            } else {
                // call getAllByName0 without security checks and
                // without using cached data
                try {
                    localAddr = getAllByName0(local, null, false, false)[0];
                } catch (UnknownHostException uhe) {
                    // Rethrow with a more informative error message.
                    UnknownHostException uhe2 =
                        new UnknownHostException(local + ": " +
                                                 uhe.getMessage());
                    uhe2.initCause(uhe);
                    throw uhe2;
                }
            }
            cachedLocalHost = new CachedLocalHost(local, localAddr);
            return localAddr;
        } catch (java.lang.SecurityException e) {
            return impl.loopbackAddress();
        }
    }

    /**
     * Perform class load-time initializations.
     */
    private static native void init();


    /*
     * Returns the InetAddress representing anyLocalAddress
     * (typically 0.0.0.0 or ::0)
     */
    static InetAddress anyLocalAddress() {
        return impl.anyLocalAddress();
    }

    /*
     * Load and instantiate an underlying impl class
     */
    static InetAddressImpl loadImpl(String implName) {
        Object impl = null;

        /*
         * Property "impl.prefix" will be prepended to the classname
         * of the implementation object we instantiate, to which we
         * delegate the real work (like native methods).  This
         * property can vary across implementations of the java.
         * classes.  The default is an empty String "".
         */
        String prefix = AccessController.doPrivileged(
                      new GetPropertyAction("impl.prefix", ""));
        try {
            impl = Class.forName("java.net." + prefix + implName).newInstance();
        } catch (ClassNotFoundException e) {
            System.err.println("Class not found: java.net." + prefix +
                               implName + ":\ncheck impl.prefix property " +
                               "in your properties file.");
        } catch (InstantiationException e) {
            System.err.println("Could not instantiate: java.net." + prefix +
                               implName + ":\ncheck impl.prefix property " +
                               "in your properties file.");
        } catch (IllegalAccessException e) {
            System.err.println("Cannot access class: java.net." + prefix +
                               implName + ":\ncheck impl.prefix property " +
                               "in your properties file.");
        }

        if (impl == null) {
            try {
                impl = Class.forName(implName).newInstance();
            } catch (Exception e) {
                throw new Error("System property impl.prefix incorrect");
            }
        }

        return (InetAddressImpl) impl;
    }

    private void readObjectNoData (ObjectInputStream s) throws
                         IOException, ClassNotFoundException {
        if (getClass().getClassLoader() != null) {
            throw new SecurityException ("invalid address type");
        }
    }

    private static final long FIELDS_OFFSET;
    private static final jdk.internal.misc.Unsafe UNSAFE;

    static {
        try {
            jdk.internal.misc.Unsafe unsafe = jdk.internal.misc.Unsafe.getUnsafe();
            FIELDS_OFFSET = unsafe.objectFieldOffset(
                InetAddress.class.getDeclaredField("holder")
            );
            UNSAFE = unsafe;
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    private void readObject (ObjectInputStream s) throws
                         IOException, ClassNotFoundException {
        if (getClass().getClassLoader() != null) {
            throw new SecurityException ("invalid address type");
        }
        GetField gf = s.readFields();
        String host = (String)gf.get("hostName", null);
        int address= gf.get("address", 0);
        int family= gf.get("family", 0);
        InetAddressHolder h = new InetAddressHolder(host, address, family);
        UNSAFE.putObject(this, FIELDS_OFFSET, h);
    }

    /* needed because the serializable fields no longer exist */

    /**
     * @serialField hostName String
     * @serialField address int
     * @serialField family int
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("hostName", String.class),
        new ObjectStreamField("address", int.class),
        new ObjectStreamField("family", int.class),
    };

    private void writeObject (ObjectOutputStream s) throws
                        IOException {
        if (getClass().getClassLoader() != null) {
            throw new SecurityException ("invalid address type");
        }
        PutField pf = s.putFields();
        pf.put("hostName", holder().getHostName());
        pf.put("address", holder().getAddress());
        pf.put("family", holder().getFamily());
        s.writeFields();
    }
}

/*
 * Simple factory to create the impl
 */
class InetAddressImplFactory {

    static InetAddressImpl create() {
        return InetAddress.loadImpl(isIPv6Supported() ?
                                    "Inet6AddressImpl" : "Inet4AddressImpl");
    }

    static native boolean isIPv6Supported();
}
