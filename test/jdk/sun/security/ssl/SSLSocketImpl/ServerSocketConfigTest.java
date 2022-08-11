/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8133816
 * @library /test/lib /javax/net/ssl/templates
 * @summary Display extra SSLServerSocket info in debug mode
 */

import jdk.test.lib.process.ProcessTools;

import java.net.InetAddress;
import java.security.Security;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public class ServerSocketConfigTest extends SSLSocketTemplate {
    private  String protocol;

    private final static String[] protocols = {"TLSv1.2", "TLSv1.3"};
    private final static String[] serverCipherSuites = {
            "TLS_RSA_WITH_NULL_SHA256",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_DES_CBC_SHA",
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_256_GCM_SHA384"};

    private final static String[] noCommonCipherSuites = {
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_DES_CBC_SHA",
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA"};

    private final static String[] clientTSLV12Ciphersuites = {"TLS_RSA_WITH_NULL_SHA256"};

    private final static String[] clientTLSV13Ciphersuites = {"TLS_AES_128_GCM_SHA256"};
    private final static String debugMessage = "\"Enabled Server Cipher Suites\":";
    private final static String keyExchange = "\"[K_RSA]\"";
    private final static String legacySuites = "\"[TLS_RSA_WITH_NULL_SHA256]\"";

    private final static String noCommonInCipherSuiteMsg = "no cipher suites in common";

    private final static String keyExchangeFailedMsg = "key exchange failed";

    ServerSocketConfigTest(String protocol) {
        serverAddress = InetAddress.getLoopbackAddress();
        this.protocol = protocol;
    }

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        if (protocols[0].equalsIgnoreCase(protocol)) {
            return createSSLContext(TRUSTED_CERTS, null,
                    getServerContextParameters());
        } else {
            return createSSLContext(TRUSTED_CERTS, END_ENTITY_CERTS,
                    getServerContextParameters());
        }
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        if (protocols[0].equalsIgnoreCase(protocol) || protocols[1].equalsIgnoreCase(protocol)) {
            socket.setEnabledCipherSuites(serverCipherSuites);
            socket.setEnabledProtocols(new String[]{protocol});
        } else {
            socket.setEnabledCipherSuites(noCommonCipherSuites);
            socket.setEnabledProtocols(new String[]{protocols[0]});
        }
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        String[] clientCipherSuites = protocols[1].equalsIgnoreCase(protocol) ?
                clientTLSV13Ciphersuites : clientTSLV12Ciphersuites;

        socket.setEnabledCipherSuites(clientCipherSuites);

        if (protocols[1].equalsIgnoreCase(protocol)) {
            socket.setEnabledProtocols(new String[]{protocol});
        } else {
            socket.setEnabledProtocols(new String[]{protocols[0]});
        }
    }

    @Override
    protected void runServerApplication(SSLSocket socket){
        try{
            super.runServerApplication(socket);
        }catch(Exception e){

        }
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        if (args.length != 0) {
            // A non-empty set of arguments occurs when the "runTest" argument
            // is passed to the test via ProcessTools::executeTestJvm.
            //
            // This is done because an OutputAnalyzer is unable to read
            // the output of the current running JVM, and must therefore create
            // a test JVM. When this case occurs, it will inherit all specified
            // properties passed to the test JVM - debug flags, tls version, etc.
            try {
                new ServerSocketConfigTest(args[0]).run();
            }catch (Exception e){
                //do nothing
            }
        } else {
            // We are in the test JVM that the test is being ran in.
            var testSrc = "-Dtest.src=" + System.getProperty("test.src");
            var enabledDebug = "-Djavax.net.debug=ssl,handshake";


            // Testing protocol TSLv1.2,catch log message when no common cipher suite between client and server
            var output0 = ProcessTools.executeTestJvm(testSrc, enabledDebug, "ServerSocketConfigTest",
                    "NotTLSv1.2OrTLSv1.3"); // Ensuring args.length is greater than 0 when test JVM starts

            output0.shouldContain(debugMessage)
                    .shouldContain(serverCipherSuites[0])
                    .shouldNotContain(legacySuites)
                    .shouldNotContain(keyExchange)
                    .shouldContain(noCommonInCipherSuiteMsg);

            // Testing protocol TSLv1.2,catch log message when key exchange failed between client and server
            var output1 = ProcessTools.executeTestJvm(testSrc, enabledDebug, "ServerSocketConfigTest",
                    protocols[0]); // Ensuring args.length is greater than 0 when test JVM starts

            output1.shouldContain(debugMessage)
                    .shouldContain(serverCipherSuites[0])
                    .shouldContain(legacySuites)
                    .shouldContain(keyExchange)
                    .shouldContain(keyExchangeFailedMsg);

            // Testing protocol TSLv1.3, catch log message when no common cipher suites between client and server
            var output2 = ProcessTools.executeTestJvm(testSrc, enabledDebug, "ServerSocketConfigTest",
                    protocols[1]); // Ensuring args.length is greater than 0 when test JVM starts

            output2.shouldContain(debugMessage)
                   .shouldContain(serverCipherSuites[6])
                   .shouldContain(noCommonInCipherSuiteMsg);
        }
    }
}

