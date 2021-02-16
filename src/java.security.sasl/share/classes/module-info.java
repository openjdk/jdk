/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Defines Java support for the IETF Simple Authentication and Security Layer
 * (SASL).
 * <P>
 * This module also contains SASL mechanisms including DIGEST-MD5,
 * CRAM-MD5, and NTLM.
 *
 * @implNote
 * The following implementation specific property is supported by the
 * default SASL implementation in the JDK:
 * <ul>
 *     <li>{@code com.sun.sasl.tls.cbdata}:
 *         <br>The value of this property is the byte array representing the
 *         TLS Channel Binding data as defined in RFC-5929 and RFC-5056.
 *         LDAP Client generates TLS Channel Binding data on the base of
 *         TLS handshake and provides it to the SASL Client for authentication.
 *         "com.sun.sasl.tls.cbdata" property should not be specified
 *         explicitly. It is used internally to pass Channel Binding data from
 *         LDAP to SASL client.
 *     </li>
 * </ul>
 * @moduleGraph
 * @since 9
 */
module java.security.sasl {
    requires java.logging;

    exports javax.security.sasl;

    exports com.sun.security.sasl.util to
        jdk.security.jgss;

    provides java.security.Provider with
        com.sun.security.sasl.Provider;
}

