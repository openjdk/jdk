/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * may also support other environment or system properties, which are specific
 * to their implementation.
 *
 * @implNote
 * The following implementation specific environment properties are supported by the
 * default LDAP Naming Service Provider implementation in the JDK:
 * <ul>
 *     <li>{@code java.naming.ldap.factory.socket}:
 *         <br>The value of this environment property specifies the fully
 *         qualified class name of the socket factory used by the LDAP provider.
 *         This class must implement the {@link javax.net.SocketFactory} abstract class
 *         and provide an implementation of the static "getDefault()" method that
 *         returns an instance of the socket factory. By default the environment
 *         property is not set.
 *     </li>
 *     <li>{@code com.sun.jndi.ldap.connect.timeout}:
 *         <br>The value of this environment property is the string representation
 *         of an integer specifying the connection timeout in milliseconds.
 *         If the LDAP provider cannot establish a connection within that period,
 *         it aborts the connection attempt.
 *         The integer should be greater than zero. An integer less than
 *         or equal to zero means to use the network protocol's (i.e., TCP's)
 *         timeout value.
 *         <br> If this property is not specified, the default is to wait
 *         for the connection to be established or until the underlying
 *         network times out.
 *         <br> If a custom socket factory is provided via environment property
 *         {@code java.naming.ldap.factory.socket} and unconnected sockets
 *         are not supported, the specified timeout is ignored
 *         and the provider behaves as if no connection timeout was set.
 *     </li>
 *     <li>{@code com.sun.jndi.ldap.read.timeout}:
 *         <br>The value of this property is the string representation
 *         of an integer specifying the read timeout in milliseconds
 *         for LDAP operations. If the LDAP provider cannot get a LDAP
 *         response within that period, it aborts the read attempt. The
 *         integer should be greater than zero. An integer less than or
 *         equal to zero means no read timeout is specified which is equivalent
 *         to waiting for the response infinitely until it is received.
 *         <br>If this property is not specified, the default is to wait
 *         for the response until it is received.
 *     </li>
 *     <li>{@code com.sun.jndi.ldap.tls.cbtype}:
 *         <br>The value of this property is the string representing the TLS
 *         Channel Binding type required for an LDAP connection over SSL/TLS.
 *         Possible value is :
 *         <ul>
 *             <li>"tls-server-end-point" - Channel Binding data is created on
 *                 the basis of the TLS server certificate.
 *             </li>
 *         </ul>
 *         <br>"tls-unique" TLS Channel Binding type is specified in RFC-5929
 *         but not supported.
 *         <br>If this property is not specified, the client does not send
 *         channel binding information to the server.
 *     </li>
 * </ul>
 * <p>The following implementation specific system properties are supported by the
 * default LDAP Naming Service Provider implementation in the JDK:
 * <ul>
 *     <li>{@systemProperty com.sun.jndi.ldap.object.trustSerialData}:
 *          <br>The value of this system property is the string representation of a boolean value
 *          that controls the deserialization of java objects from the {@code javaSerializedData} LDAP
 *          attribute, reconstruction of RMI references from the {@code javaRemoteLocation} LDAP attribute, and
 *          reconstruction of {@linkplain javax.naming.BinaryRefAddr binary reference addresses} from
 *          the {@code javaReferenceAddress} LDAP attribute.
 *          To allow the deserialization or reconstruction of java objects from {@code javaSerializedData},
 *          {@code javaRemoteLocation} or {@code javaReferenceAddress} attributes, the system property value
 *          can be set to {@code true} (case insensitive).
 *          <br>If the property is not specified the deserialization of java objects
 *          from the {@code javaSerializedData}, the {@code javaRemoteLocation}, or {@code javaReferenceAddress}
 *          attributes is not allowed.
 *     </li>
 *     <li>{@systemProperty jdk.jndi.object.factoriesFilter}:
 *          <br>The value of this system property defines a filter used by
 *          the JNDI runtime implementation to control the set of object factory classes which will
 *          be allowed to instantiate objects from object references returned by naming/directory systems.
 *          The factory class named by the reference instance will be matched against this filter.
 *          The filter property supports pattern-based filter syntax with the same format as
 *          {@link java.io.ObjectInputFilter.Config#createFilter(String) jdk.serialFilter}. Limit patterns
 *          specified in the filter property are unused.
 *          This property can also be specified as a {@linkplain java.security.Security security property}.
 *          This property is also supported by the <a href="{@docRoot}/jdk.naming.rmi/module-summary.html">default JNDI
 *          RMI Provider</a>.
 *          <br>The default value allows any object factory class specified by the reference
 *          instance to recreate the referenced object.
 *     </li>
 *     <li>{@systemProperty jdk.jndi.ldap.object.factoriesFilter}:
 *          <br>The value of this system property defines a filter used by
 *          the JDK LDAP provider implementation to further restrict the set of object factory classes which will
 *          be allowed to instantiate objects from object references returned by LDAP systems.
 *          The factory class named by the {@linkplain javax.naming.Reference reference instance} first will be
 *          matched against this specific filter and then against the global filter. The factory class is rejected
 *          if any of these two filters reject it, or if none of them allow it.
 *          The filter property supports pattern-based filter syntax with the same format as
 *          {@link java.io.ObjectInputFilter.Config#createFilter(String) jdk.serialFilter}. Limit patterns
 *          specified in the filter property are unused.
 *          <br>The default value allows any object factory class provided by the JDK LDAP provider
 *          implementation.
 *         <br>This system property will be used to filter LDAP specific object factories only if
 *         global {@link javax.naming.spi.ObjectFactoryBuilder} is {@linkplain
 *         javax.naming.spi.NamingManager#setObjectFactoryBuilder(javax.naming.spi.ObjectFactoryBuilder)
 *         not set}.
 *     </li>
 * </ul>
 * <p>Other providers may define additional properties in their module description:
 * <ul>
 *  <li><a href="{@docRoot}/jdk.naming.dns/module-summary.html">DNS Naming Provider</a></li>
 *  <li><a href="{@docRoot}/jdk.naming.rmi/module-summary.html">RMI Naming Provider</a></li>
 * </ul>
 * @provides java.security.Provider
 *
 * @uses javax.naming.ldap.StartTlsResponse
 * @uses javax.naming.ldap.spi.LdapDnsProvider
 * @uses javax.naming.spi.InitialContextFactory
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
    exports com.sun.naming.internal to
         jdk.naming.rmi;

    uses javax.naming.ldap.StartTlsResponse;
    uses javax.naming.spi.InitialContextFactory;
    uses javax.naming.ldap.spi.LdapDnsProvider;

    provides java.security.Provider with
        sun.security.provider.certpath.ldap.JdkLDAP;
}
