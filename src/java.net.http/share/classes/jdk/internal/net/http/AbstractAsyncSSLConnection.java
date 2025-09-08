/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import jdk.internal.net.http.common.Alpns;
import jdk.internal.net.http.common.SSLTube;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Utils;
import sun.net.util.IPAddressUtil;

/**
 * Asynchronous version of SSLConnection.
 *
 * There are two concrete implementations of this class: AsyncSSLConnection
 * and AsyncSSLTunnelConnection.
 * This abstraction is useful when downgrading from HTTP/2 to HTTP/1.1 over
 * an SSL connection. See ExchangeImpl::get in the case where an ALPNException
 * is thrown.
 *
 * Note: An AsyncSSLConnection wraps a PlainHttpConnection, while an
 *       AsyncSSLTunnelConnection wraps a PlainTunnelingConnection.
 *       If both these wrapped classes where made to inherit from a
 *       common abstraction then it might be possible to merge
 *       AsyncSSLConnection and AsyncSSLTunnelConnection back into
 *       a single class - and simply use different factory methods to
 *       create different wrappees, but this is left up for further cleanup.
 *
 */
abstract class AbstractAsyncSSLConnection extends HttpConnection
{

    private record ServerName(String name, boolean isLiteral) {
    }

    protected final SSLEngine engine;
    protected final SSLParameters sslParameters;
    private final List<SNIServerName> sniServerNames;

    // Setting this property disables HTTPS hostname verification. Use with care.
    private static final boolean disableHostnameVerification
            = Utils.isHostnameVerificationDisabled();

    AbstractAsyncSSLConnection(Origin originServer,
                               InetSocketAddress addr,
                               HttpClientImpl client,
                               String[] alpn,
                               String label) {
        super(originServer, addr, client, label);
        assert originServer != null : "origin server is null";
        final ServerName serverName = getServerName(originServer);
        this.sniServerNames = formSNIServerNames(serverName, client);
        SSLContext context = client.theSSLContext();
        sslParameters = createSSLParameters(client, this.sniServerNames, alpn);
        Log.logParams(sslParameters);
        engine = createEngine(context, serverName.name(), originServer.port(), sslParameters);
    }

    abstract SSLTube getConnectionFlow();

    final CompletableFuture<String> getALPN() {
        return getConnectionFlow().getALPN();
    }

    final SSLEngine getEngine() { return engine; }

    @Override
    public final List<SNIServerName> getSNIServerNames() {
        return this.sniServerNames;
    }

    private static boolean contains(String[] rr, String target) {
        for (String s : rr)
            if (target.equalsIgnoreCase(s))
                return true;
        return false;
    }

    /**
     * Returns the {@link SSLParameters} to be used by the {@link SSLEngine} for this connection.
     * <p>
     * The returned {@code SSLParameters} will have its {@link SNIServerName}s set to the given
     * {@code sniServerNames}. If {@code alpn} is non-null then the returned {@code SSLParameters}
     * will have its {@linkplain SSLParameters#getApplicationProtocols() application layer protocols}
     * set to this value. All other parameters in the returned {@code SSLParameters} will be
     * copied over from {@link HttpClient#sslParameters()} of the given {@code client}.
     *
     * @param client         the HttpClient
     * @param sniServerNames the SNIServerName(s)
     * @param alpn           the application layer protocols
     * @return the SSLParameters to be set on the SSLEngine used by this connection.
     */
    private static SSLParameters createSSLParameters(HttpClientImpl client,
                                                     List<SNIServerName> sniServerNames,
                                                     String[] alpn) {
        SSLParameters sslp = client.sslParameters();
        SSLParameters sslParameters = Utils.copySSLParameters(sslp);
        // filter out unwanted protocols, if h2 only
        if (alpn != null && alpn.length != 0 && !contains(alpn, Alpns.HTTP_1_1)) {
            ArrayDeque<String> l = new ArrayDeque<>();
            for (String proto : sslParameters.getProtocols()) {
                if (!proto.startsWith("SSL") && !proto.endsWith("v1.1") && !proto.endsWith("v1")) {
                    l.add(proto);
                }
            }
            String[] a1 = l.toArray(new String[0]);
            sslParameters.setProtocols(a1);
        }

        if (!disableHostnameVerification)
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        if (alpn != null) {
            Log.logSSL("AbstractAsyncSSLConnection: Setting application protocols: {0}",
                    Arrays.toString(alpn));
            sslParameters.setApplicationProtocols(alpn);
        } else {
            Log.logSSL("AbstractAsyncSSLConnection: no applications set!");
        }
        sslParameters.setServerNames(sniServerNames);
        return sslParameters;
    }

    /**
     * Returns a list of {@link SNIServerName}s that are expected to be used to
     * configure the {@link SSLEngine} used by this connection.
     * <p>
     * The given {@code serverName} is given preference, and if it is not null and
     * is not an IP address literal, then the returned list will contain only one
     * {@code SNIServerName} formed out of the {@code serverName}. If {@code serverName}
     * is null or is an IP address literal then the {@code SNIServerName}(s)
     * configured through {@link HttpClient#sslParameters()} will be returned. If none have
     * been configured, then an empty list is returned.
     *
     * @param serverName the {@link ServerName}, typically computed based on the request URI
     * @param client     the {@code HttpClient}
     * @return a list of {@code SNIServerName}s to be used by the {@code SSLEngine}
     *         of this connection.
     */
    private static List<SNIServerName> formSNIServerNames(final ServerName serverName,
                                                          final HttpClientImpl client) {
        if (serverName != null && !serverName.isLiteral()) {
            final String name = serverName.name();
            if (name != null && !name.isEmpty()) {
                return List.of(new SNIHostName(name));
            }
        }
        // fallback on any SNIServerName(s) configured through HttpClient.sslParameters()
        final SSLParameters clientSSLParams = client.sslParameters();
        final List<SNIServerName> clientConfigured = clientSSLParams.getServerNames();
        return clientConfigured != null ? clientConfigured : List.of();
    }

    private static SSLEngine createEngine(SSLContext context, String peerHost, int port,
                                          SSLParameters sslParameters) {
        SSLEngine engine = context.createSSLEngine(peerHost, port);
        engine.setUseClientMode(true);

        engine.setSSLParameters(sslParameters);
        return engine;
    }

    /**
     * Analyse the given {@linkplain Origin origin server} and determine
     * if the origin server's host is a literal or not, returning the server's
     * address in String form.
     */
    private static ServerName getServerName(final Origin originServer) {
        final String host = originServer.host();
        byte[] literal = IPAddressUtil.textToNumericFormatV4(host);
        if (literal == null) {
            // not IPv4 literal. Check IPv6
            literal = IPAddressUtil.textToNumericFormatV6(host);
            return new ServerName(host, literal != null);
        } else {
            return new ServerName(host, true);
        }
    }

    @Override
    final boolean isSecure() {
        return true;
    }

}
