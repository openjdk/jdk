/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

/**
 * This class provides a simple way for servers to support conventional
 * use of the Secure Sockets Layer (SSL).  Application code uses an
 * SSLServerSocketImpl exactly like it uses a regular TCP ServerSocket; the
 * difference is that the connections established are secured using SSL.
 *
 * <P> Also, the constructors take an explicit authentication context
 * parameter, giving flexibility with respect to how the server socket
 * authenticates itself.  That policy flexibility is not exposed through
 * the standard SSLServerSocketFactory API.
 *
 * <P> System security defaults prevent server sockets from accepting
 * connections if they the authentication context has not been given
 * a certificate chain and its matching private key.  If the clients
 * of your application support "anonymous" cipher suites, you may be
 * able to configure a server socket to accept those suites.
 *
 * @see SSLSocketImpl
 * @see SSLServerSocketFactoryImpl
 *
 * @author David Brownell
 */
final class SSLServerSocketImpl extends SSLServerSocket {
    private final SSLContextImpl        sslContext;
    private final SSLConfiguration      sslConfig;

    SSLServerSocketImpl(SSLContextImpl sslContext) throws IOException {

        super();
        this.sslContext = sslContext;
        this.sslConfig = new SSLConfiguration(sslContext, false);
        this.sslConfig.isClientMode = false;
    }

    SSLServerSocketImpl(SSLContextImpl sslContext,
            int port, int backlog) throws IOException {

        super(port, backlog);
        this.sslContext = sslContext;
        this.sslConfig = new SSLConfiguration(sslContext, false);
        this.sslConfig.isClientMode = false;
    }

    SSLServerSocketImpl(SSLContextImpl sslContext,
            int port, int backlog, InetAddress address) throws IOException {

        super(port, backlog, address);
        this.sslContext = sslContext;
        this.sslConfig = new SSLConfiguration(sslContext, false);
        this.sslConfig.isClientMode = false;
    }

    @Override
    public synchronized String[] getEnabledCipherSuites() {
        return CipherSuite.namesOf(sslConfig.enabledCipherSuites);
    }

    @Override
    public synchronized void setEnabledCipherSuites(String[] suites) {
        sslConfig.enabledCipherSuites =
                CipherSuite.validValuesOf(suites);
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return CipherSuite.namesOf(sslContext.getSupportedCipherSuites());
    }

    @Override
    public String[] getSupportedProtocols() {
        return ProtocolVersion.toStringArray(
                sslContext.getSupportedProtocolVersions());
    }

    @Override
    public synchronized String[] getEnabledProtocols() {
        return ProtocolVersion.toStringArray(sslConfig.enabledProtocols);
    }

    @Override
    public synchronized void setEnabledProtocols(String[] protocols) {
        if (protocols == null) {
            throw new IllegalArgumentException("Protocols cannot be null");
        }

        sslConfig.enabledProtocols = ProtocolVersion.namesOf(protocols);
    }

    @Override
    public synchronized void setNeedClientAuth(boolean need) {
        sslConfig.clientAuthType =
                (need ? ClientAuthType.CLIENT_AUTH_REQUIRED :
                        ClientAuthType.CLIENT_AUTH_NONE);
    }

    @Override
    public synchronized boolean getNeedClientAuth() {
        return (sslConfig.clientAuthType ==
                        ClientAuthType.CLIENT_AUTH_REQUIRED);
    }

    @Override
    public synchronized void setWantClientAuth(boolean want) {
        sslConfig.clientAuthType =
                (want ? ClientAuthType.CLIENT_AUTH_REQUESTED :
                        ClientAuthType.CLIENT_AUTH_NONE);
    }

    @Override
    public synchronized boolean getWantClientAuth() {
        return (sslConfig.clientAuthType ==
                        ClientAuthType.CLIENT_AUTH_REQUESTED);
    }

    @Override
    public synchronized void setUseClientMode(boolean useClientMode) {
        /*
         * If we need to change the client mode and the enabled
         * protocols and cipher suites haven't specifically been
         * set by the user, change them to the corresponding
         * default ones.
         */
        if (sslConfig.isClientMode != useClientMode) {
            if (sslContext.isDefaultProtocolVesions(
                    sslConfig.enabledProtocols)) {
                sslConfig.enabledProtocols =
                        sslContext.getDefaultProtocolVersions(!useClientMode);
            }

            if (sslContext.isDefaultCipherSuiteList(
                    sslConfig.enabledCipherSuites)) {
                sslConfig.enabledCipherSuites =
                        sslContext.getDefaultCipherSuites(!useClientMode);
            }

            sslConfig.isClientMode = useClientMode;
        }
    }

    @Override
    public synchronized boolean getUseClientMode() {
        return sslConfig.isClientMode;
    }

    @Override
    public synchronized void setEnableSessionCreation(boolean flag) {
        sslConfig.enableSessionCreation = flag;
    }

    @Override
    public synchronized boolean getEnableSessionCreation() {
        return sslConfig.enableSessionCreation;
    }

    @Override
    public synchronized SSLParameters getSSLParameters() {
        return sslConfig.getSSLParameters();
    }

    @Override
    public synchronized void setSSLParameters(SSLParameters params) {
        sslConfig.setSSLParameters(params);
    }

    @Override
    public Socket accept() throws IOException {
        SSLSocketImpl s = new SSLSocketImpl(sslContext, sslConfig);

        implAccept(s);
        s.doneConnect();
        return s;
    }

    @Override
    public String toString() {
        return "[SSL: "+ super.toString() + "]";
    }
}
