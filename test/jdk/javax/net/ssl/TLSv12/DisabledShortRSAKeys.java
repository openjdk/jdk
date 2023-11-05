/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 7109274
 * @summary Consider disabling support for X.509 certificates with RSA keys
 *          less than 1024 bits
 * @library /javax/net/ssl/templates
 * @run main/othervm DisabledShortRSAKeys PKIX TLSv1.2
 * @run main/othervm DisabledShortRSAKeys SunX509 TLSv1.2
 * @run main/othervm DisabledShortRSAKeys PKIX TLSv1.1
 * @run main/othervm DisabledShortRSAKeys SunX509 TLSv1.1
 * @run main/othervm DisabledShortRSAKeys PKIX TLSv1
 * @run main/othervm DisabledShortRSAKeys SunX509 TLSv1
 * @run main/othervm DisabledShortRSAKeys PKIX SSLv3
 * @run main/othervm DisabledShortRSAKeys SunX509 SSLv3
 */

import java.io.*;
import javax.net.ssl.*;
import java.security.Security;

public class DisabledShortRSAKeys extends SSLSocketTemplate {

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    private final String enabledProtocol;
    private final String tmAlgorithm;

    public DisabledShortRSAKeys(String tmAlgorithm, String enabledProtocol) {
        this.tmAlgorithm = tmAlgorithm;
        this.enabledProtocol = enabledProtocol;
    }

    @Override
    public SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(new Cert[]{Cert.CA_RSA_512}, null,
                new ContextParameters(enabledProtocol, tmAlgorithm, "NewSunX509"));
    }

    @Override
    public SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(null, new Cert[]{Cert.EE_RSA_512},
                new ContextParameters(enabledProtocol, tmAlgorithm, "NewSunX509"));
    }

    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
        try {
            try (InputStream sslIS = socket.getInputStream()) {
                sslIS.read();
            }
            throw new Exception("RSA keys shorter than 1024 bits should be disabled");
        } catch (SSLHandshakeException sslhe) {
            // the expected exception, ignore
        }

    }

    @Override
    protected void runClientApplication(SSLSocket socket) throws Exception {

        try {

            // only enable the target protocol
            socket.setEnabledProtocols(new String[] { enabledProtocol });
            // enable a block cipher
            socket.setEnabledCipherSuites(
                new String[] { "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" });

            try (OutputStream sslOS = socket.getOutputStream()) {
                sslOS.write('B');
                sslOS.flush();
            }
            throw new Exception(
               "RSA keys shorter than 1024 bits should be disabled");
        } catch (SSLHandshakeException sslhe) {
            // the expected exception, ignore
        }
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "RSA keySize < 1024");

        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        String tmAlgorithm = args[0];
        String enabledProtocol = args[1];

        /*
         * Start the tests.
         */
        new DisabledShortRSAKeys(tmAlgorithm, enabledProtocol).run();
    }
}
