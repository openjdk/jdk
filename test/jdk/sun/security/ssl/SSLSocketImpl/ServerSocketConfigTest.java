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

    private final static String[] PROTOCOLS = {"TLSv1.2", "TLSv1.3"};
    private final static String[] SERVER_CS = {
            "TLS_RSA_WITH_NULL_SHA256",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_DES_CBC_SHA",
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_256_GCM_SHA384"};

    private final static String[] NO_COMMON_CS = {
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_DES_CBC_SHA",
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA"};

    private final static String[] CLIENT_TLSV12_CS = {"TLS_RSA_WITH_NULL_SHA256"};

    private final static String[] CLIENT_TLSV13_CS = {"TLS_AES_128_GCM_SHA256"};
    private final static String DEBUG_MESSAGE = "\"SSLServerSocket info\":";
    private final static String LEGACY_CS = "\"[TLS_RSA_WITH_NULL_SHA256]\"";

    private final static String NO_COMMON_IN_CS_MSG = "no cipher suites in common";

    private final static String KEY_EXCHANGE_FAILED_MSG = "key exchange failed";

    ServerSocketConfigTest(String protocol) {
        serverAddress = InetAddress.getLoopbackAddress();
        this.protocol = protocol;
    }

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        if (PROTOCOLS[0].equalsIgnoreCase(protocol)) {
            return createSSLContext(TRUSTED_CERTS, null,
                    getServerContextParameters());
        } else {
            return createSSLContext(TRUSTED_CERTS, END_ENTITY_CERTS,
                    getServerContextParameters());
        }
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        if (PROTOCOLS[0].equalsIgnoreCase(protocol) || PROTOCOLS[1].equalsIgnoreCase(protocol)) {
            socket.setEnabledCipherSuites(SERVER_CS);
            socket.setEnabledProtocols(new String[]{protocol});
        } else {
            socket.setEnabledCipherSuites(NO_COMMON_CS);
            socket.setEnabledProtocols(new String[]{PROTOCOLS[0]});
        }
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        String[] clientCipherSuites = PROTOCOLS[1].equalsIgnoreCase(protocol) ?
                CLIENT_TLSV13_CS : CLIENT_TLSV12_CS;

        socket.setEnabledCipherSuites(clientCipherSuites);

        if (PROTOCOLS[1].equalsIgnoreCase(protocol)) {
            socket.setEnabledProtocols(new String[]{protocol});
        } else {
            socket.setEnabledProtocols(new String[]{PROTOCOLS[0]});
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
                    "NO_COMMON_IN_CIPHERSUITE"); // Ensuring args.length is greater than 0 when test JVM starts

            output0.shouldContain(DEBUG_MESSAGE)
                    .shouldContain(SERVER_CS[0])
                    .shouldNotContain(LEGACY_CS)
                    .shouldContain(NO_COMMON_IN_CS_MSG);

            // Testing protocol TSLv1.2,catch log message when key exchange failed between client and server
            var output1 = ProcessTools.executeTestJvm(testSrc, enabledDebug, "ServerSocketConfigTest",
                    PROTOCOLS[0]); // Ensuring args.length is greater than 0 when test JVM starts

            output1.shouldContain(DEBUG_MESSAGE)
                    .shouldContain(SERVER_CS[0])
                    .shouldContain(LEGACY_CS)
                    .shouldContain(KEY_EXCHANGE_FAILED_MSG);

            // Testing protocol TSLv1.3, catch log message when no common cipher suites between client and server
            var output2 = ProcessTools.executeTestJvm(testSrc, enabledDebug, "ServerSocketConfigTest",
                    PROTOCOLS[1]); // Ensuring args.length is greater than 0 when test JVM starts

            output2.shouldContain(DEBUG_MESSAGE)
                   .shouldContain(SERVER_CS[6])
                   .shouldContain(NO_COMMON_IN_CS_MSG);
        }
    }
}