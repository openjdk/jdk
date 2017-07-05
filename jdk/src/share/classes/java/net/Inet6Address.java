/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Enumeration;

/**
 * This class represents an Internet Protocol version 6 (IPv6) address.
 * Defined by <a href="http://www.ietf.org/rfc/rfc2373.txt">
 * <i>RFC&nbsp;2373: IP Version 6 Addressing Architecture</i></a>.
 *
 * <h3> <A NAME="format">Textual representation of IP addresses</a> </h3>
 *
 * Textual representation of IPv6 address used as input to methods
 * takes one of the following forms:
 *
 * <ol>
 *   <li><p> <A NAME="lform">The preferred form</a> is x:x:x:x:x:x:x:x,
 *   where the 'x's are
 *   the hexadecimal values of the eight 16-bit pieces of the
 *   address. This is the full form.  For example,
 *
 *   <blockquote><table cellpadding=0 cellspacing=0 summary="layout">
 *   <tr><td><tt>1080:0:0:0:8:800:200C:417A</tt><td></tr>
 *   </table></blockquote>
 *
 *   <p> Note that it is not necessary to write the leading zeros in
 *   an individual field. However, there must be at least one numeral
 *   in every field, except as described below.</li>
 *
 *   <li><p> Due to some methods of allocating certain styles of IPv6
 *   addresses, it will be common for addresses to contain long
 *   strings of zero bits. In order to make writing addresses
 *   containing zero bits easier, a special syntax is available to
 *   compress the zeros. The use of "::" indicates multiple groups
 *   of 16-bits of zeros. The "::" can only appear once in an address.
 *   The "::" can also be used to compress the leading and/or trailing
 *   zeros in an address. For example,
 *
 *   <blockquote><table cellpadding=0 cellspacing=0 summary="layout">
 *   <tr><td><tt>1080::8:800:200C:417A</tt><td></tr>
 *   </table></blockquote>
 *
 *   <li><p> An alternative form that is sometimes more convenient
 *   when dealing with a mixed environment of IPv4 and IPv6 nodes is
 *   x:x:x:x:x:x:d.d.d.d, where the 'x's are the hexadecimal values
 *   of the six high-order 16-bit pieces of the address, and the 'd's
 *   are the decimal values of the four low-order 8-bit pieces of the
 *   standard IPv4 representation address, for example,
 *
 *   <blockquote><table cellpadding=0 cellspacing=0 summary="layout">
 *   <tr><td><tt>::FFFF:129.144.52.38</tt><td></tr>
 *   <tr><td><tt>::129.144.52.38</tt><td></tr>
 *   </table></blockquote>
 *
 *   <p> where "::FFFF:d.d.d.d" and "::d.d.d.d" are, respectively, the
 *   general forms of an IPv4-mapped IPv6 address and an
 *   IPv4-compatible IPv6 address. Note that the IPv4 portion must be
 *   in the "d.d.d.d" form. The following forms are invalid:
 *
 *   <blockquote><table cellpadding=0 cellspacing=0 summary="layout">
 *   <tr><td><tt>::FFFF:d.d.d</tt><td></tr>
 *   <tr><td><tt>::FFFF:d.d</tt><td></tr>
 *   <tr><td><tt>::d.d.d</tt><td></tr>
 *   <tr><td><tt>::d.d</tt><td></tr>
 *   </table></blockquote>
 *
 *   <p> The following form:
 *
 *   <blockquote><table cellpadding=0 cellspacing=0 summary="layout">
 *   <tr><td><tt>::FFFF:d</tt><td></tr>
 *   </table></blockquote>
 *
 *   <p> is valid, however it is an unconventional representation of
 *   the IPv4-compatible IPv6 address,
 *
 *   <blockquote><table cellpadding=0 cellspacing=0 summary="layout">
 *   <tr><td><tt>::255.255.0.d</tt><td></tr>
 *   </table></blockquote>
 *
 *   <p> while "::d" corresponds to the general IPv6 address
 *   "0:0:0:0:0:0:0:d".</li>
 * </ol>
 *
 * <p> For methods that return a textual representation as output
 * value, the full form is used. Inet6Address will return the full
 * form because it is unambiguous when used in combination with other
 * textual data.
 *
 * <h4> Special IPv6 address </h4>
 *
 * <blockquote>
 * <table cellspacing=2 summary="Description of IPv4-mapped address">
 * <tr><th valign=top><i>IPv4-mapped address</i></th>
 *         <td>Of the form::ffff:w.x.y.z, this IPv6 address is used to
 *         represent an IPv4 address. It allows the native program to
 *         use the same address data structure and also the same
 *         socket when communicating with both IPv4 and IPv6 nodes.
 *
 *         <p>In InetAddress and Inet6Address, it is used for internal
 *         representation; it has no functional role. Java will never
 *         return an IPv4-mapped address.  These classes can take an
 *         IPv4-mapped address as input, both in byte array and text
 *         representation. However, it will be converted into an IPv4
 *         address.</td></tr>
 * </table></blockquote>
 * <p>
 * <h4><A NAME="scoped">Textual representation of IPv6 scoped addresses</a></h4>
 *
 * <p> The textual representation of IPv6 addresses as described above can be
 * extended to specify IPv6 scoped addresses. This extension to the basic
 * addressing architecture is described in [draft-ietf-ipngwg-scoping-arch-04.txt].
 *
 * <p> Because link-local and site-local addresses are non-global, it is possible
 * that different hosts may have the same destination address and may be
 * reachable through different interfaces on the same originating system. In
 * this case, the originating system is said to be connected to multiple zones
 * of the same scope. In order to disambiguate which is the intended destination
 * zone, it is possible to append a zone identifier (or <i>scope_id</i>) to an
 * IPv6 address.
 *
 * <p> The general format for specifying the <i>scope_id</i> is the following:
 *
 * <p><blockquote><i>IPv6-address</i>%<i>scope_id</i></blockquote>
 * <p> The IPv6-address is a literal IPv6 address as described above.
 * The <i>scope_id</i> refers to an interface on the local system, and it can be
 * specified in two ways.
 * <p><ol><li><i>As a numeric identifier.</i> This must be a positive integer
 * that identifies the particular interface and scope as understood by the
 * system. Usually, the numeric values can be determined through administration
 * tools on the system. Each interface may have multiple values, one for each
 * scope. If the scope is unspecified, then the default value used is zero.</li>
 * <li><i>As a string.</i> This must be the exact string that is returned by
 * {@link java.net.NetworkInterface#getName()} for the particular interface in
 * question. When an Inet6Address is created in this way, the numeric scope-id
 * is determined at the time the object is created by querying the relevant
 * NetworkInterface.</li></ol>
 *
 * <p> Note also, that the numeric <i>scope_id</i> can be retrieved from
 * Inet6Address instances returned from the NetworkInterface class. This can be
 * used to find out the current scope ids configured on the system.
 * @since 1.4
 */

public final
class Inet6Address extends InetAddress {
    final static int INADDRSZ = 16;

    /*
     * cached scope_id - for link-local address use only.
     */
    private transient int cached_scope_id;  // 0

    /**
     * Holds a 128-bit (16 bytes) IPv6 address.
     *
     * @serial
     */
    byte[] ipaddress;

    /**
     * scope_id. The scope specified when the object is created. If the object
     * is created with an interface name, then the scope_id is not determined
     * until the time it is needed.
     */
    private int scope_id;  // 0

    /**
     * This will be set to true when the scope_id field contains a valid
     * integer scope_id.
     */
    private boolean scope_id_set;  // false

    /**
     * scoped interface. scope_id is derived from this as the scope_id of the first
     * address whose scope is the same as this address for the named interface.
     */
    private transient NetworkInterface scope_ifname;  // null

    /**
     * set if the object is constructed with a scoped
     * interface instead of a numeric scope id.
     */
    private boolean scope_ifname_set; // false;

    private static final long serialVersionUID = 6880410070516793377L;

    // Perform native initialization
    static { init(); }

    Inet6Address() {
        super();
        holder().hostName = null;
        ipaddress = new byte[INADDRSZ];
        holder().family = IPv6;
    }

    /* checking of value for scope_id should be done by caller
     * scope_id must be >= 0, or -1 to indicate not being set
     */
    Inet6Address(String hostName, byte addr[], int scope_id) {
        holder().hostName = hostName;
        if (addr.length == INADDRSZ) { // normal IPv6 address
            holder().family = IPv6;
            ipaddress = addr.clone();
        }
        if (scope_id >= 0) {
            this.scope_id = scope_id;
            scope_id_set = true;
        }
    }

    Inet6Address(String hostName, byte addr[]) {
        try {
            initif (hostName, addr, null);
        } catch (UnknownHostException e) {} /* cant happen if ifname is null */
    }

    Inet6Address (String hostName, byte addr[], NetworkInterface nif)
        throws UnknownHostException
    {
        initif (hostName, addr, nif);
    }

    Inet6Address (String hostName, byte addr[], String ifname)
        throws UnknownHostException
    {
        initstr (hostName, addr, ifname);
    }

    /**
     * Create an Inet6Address in the exact manner of {@link
     * InetAddress#getByAddress(String,byte[])} except that the IPv6 scope_id is
     * set to the value corresponding to the given interface for the address
     * type specified in <code>addr</code>. The call will fail with an
     * UnknownHostException if the given interface does not have a numeric
     * scope_id assigned for the given address type (eg. link-local or site-local).
     * See <a href="Inet6Address.html#scoped">here</a> for a description of IPv6
     * scoped addresses.
     *
     * @param host the specified host
     * @param addr the raw IP address in network byte order
     * @param nif an interface this address must be associated with.
     * @return  an Inet6Address object created from the raw IP address.
     * @throws  UnknownHostException
     *          if IP address is of illegal length, or if the interface does not
     *          have a numeric scope_id assigned for the given address type.
     *
     * @since 1.5
     */
    public static Inet6Address getByAddress(String host, byte[] addr,
                                            NetworkInterface nif)
        throws UnknownHostException
    {
        if (host != null && host.length() > 0 && host.charAt(0) == '[') {
            if (host.charAt(host.length()-1) == ']') {
                host = host.substring(1, host.length() -1);
            }
        }
        if (addr != null) {
            if (addr.length == Inet6Address.INADDRSZ) {
                return new Inet6Address(host, addr, nif);
            }
        }
        throw new UnknownHostException("addr is of illegal length");
    }

    /**
     * Create an Inet6Address in the exact manner of {@link
     * InetAddress#getByAddress(String,byte[])} except that the IPv6 scope_id is
     * set to the given numeric value. The scope_id is not checked to determine
     * if it corresponds to any interface on the system.
     * See <a href="Inet6Address.html#scoped">here</a> for a description of IPv6
     * scoped addresses.
     *
     * @param host the specified host
     * @param addr the raw IP address in network byte order
     * @param scope_id the numeric scope_id for the address.
     * @return  an Inet6Address object created from the raw IP address.
     * @throws  UnknownHostException  if IP address is of illegal length.
     *
     * @since 1.5
     */
    public static Inet6Address getByAddress(String host, byte[] addr,
                                            int scope_id)
        throws UnknownHostException
    {
        if (host != null && host.length() > 0 && host.charAt(0) == '[') {
            if (host.charAt(host.length()-1) == ']') {
                host = host.substring(1, host.length() -1);
            }
        }
        if (addr != null) {
            if (addr.length == Inet6Address.INADDRSZ) {
                return new Inet6Address(host, addr, scope_id);
            }
        }
        throw new UnknownHostException("addr is of illegal length");
    }

    private void initstr(String hostName, byte addr[], String ifname)
        throws UnknownHostException
    {
        try {
            NetworkInterface nif = NetworkInterface.getByName (ifname);
            if (nif == null) {
                throw new UnknownHostException ("no such interface " + ifname);
            }
            initif (hostName, addr, nif);
        } catch (SocketException e) {
            throw new UnknownHostException ("SocketException thrown" + ifname);
        }
    }

    private void initif(String hostName, byte addr[], NetworkInterface nif)
        throws UnknownHostException
    {
        holder().hostName = hostName;
        if (addr.length == INADDRSZ) { // normal IPv6 address
            holder().family = IPv6;
            ipaddress = addr.clone();
        }
        if (nif != null) {
            scope_ifname = nif;
            scope_id = deriveNumericScope(nif);
            scope_id_set = true;
            scope_ifname_set = true;  // for consistency
        }
    }

    /* check the two Ipv6 addresses and return false if they are both
     * non global address types, but not the same.
     * (ie. one is sitelocal and the other linklocal)
     * return true otherwise.
     */
    private boolean differentLocalAddressTypes(Inet6Address other) {
        if (isLinkLocalAddress() && !other.isLinkLocalAddress())
            return false;
        if (isSiteLocalAddress() && !other.isSiteLocalAddress())
            return false;
        return true;
    }

    private int deriveNumericScope(NetworkInterface ifc)
        throws UnknownHostException
    {
        Enumeration<InetAddress> addresses = ifc.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (!(addr instanceof Inet6Address)) {
                continue;
            }
            Inet6Address ia6_addr = (Inet6Address)addr;
            /* check if site or link local prefixes match */
            if (!differentLocalAddressTypes(ia6_addr)){
                /* type not the same, so carry on searching */
                continue;
            }
            /* found a matching address - return its scope_id */
            return ia6_addr.scope_id;
        }
        throw new UnknownHostException ("no scope_id found");
    }

    private int deriveNumericScope(String ifname) throws UnknownHostException {
        Enumeration<NetworkInterface> en;
        try {
            en = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new UnknownHostException(
                    "could not enumerate local network interfaces");
        }
        while (en.hasMoreElements()) {
            NetworkInterface ifc = en.nextElement();
            if (ifc.getName().equals(ifname)) {
                Enumeration<InetAddress> addresses = ifc.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!(addr instanceof Inet6Address)) {
                        continue;
                    }
                    Inet6Address ia6_addr = (Inet6Address)addr;
                    /* check if site or link local prefixes match */
                    if (!differentLocalAddressTypes(ia6_addr)){
                        /* type not the same, so carry on searching */
                        continue;
                    }
                    /* found a matching address - return its scope_id */
                    return ia6_addr.scope_id;
                }
            }
        }
        throw new UnknownHostException(
                "No matching address found for interface : " +ifname);
    }

    /**
     * restore the state of this object from stream
     * including the scope information, only if the
     * scoped interface name is valid on this system
     */
    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {

        if (getClass().getClassLoader() != null) {
            throw new SecurityException ("invalid address type");
        }

        s.defaultReadObject();

        if (ifname != null && !ifname.equals("")) {
            try {
                scope_ifname = NetworkInterface.getByName(ifname);
                if (scope_ifname != null) {
                    scope_ifname_set = true;
                    try {
                        scope_id = deriveNumericScope(scope_ifname);
                    } catch (UnknownHostException e) {
                        // typically should not happen, but it may be that
                        // the machine being used for deserialization has
                        // the same interface name but without IPv6 configured.
                    }
                } else {
                    /* the interface does not exist on this system, so we clear
                     * the scope information completely */
                    scope_id_set = false;
                    scope_ifname_set = false;
                    scope_id = 0;
                }
            } catch (SocketException e) {}

        }
        /* if ifname was not supplied, then the numeric info is used */

        ipaddress = ipaddress.clone();

        // Check that our invariants are satisfied
        if (ipaddress.length != INADDRSZ) {
            throw new InvalidObjectException("invalid address length: "+
                                             ipaddress.length);
        }

        if (holder().getFamily() != IPv6) {
            throw new InvalidObjectException("invalid address family type");
        }
    }

    /**
     * Utility routine to check if the InetAddress is an IP multicast
     * address. 11111111 at the start of the address identifies the
     * address as being a multicast address.
     *
     * @return a {@code boolean} indicating if the InetAddress is an IP
     *         multicast address
     *
     * @since JDK1.1
     */
    @Override
    public boolean isMulticastAddress() {
        return ((ipaddress[0] & 0xff) == 0xff);
    }

    /**
     * Utility routine to check if the InetAddress in a wildcard address.
     *
     * @return a {@code boolean} indicating if the Inetaddress is
     *         a wildcard address.
     *
     * @since 1.4
     */
    @Override
    public boolean isAnyLocalAddress() {
        byte test = 0x00;
        for (int i = 0; i < INADDRSZ; i++) {
            test |= ipaddress[i];
        }
        return (test == 0x00);
    }

    /**
     * Utility routine to check if the InetAddress is a loopback address.
     *
     * @return a {@code boolean} indicating if the InetAddress is a loopback
     *         address; or false otherwise.
     *
     * @since 1.4
     */
    @Override
    public boolean isLoopbackAddress() {
        byte test = 0x00;
        for (int i = 0; i < 15; i++) {
            test |= ipaddress[i];
        }
        return (test == 0x00) && (ipaddress[15] == 0x01);
    }

    /**
     * Utility routine to check if the InetAddress is an link local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is a link local
     *         address; or false if address is not a link local unicast address.
     *
     * @since 1.4
     */
    @Override
    public boolean isLinkLocalAddress() {
        return ((ipaddress[0] & 0xff) == 0xfe
                && (ipaddress[1] & 0xc0) == 0x80);
    }

    /**
     * Utility routine to check if the InetAddress is a site local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is a site local
     *         address; or false if address is not a site local unicast address.
     *
     * @since 1.4
     */
    @Override
    public boolean isSiteLocalAddress() {
        return ((ipaddress[0] & 0xff) == 0xfe
                && (ipaddress[1] & 0xc0) == 0xc0);
    }

    /**
     * Utility routine to check if the multicast address has global scope.
     *
     * @return a {@code boolean} indicating if the address has is a multicast
     *         address of global scope, false if it is not of global scope or
     *         it is not a multicast address
     *
     * @since 1.4
     */
    @Override
    public boolean isMCGlobal() {
        return ((ipaddress[0] & 0xff) == 0xff
                && (ipaddress[1] & 0x0f) == 0x0e);
    }

    /**
     * Utility routine to check if the multicast address has node scope.
     *
     * @return a {@code boolean} indicating if the address has is a multicast
     *         address of node-local scope, false if it is not of node-local
     *         scope or it is not a multicast address
     *
     * @since 1.4
     */
    @Override
    public boolean isMCNodeLocal() {
        return ((ipaddress[0] & 0xff) == 0xff
                && (ipaddress[1] & 0x0f) == 0x01);
    }

    /**
     * Utility routine to check if the multicast address has link scope.
     *
     * @return a {@code boolean} indicating if the address has is a multicast
     *         address of link-local scope, false if it is not of link-local
     *         scope or it is not a multicast address
     *
     * @since 1.4
     */
    @Override
    public boolean isMCLinkLocal() {
        return ((ipaddress[0] & 0xff) == 0xff
                && (ipaddress[1] & 0x0f) == 0x02);
    }

    /**
     * Utility routine to check if the multicast address has site scope.
     *
     * @return a {@code boolean} indicating if the address has is a multicast
     *         address of site-local scope, false if it is not  of site-local
     *         scope or it is not a multicast address
     *
     * @since 1.4
     */
    @Override
    public boolean isMCSiteLocal() {
        return ((ipaddress[0] & 0xff) == 0xff
                && (ipaddress[1] & 0x0f) == 0x05);
    }

    /**
     * Utility routine to check if the multicast address has organization scope.
     *
     * @return a {@code boolean} indicating if the address has is a multicast
     *         address of organization-local scope, false if it is not of
     *         organization-local scope or it is not a multicast address
     *
     * @since 1.4
     */
    @Override
    public boolean isMCOrgLocal() {
        return ((ipaddress[0] & 0xff) == 0xff
                && (ipaddress[1] & 0x0f) == 0x08);
    }

    /**
     * Returns the raw IP address of this {@code InetAddress} object. The result
     * is in network byte order: the highest order byte of the address is in
     * {@code getAddress()[0]}.
     *
     * @return  the raw IP address of this object.
     */
    @Override
    public byte[] getAddress() {
        return ipaddress.clone();
    }

    /**
     * Returns the numeric scopeId, if this instance is associated with
     * an interface. If no scoped_id is set, the returned value is zero.
     *
     * @return the scopeId, or zero if not set.
     *
     * @since 1.5
     */
     public int getScopeId() {
        return scope_id;
     }

    /**
     * Returns the scoped interface, if this instance was created with
     * with a scoped interface.
     *
     * @return the scoped interface, or null if not set.
     * @since 1.5
     */
     public NetworkInterface getScopedInterface() {
        return scope_ifname;
     }

    /**
     * Returns the IP address string in textual presentation. If the instance
     * was created specifying a scope identifier then the scope id is appended
     * to the IP address preceded by a "%" (per-cent) character. This can be
     * either a numeric value or a string, depending on which was used to create
     * the instance.
     *
     * @return  the raw IP address in a string format.
     */
    @Override
    public String getHostAddress() {
        String s = numericToTextFormat(ipaddress);
        if (scope_ifname != null) { /* must check this first */
            s = s + "%" + scope_ifname.getName();
        } else if (scope_id_set) {
            s = s + "%" + scope_id;
        }
        return s;
    }

    /**
     * Returns a hashcode for this IP address.
     *
     * @return  a hash code value for this IP address.
     */
    @Override
    public int hashCode() {
        if (ipaddress != null) {

            int hash = 0;
            int i=0;
            while (i<INADDRSZ) {
                int j=0;
                int component=0;
                while (j<4 && i<INADDRSZ) {
                    component = (component << 8) + ipaddress[i];
                    j++;
                    i++;
                }
                hash += component;
            }
            return hash;

        } else {
            return 0;
        }
    }

    /**
     * Compares this object against the specified object. The result is {@code
     * true} if and only if the argument is not {@code null} and it represents
     * the same IP address as this object.
     *
     * <p> Two instances of {@code InetAddress} represent the same IP address
     * if the length of the byte arrays returned by {@code getAddress} is the
     * same for both, and each of the array components is the same for the byte
     * arrays.
     *
     * @param   obj   the object to compare against.
     *
     * @return  {@code true} if the objects are the same; {@code false} otherwise.
     *
     * @see     java.net.InetAddress#getAddress()
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Inet6Address))
            return false;

        Inet6Address inetAddr = (Inet6Address)obj;
        for (int i = 0; i < INADDRSZ; i++) {
            if (ipaddress[i] != inetAddr.ipaddress[i])
                return false;
        }

        return true;
    }

    /**
     * Utility routine to check if the InetAddress is an
     * IPv4 compatible IPv6 address.
     *
     * @return a {@code boolean} indicating if the InetAddress is an IPv4
     *         compatible IPv6 address; or false if address is IPv4 address.
     *
     * @since 1.4
     */
    public boolean isIPv4CompatibleAddress() {
        if ((ipaddress[0] == 0x00) && (ipaddress[1] == 0x00) &&
            (ipaddress[2] == 0x00) && (ipaddress[3] == 0x00) &&
            (ipaddress[4] == 0x00) && (ipaddress[5] == 0x00) &&
            (ipaddress[6] == 0x00) && (ipaddress[7] == 0x00) &&
            (ipaddress[8] == 0x00) && (ipaddress[9] == 0x00) &&
            (ipaddress[10] == 0x00) && (ipaddress[11] == 0x00))  {
            return true;
        }
        return false;
    }

    // Utilities
    private final static int INT16SZ = 2;

    /*
     * Convert IPv6 binary address into presentation (printable) format.
     *
     * @param src a byte array representing the IPv6 numeric address
     * @return a String representing an IPv6 address in
     *         textual representation format
     * @since 1.4
     */
    static String numericToTextFormat(byte[] src) {
        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < (INADDRSZ / INT16SZ); i++) {
            sb.append(Integer.toHexString(((src[i<<1]<<8) & 0xff00)
                                          | (src[(i<<1)+1] & 0xff)));
            if (i < (INADDRSZ / INT16SZ) -1 ) {
               sb.append(":");
            }
        }
        return sb.toString();
    }

    /**
     * Perform class load-time initializations.
     */
    private static native void init();

    /**
     * Following field is only used during (de)/serialization
     */
    private String ifname;

    /**
     * default behavior is overridden in order to write the
     * scope_ifname field as a String, rather than a NetworkInterface
     * which is not serializable
     */
    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
        if (scope_ifname != null) {
            ifname = scope_ifname.getName();
            scope_ifname_set = true;
        }
        s.defaultWriteObject();
    }
}
