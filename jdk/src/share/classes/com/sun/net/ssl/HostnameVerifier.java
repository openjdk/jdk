/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * NOTE:  this file was copied from javax.net.ssl.HostnameVerifier
 */

package com.sun.net.ssl;

/**
 * HostnameVerifier provides a callback mechanism so that
 * implementers of this interface can supply a policy for
 * handling the case where the host to connect to and
 * the server name from the certificate mismatch.
 *
 * @deprecated As of JDK 1.4, this implementation-specific class was
 *      replaced by {@link javax.net.ssl.HostnameVerifier} and
 *      {@link javax.net.ssl.CertificateHostnameVerifier}.
 */
@Deprecated
public interface HostnameVerifier {
    /**
     * Verify that the hostname from the URL is an acceptable
     * match with the value from the common name entry in the
     * server certificate's distinguished name.
     *
     * @param urlHostname the host name of the URL
     * @param certHostname the common name entry from the certificate
     * @return true if the certificate host name is acceptable
     */
    public boolean verify(String urlHostname, String certHostname);
}
