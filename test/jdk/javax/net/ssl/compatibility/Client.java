/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
 * A simple SSL socket client.
 */
public class Client {

    private final SSLSocket socket;

    public Client(SSLContext context) throws Exception {
        SSLSocketFactory socketFactory = context.getSocketFactory();
        socket = (SSLSocket) socketFactory.createSocket();
        socket.setSoTimeout(Utils.TIMEOUT);
    }

    public Client(Cert... certs) throws Exception {
        this(Utils.createSSLContext(certs));
    }

    private SSLSession getSession() {
        return socket.getSession();
    }

    private void setEnabledCipherSuites(String... cipherSuites) {
        socket.setEnabledCipherSuites(cipherSuites);
    }

    private void setEnabledProtocols(String... protocols) {
        socket.setEnabledProtocols(protocols);
    }

    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    private void setServerName(String hostname) {
        List serverNames = new ArrayList();
        serverNames.add(createSNIHostName(hostname));
        SSLParameters params = socket.getSSLParameters();
        params.setServerNames(serverNames);
        socket.setSSLParameters(params);
    }

    // Create SNIHostName via reflection due to pre-8 JDK builds don't support
    // SNI. Those JDK builds cannot find classes SNIServerName and SNIHostName.
    private Object createSNIHostName(String hostname) {
        try {
            Class<?> clazz = Class.forName("javax.net.ssl.SNIHostName");
            return clazz.getConstructor(String.class).newInstance(hostname);
        } catch (Exception e) {
            throw new RuntimeException("Creates SNIHostName failed!", e);
        }
    }

    private void setApplicationProtocols(String... protocols) {
        SSLParameters params = socket.getSSLParameters();
        params.setApplicationProtocols(protocols);
        socket.setSSLParameters(params);
    }

    private String getNegotiatedApplicationProtocol() {
        return socket.getApplicationProtocol();
    }

    private void oneTimeConnect(String host, int port) throws IOException {
        socket.connect(new InetSocketAddress(host, port));

        OutputStream out = socket.getOutputStream();
        out.write('C');
        out.flush();

        InputStream in = socket.getInputStream();
        in.read();
    }

    public void close() throws IOException {
        socket.close();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("----- Client start -----");
        int port = Integer.valueOf(System.getProperty(Utils.PROP_PORT));

        String protocol = System.getProperty(Utils.PROP_PROTOCOL);
        String cipherSuite = System.getProperty(Utils.PROP_CIPHER_SUITE);
        String serverName = System.getProperty(Utils.PROP_SERVER_NAME);
        String appProtocols = System.getProperty(Utils.PROP_APP_PROTOCOLS);
        boolean supportsSNIOnServer
                = Utils.getBoolProperty(Utils.PROP_SUPPORTS_SNI_ON_SERVER);
        boolean supportsSNIOnClient
                = Utils.getBoolProperty(Utils.PROP_SUPPORTS_SNI_ON_CLIENT);
        boolean supportsALPNOnServer
                = Utils.getBoolProperty(Utils.PROP_SUPPORTS_ALPN_ON_SERVER);
        boolean supportsALPNOnClient
                = Utils.getBoolProperty(Utils.PROP_SUPPORTS_ALPN_ON_CLIENT);
        boolean negativeCase
                = Utils.getBoolProperty(Utils.PROP_NEGATIVE_CASE_ON_CLIENT);
        System.out.println(Utils.join(Utils.PARAM_DELIMITER,
                "ClientJDK=" + System.getProperty(Utils.PROP_CLIENT_JDK),
                "Protocol=" + protocol,
                "CipherSuite=" + cipherSuite,
                "ServerName=" + serverName,
                "AppProtocols=" + appProtocols));

        Status status = Status.SUCCESS;
        Client client = null;
        try {
            client = new Client(Cert.getCerts(CipherSuite.cipherSuite(cipherSuite)));
            client.setEnabledProtocols(protocol);
            client.setEnabledCipherSuites(cipherSuite);

            if (serverName != null) {
                if (supportsSNIOnClient) {
                    client.setServerName(serverName);
                } else {
                    System.out.println(
                            "Ignored due to client doesn't support SNI.");
                }
            }

            if (appProtocols != null) {
                if (supportsALPNOnClient) {
                    client.setApplicationProtocols(
                            Utils.split(appProtocols, Utils.VALUE_DELIMITER));
                } else {
                    System.out.println(
                            "Ignored due to client doesn't support ALPN.");
                }
            }

            client.oneTimeConnect("localhost", port);

            if (serverName != null && supportsSNIOnServer
                    && supportsSNIOnClient) {
                X509Certificate cert
                        = (X509Certificate) client.getSession().getPeerCertificates()[0];
                String subject
                        = cert.getSubjectX500Principal().getName();
                if (!subject.contains(serverName)) {
                    System.out.println("Unexpected server: " + subject);
                    status = Status.FAIL;
                }
            }

            if (appProtocols != null && supportsALPNOnServer
                    && supportsALPNOnClient) {
                String negoAppProtocol
                        = client.getNegotiatedApplicationProtocol();
                String expectedNegoAppProtocol
                        = System.getProperty(Utils.PROP_NEGO_APP_PROTOCOL);
                if (!expectedNegoAppProtocol.equals(negoAppProtocol)) {
                    System.out.println("Unexpected negotiated app protocol: "
                            + negoAppProtocol);
                    status = Status.FAIL;
                }
            }

            if (status != Status.FAIL) {
                status = negativeCase
                       ? Status.UNEXPECTED_SUCCESS
                       : Status.SUCCESS;
            }
        } catch (Exception exception) {
            status = Utils.handleException(exception, negativeCase);
        } finally {
            if (client != null) {
                client.close();
            }
        }

        System.out.println("STATUS: " + status);
        System.out.println("----- Client end -----");
    }
}
