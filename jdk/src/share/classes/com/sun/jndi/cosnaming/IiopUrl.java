/*
 * Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.cosnaming;

import javax.naming.Name;
import javax.naming.NamingException;

import java.net.MalformedURLException;
import java.util.Vector;
import java.util.StringTokenizer;
import com.sun.jndi.toolkit.url.UrlUtil;

/**
 * Extract components of an "iiop" or "iiopname" URL.
 *
 * The format of an iiopname URL is defined in INS 98-10-11 as follows:
 *
 * iiopname url = "iiopname://" [addr_list]["/" string_name]
 * addr_list    = [address ","]* address
 * address      = [version host [":" port]]
 * host         = DNS style host name | IP address
 * version      = major "." minor "@" | empty_string
 * port         = number
 * major        = number
 * minor        = number
 * string_name = stringified name | empty_string
 *
 * The default port is 9999. The default version is "1.0"
 * US-ASCII alphanumeric characters are not escaped. Any characters outside
 * of this range are escaped except for the following:
 * ; / : ? : @ & = + $ , - _ . ! ~ *  ' ( )
 * Escaped characters is escaped by using a % followed by its 2 hexadecimal
 * numbers representing the octet.
 *
 * For backward compatibility,  the "iiop" URL as defined in INS 97-6-6
 * is also supported:
 *
 * iiop url     = "iiop://" [host [":" port]] ["/" string_name]
 * The default port is 900.
 *
 * @author Rosanna Lee
 */

public final class IiopUrl {
    static final private int DEFAULT_IIOPNAME_PORT = 9999;
    static final private int DEFAULT_IIOP_PORT = 900;
    static final private String DEFAULT_HOST = "localhost";
    private Vector addresses;
    private String stringName;

    public static class Address {
        public int port = -1;
        public int major, minor;
        public String host;

        public Address(String hostPortVers, boolean oldFormat)
            throws MalformedURLException {
            // [version host [":" port]]
            int start;

            // Parse version
            int at;
            if (oldFormat || (at = hostPortVers.indexOf('@')) < 0) {
                major = 1;
                minor = 0;
                start = 0;     // start at the beginning
            } else {
                int dot = hostPortVers.indexOf('.');
                if (dot < 0) {
                    throw new MalformedURLException(
                        "invalid version: " + hostPortVers);
                }
                try {
                    major = Integer.parseInt(hostPortVers.substring(0, dot));
                    minor = Integer.parseInt(hostPortVers.substring(dot+1, at));
                } catch (NumberFormatException e) {
                    throw new MalformedURLException(
                        "Nonnumeric version: " + hostPortVers);
                }
                start = at + 1;  // skip '@' sign
            }

            // Parse host and port
            int slash = hostPortVers.indexOf('/', start);
            if (slash < 0) {
                slash = hostPortVers.length();
            }
            if (hostPortVers.startsWith("[", start)) {  // at IPv6 literal
                int brac = hostPortVers.indexOf(']', start + 1);
                if (brac < 0 || brac > slash) {
                    throw new IllegalArgumentException(
                        "IiopURL: name is an Invalid URL: " + hostPortVers);
                }

                // include brackets
                host = hostPortVers.substring(start, brac + 1);
                start = brac + 1;
            } else {      // at hostname or IPv4
                int colon = hostPortVers.indexOf(':', start);
                int hostEnd = (colon < 0 || colon > slash)
                    ? slash
                    : colon;
                if (start < hostEnd) {
                    host = hostPortVers.substring(start, hostEnd);
                }
                start = hostEnd;   // skip past host
            }
            if ((start + 1 < slash)) {
                if ( hostPortVers.startsWith(":", start)) { // parse port
                    start++;    // skip past ":"
                    port = Integer.parseInt(hostPortVers.
                                            substring(start, slash));
                } else {
                    throw new IllegalArgumentException(
                        "IiopURL: name is an Invalid URL: " + hostPortVers);
                }
            }
            start = slash;
            if ("".equals(host) || host == null) {
                host = DEFAULT_HOST ;
            }
            if (port == -1) {
                port = (oldFormat ? DEFAULT_IIOP_PORT :
                                DEFAULT_IIOPNAME_PORT);
            }
        }
    }

    public Vector getAddresses() {
        return addresses;
    }

    /**
     * Returns a possibly empty but non-null string that is the "string_name"
     * portion of the URL.
     */
    public String getStringName() {
        return stringName;
    }

    public Name getCosName() throws NamingException {
        return CNCtx.parser.parse(stringName);
    }

    public IiopUrl(String url) throws MalformedURLException {
        int addrStart;
        boolean oldFormat;

        if (url.startsWith("iiopname://")) {
            oldFormat = false;
            addrStart = 11;
        } else if (url.startsWith("iiop://")) {
            oldFormat = true;
            addrStart = 7;
        } else {
            throw new MalformedURLException("Invalid iiop/iiopname URL: " + url);
        }
        int addrEnd = url.indexOf('/', addrStart);
        if (addrEnd < 0) {
            addrEnd = url.length();
            stringName = "";
        } else {
            stringName = UrlUtil.decode(url.substring(addrEnd+1));
        }
        addresses = new Vector(3);
        if (oldFormat) {
            // Only one host:port part, not multiple
            addresses.addElement(
                new Address(url.substring(addrStart, addrEnd), oldFormat));
        } else {
            StringTokenizer tokens =
                new StringTokenizer(url.substring(addrStart, addrEnd), ",");
            while (tokens.hasMoreTokens()) {
                addresses.addElement(new Address(tokens.nextToken(), oldFormat));
            }
            if (addresses.size() == 0) {
                addresses.addElement(new Address("", oldFormat));
            }
        }
    }

    // for testing only
    /*public static void main(String[] args) {
        try {
            IiopUrl url = new IiopUrl(args[0]);
            Vector addrs = url.getAddresses();
            String name = url.getStringName();

            for (int i = 0; i < addrs.size(); i++) {
                Address addr = (Address)addrs.elementAt(i);
                System.out.println("host: " + addr.host);
                System.out.println("port: " + addr.port);
                System.out.println("version: " + addr.major + " " + addr.minor);
            }
            System.out.println("name: " + name);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    } */
}
