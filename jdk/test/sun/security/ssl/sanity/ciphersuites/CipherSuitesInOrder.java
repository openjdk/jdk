/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 7174244
 * @summary NPE in Krb5ProxyImpl.getServerKeys()
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
 * @run main/othervm CipherSuitesInOrder
 */

import java.util.*;
import javax.net.ssl.*;

public class CipherSuitesInOrder {

    // supported ciphersuites
    private final static List<String> supportedCipherSuites =
            Arrays.<String>asList(
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
        "TLS_RSA_WITH_AES_256_CBC_SHA256",
        "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
        "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
        "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
        "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_RSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
        "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
        "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
        "SSL_RSA_WITH_RC4_128_SHA",
        "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
        "TLS_ECDH_RSA_WITH_RC4_128_SHA",
        "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
        "SSL_RSA_WITH_RC4_128_MD5",

        "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",

        "TLS_DH_anon_WITH_AES_256_CBC_SHA256",
        "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
        "TLS_DH_anon_WITH_AES_256_CBC_SHA",
        "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
        "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
        "TLS_DH_anon_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_anon_WITH_RC4_128_SHA",
        "SSL_DH_anon_WITH_RC4_128_MD5",
        "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
        "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
        "TLS_RSA_WITH_NULL_SHA256",
        "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
        "TLS_ECDHE_RSA_WITH_NULL_SHA",
        "SSL_RSA_WITH_NULL_SHA",
        "TLS_ECDH_ECDSA_WITH_NULL_SHA",
        "TLS_ECDH_RSA_WITH_NULL_SHA",
        "TLS_ECDH_anon_WITH_NULL_SHA",
        "SSL_RSA_WITH_NULL_MD5",
        "SSL_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_DSS_WITH_DES_CBC_SHA",
        "SSL_DH_anon_WITH_DES_CBC_SHA",
        "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
        "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
        "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
        "TLS_KRB5_WITH_RC4_128_SHA",
        "TLS_KRB5_WITH_RC4_128_MD5",
        "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
        "TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
        "TLS_KRB5_WITH_DES_CBC_SHA",
        "TLS_KRB5_WITH_DES_CBC_MD5",
        "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
        "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
        "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
        "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5"
    );

    private final static String[] protocols = {
        "", "SSL", "TLS", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"
    };


    public static void main(String[] args) throws Exception {
        // show all of the supported cipher suites
        showSuites(supportedCipherSuites.toArray(new String[0]),
                                "All supported cipher suites");

        for (String protocol : protocols) {
            System.out.println("//");
            System.out.println("// " +
                        "Testing for SSLContext of " + protocol);
            System.out.println("//");
            checkForProtocols(protocol);
        }
    }

    public static void checkForProtocols(String protocol) throws Exception {
        SSLContext context;
        if (protocol.isEmpty()) {
            context = SSLContext.getDefault();
        } else {
            context = SSLContext.getInstance(protocol);
            context.init(null, null, null);
        }

        // check the order of default cipher suites of SSLContext
        SSLParameters parameters = context.getDefaultSSLParameters();
        checkSuites(parameters.getCipherSuites(),
                "Default cipher suites in SSLContext");

        // check the order of supported cipher suites of SSLContext
        parameters = context.getSupportedSSLParameters();
        checkSuites(parameters.getCipherSuites(),
                "Supported cipher suites in SSLContext");


        //
        // Check the cipher suites order of SSLEngine
        //
        SSLEngine engine = context.createSSLEngine();

        // check the order of endabled cipher suites
        String[] ciphers = engine.getEnabledCipherSuites();
        checkSuites(ciphers,
                "Enabled cipher suites in SSLEngine");

        // check the order of supported cipher suites
        ciphers = engine.getSupportedCipherSuites();
        checkSuites(ciphers,
                "Supported cipher suites in SSLEngine");

        //
        // Check the cipher suites order of SSLSocket
        //
        SSLSocketFactory factory = context.getSocketFactory();
        try (SSLSocket socket = (SSLSocket)factory.createSocket()) {

            // check the order of endabled cipher suites
            ciphers = socket.getEnabledCipherSuites();
            checkSuites(ciphers,
                "Enabled cipher suites in SSLSocket");

            // check the order of supported cipher suites
            ciphers = socket.getSupportedCipherSuites();
            checkSuites(ciphers,
                "Supported cipher suites in SSLSocket");
        }

        //
        // Check the cipher suites order of SSLServerSocket
        //
        SSLServerSocketFactory serverFactory = context.getServerSocketFactory();
        try (SSLServerSocket serverSocket =
                (SSLServerSocket)serverFactory.createServerSocket()) {
            // check the order of endabled cipher suites
            ciphers = serverSocket.getEnabledCipherSuites();
            checkSuites(ciphers,
                "Enabled cipher suites in SSLServerSocket");

            // check the order of supported cipher suites
            ciphers = serverSocket.getSupportedCipherSuites();
            checkSuites(ciphers,
                "Supported cipher suites in SSLServerSocket");
        }
    }

    private static void checkSuites(String[] suites, String title) {
        showSuites(suites, title);

        int loc = -1;
        int index = 0;
        for (String suite : suites) {
            index = supportedCipherSuites.indexOf(suite);
            if (index <= loc) {
                throw new RuntimeException(suite + " is not in order");
            }

            loc = index;
        }
    }

    private static void showSuites(String[] suites, String title) {
        System.out.println(title + "[" + suites.length + "]:");
        for (String suite : suites) {
            System.out.println("  " + suite);
        }
    }
}
