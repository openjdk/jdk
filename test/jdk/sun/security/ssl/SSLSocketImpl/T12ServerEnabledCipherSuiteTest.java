/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /javax/net/ssl/templates ../../
 * @summary  when ssl debug is enabled, verify debugging message containing correct information with TLSv1.2
 */

import jdk.test.lib.process.ProcessTools;

import java.net.InetAddress;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public class T12ServerEnabledCipherSuiteTest extends SSLSocketTemplate {
    private final static String protocl = "TLSv1.2";
    private final static String[] serverCipherSuites = {"TLS_RSA_WITH_AES_128_CBC_SHA256","TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256"};
    private final static String[] clientCipherSuites = {"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"};
    private final static String debugMessage = "\"Server Cipher Suites Debugging Information\":";
    private final static String serverVersionMessage = "server version";
    private final static String cipherSuitesMessage = "enabled server cipher suites";

    T12ServerEnabledCipherSuiteTest () {
        serverAddress = InetAddress.getLoopbackAddress();
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setEnabledCipherSuites(serverCipherSuites);
        socket.setEnabledProtocols(new String[] {protocl});
    }
    @Override
    protected void configureClientSocket(SSLSocket socket) {
        socket.setEnabledCipherSuites(clientCipherSuites);
        socket.setEnabledProtocols(new String[] {protocl});
    }
    @Override
    protected void runServerApplication(SSLSocket socket){
        try{
            super.runServerApplication(socket);
        }catch(Exception e){

        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            // A non-empty set of arguments occurs when the "runTest" argument
            // is passed to the test via ProcessTools::executeTestJvm.
            //
            // This is done because an OutputAnalyzer is unable to read
            // the output of the current running JVM, and must therefore create
            // a test JVM. When this case occurs, it will inherit all specified
            // properties passed to the test JVM - debug flags, tls version, etc.
            try {
                new T12ServerEnabledCipherSuiteTest().run();
            }catch (Exception e){
                //do nothing
            }
        } else {
            // We are in the test JVM that the test is being ran in.
            var testSrc = "-Dtest.src=" + System.getProperty("test.src");
            var enabledDebug = "-Djavax.net.debug=ssl,handshake";

            var output = ProcessTools.executeTestJvm(testSrc, enabledDebug, "T12ServerEnabledCipherSuiteTest",
                    "runTest"); // Ensuring args.length is greater than 0 when test JVM starts

            // the program will run failed. The purpose is when the exception happened on the server side, enabled
            // cipher suites on the server side will be printed out to the log
            output.asLines()
                    .stream()
                    .filter(line ->
                            line.startsWith(debugMessage) || line.startsWith(cipherSuitesMessage)
                    );

            output.shouldContain(debugMessage);
            output.shouldContain("\"enabled server cipher suites\": \"[TLS_RSA_WITH_AES_128_CBC_SHA256, TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256]\",");
            // output.shouldNotContain(clientCipherSuites[0]);
        }
    }
}

