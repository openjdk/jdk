/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.net.httpserver;
import java.net.InetSocketAddress;
//BEGIN_TIGER_EXCLUDE
import javax.net.ssl.SSLParameters;
//END_TIGER_EXCLUDE

/**
 * Represents the set of parameters for each https
 * connection negotiated with clients. One of these
 * is created and passed to
 * {@link HttpsConfigurator#configure(HttpsParameters)}
 * for every incoming https connection,
 * in order to determine the parameters to use.
 * <p>
 * The underlying SSL parameters may be established either
 * via the set/get methods of this class, or else via
 * a {@link javax.net.ssl.SSLParameters} object. SSLParameters
 * is the preferred method, because in the future,
 * additional configuration capabilities may be added to that class, and
 * it is easier to determine the set of supported parameters and their
 * default values with SSLParameters. Also, if an SSLParameters object is
 * provided via
 * {@link #setSSLParameters(SSLParameters)} then those parameter settings
 * are used, and any settings made in this object are ignored.
 * @since 1.6
 */
@jdk.Exported
public abstract class HttpsParameters {

    private String[] cipherSuites;
    private String[] protocols;
    private boolean wantClientAuth;
    private boolean needClientAuth;

    protected HttpsParameters() {}

    /**
     * Returns the HttpsConfigurator for this HttpsParameters.
     */
    public abstract HttpsConfigurator getHttpsConfigurator();

    /**
     * Returns the address of the remote client initiating the
     * connection.
     */
    public abstract InetSocketAddress getClientAddress();

//BEGIN_TIGER_EXCLUDE
    /**
     * Sets the SSLParameters to use for this HttpsParameters.
     * The parameters must be supported by the SSLContext contained
     * by the HttpsConfigurator associated with this HttpsParameters.
     * If no parameters are set, then the default behavior is to use
     * the default parameters from the associated SSLContext.
     * @param params the SSLParameters to set. If <code>null</code>
     * then the existing parameters (if any) remain unchanged.
     * @throws IllegalArgumentException if any of the parameters are
     *   invalid or unsupported.
     */
    public abstract void setSSLParameters (SSLParameters params);
//END_TIGER_EXCLUDE

    /**
     * Returns a copy of the array of ciphersuites or null if none
     * have been set.
     *
     * @return a copy of the array of ciphersuites or null if none
     * have been set.
     */
    public String[] getCipherSuites() {
        return cipherSuites != null ? cipherSuites.clone() : null;
    }

    /**
     * Sets the array of ciphersuites.
     *
     * @param cipherSuites the array of ciphersuites (or null)
     */
    public void setCipherSuites(String[] cipherSuites) {
        this.cipherSuites = cipherSuites != null ? cipherSuites.clone() : null;
    }

    /**
     * Returns a copy of the array of protocols or null if none
     * have been set.
     *
     * @return a copy of the array of protocols or null if none
     * have been set.
     */
    public String[] getProtocols() {
        return protocols != null ? protocols.clone() : null;
    }

    /**
     * Sets the array of protocols.
     *
     * @param protocols the array of protocols (or null)
     */
    public void setProtocols(String[] protocols) {
        this.protocols = protocols != null ? protocols.clone() : null;
    }

    /**
     * Returns whether client authentication should be requested.
     *
     * @return whether client authentication should be requested.
     */
    public boolean getWantClientAuth() {
        return wantClientAuth;
    }

    /**
     * Sets whether client authentication should be requested. Calling
     * this method clears the <code>needClientAuth</code> flag.
     *
     * @param wantClientAuth whether client authentication should be requested
     */
    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }

    /**
     * Returns whether client authentication should be required.
     *
     * @return whether client authentication should be required.
     */
    public boolean getNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * Sets whether client authentication should be required. Calling
     * this method clears the <code>wantClientAuth</code> flag.
     *
     * @param needClientAuth whether client authentication should be required
     */
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }
}
