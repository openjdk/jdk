/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8255148
 * @library /test/lib /javax/net/ssl/templates ../../
 * @summary Checks for clarified exception messages for non-fatal SSLSocketImpl exceptions which
 *          can be ignored by the user
 * @run main/othervm IgnorableExceptionMessages
 */

/*
 * This test runs in another process so we can monitor the debug
 * results. The OutputAnalyzer must see correct debug output to return a
 * success.
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.ForceGC;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class IgnorableExceptionMessages extends SSLSocketTemplate {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // A non-empty set of arguments occurs when the "runTest" argument
            // is passed to the test via ProcessTools::executeTestJvm.
            //
            // This is done because an OutputAnalyzer is unable to read
            // the output of the current running JVM, and must therefore create
            // a test JVM. When this case occurs, it will inherit all specified
            // properties passed to the test JVM - debug flags, tls version, etc.
            new IgnorableExceptionMessages().run();
        } else {
            String clientCipherSuites = "-Djdk.tls.client.cipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA";
            String serverCipherSuites = "-Djdk.tls.server.cipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA";
            String javaxDebugFlag = "-Djavax.net.debug=all";
            String className = "IgnorableExceptionMessages";
            String extraArgument = "runTest"; // Triggers the test JVM to execute when args.length > 0
            List<String> jvmArgs = List.of(clientCipherSuites,
                    serverCipherSuites,
                    javaxDebugFlag,
                    className,
                    extraArgument);

            OutputAnalyzer output = ProcessTools.executeTestJvm(jvmArgs);

            if (output.getExitValue() != 0) {
                output.asLines().forEach(System.out::println); // will
                throw new RuntimeException("Test JVM process failed");
            }
            output.shouldContain("SSLSocket close failed. Debug info only. Exception details:");
            output.shouldContain("SSLSocket duplex close failed. Debug info only. Exception details:");
        }
    }

    @Override
    protected void runClientApplication(int serverPort) throws Exception {
        String urlString = serverAddress.getHostAddress() + serverPort + "/";
        URL url = new URL(urlString);

        try {
            new BufferedReader(new InputStreamReader(url.openStream()));
            for(int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                System.gc();
            }
        } catch (SSLHandshakeException sslEx) {
            System.out.println(sslEx.getCause());
            System.out.println(sslEx.getMessage());
        }
    }
}