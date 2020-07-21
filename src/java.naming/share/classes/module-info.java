/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines the Java Naming and Directory Interface (JNDI) API.
 * <p>
 * Common standard JNDI environment properties that may be supported
 * by JNDI providers are defined and documented in
 * {@link javax.naming.Context}. Specific JNDI provider implementations
 * may also support other environment properties, which are specific
 * to their implementation.
 *
 * @implNote
 * The following implementation specific properties are supported by the
 * default LDAP Naming Service Provider implementation in the JDK:
 * <ul>
 *     <li>{@code com.sun.jndi.ldap.connect.timeout}:
 *         <br>The value of this property is the string representation
 *         of an integer representing the connection timeout in
 *         milliseconds. If the LDAP provider cannot establish a
 *         connection within that period, it aborts the connection attempt.
 *         The integer should be greater than zero. An integer less than
 *         or equal to zero means to use the network protocol's (i.e., TCP's)
 *         timeout value.
 *         <br> If this property is not specified, the default is to wait
 *         for the connection to be established or until the underlying
 *         network times out.
 *     </li>
 *     <li>{@code com.sun.jndi.ldap.read.timeout}:
 *         <br>The value of this property is the string representation
 *         of an integer representing the read timeout in milliseconds
 *         for LDAP operations. If the LDAP provider cannot get a LDAP
 *         response within that period, it aborts the read attempt. The
 *         integer should be greater than zero. An integer less than or
 *         equal to zero means no read timeout is specified which is equivalent
 *         to waiting for the response infinitely until it is received.
 *         <br>If this property is not specified, the default is to wait
 *         for the response until it is received.
 *     </li>
 * </ul>
 *
 * @provides javax.naming.ldap.spi.LdapDnsProvider
 *
 * @uses javax.naming.ldap.spi.LdapDnsProvider
 *
 * @moduleGraph
 * @since 9
 */
module java.naming {
    requires java.security.sasl;

    exports javax.naming;
    exports javax.naming.directory;
    exports javax.naming.event;
    exports javax.naming.ldap;
    exports javax.naming.spi;
    exports javax.naming.ldap.spi;

    exports com.sun.jndi.toolkit.ctx to
        jdk.naming.dns;
    exports com.sun.jndi.toolkit.url to
        jdk.naming.dns,
        jdk.naming.rmi;

    uses javax.naming.ldap.StartTlsResponse;
    uses javax.naming.spi.InitialContextFactory;
    uses javax.naming.ldap.spi.LdapDnsProvider;

    provides java.security.Provider with
        sun.security.provider.certpath.ldap.JdkLDAP;
}
