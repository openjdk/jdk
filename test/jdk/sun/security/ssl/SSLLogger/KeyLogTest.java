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
 * @bug 8262880
 * @library /test/lib /javax/net/ssl/templates ../../
 * @summary Tests for TLS key logging in the proper format
 * @run main/othervm KeyLogTest
 */

/*
 * This test runs in another process so we can examine the key log after process completion.
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.security.SecurityUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class KeyLogTest extends SSLSocketTemplate {
    KeyLogTest () {
        serverAddress = InetAddress.getLoopbackAddress();
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
            new KeyLogTest().run();
        } else {
            // We are in the test JVM that the test is being ran in.
            var testSrc = "-Dtest.src=" + System.getProperty("test.src");
            var keyLogFile = new File(System.getProperty("test.dir", "."), "keylog.txt");

            if (keyLogFile.exists()) {
                // Delete existing file to avoid having the test incorrectly fail
                keyLogFile.delete();
            }
            var javaxNetDebug = "-Djavax.net.debug.keylog=" + keyLogFile.getAbsolutePath();

            var tlsVersions = new String[]{"TLSv1.2", "TLSv1.3"};

            for (var tlsVersion : tlsVersions) {
                System.out.println("TESTING " + tlsVersion);
                var activeTLSProtocol = "-Djdk.tls.client.protocols=" + tlsVersion;
                var output = ProcessTools.executeTestJvm(
                        testSrc,
                        activeTLSProtocol,
                        javaxNetDebug,
                        "KeyLogTest",
                        "runTest"); // Ensuring args.length is greater than 0 when test JVM starts

                if (output.getExitValue() != 0) {
                    throw new RuntimeException("Test JVM process failed. JVM stderr= " + output.getStderr());
                }
            }

            validateFile(keyLogFile);
        }
    }

    private static void validateFile(File keyLog) throws Exception {
        // The resulting file must have the following structure where the lines between the comments are unordered
        // and every line between the comments must show up exactly twice (though they are represented just once below)
        // Comment
        // CLIENT_RANDOM
        // Comment
        // CLIENT_HANDSHAKE_TRAFFIC_SECRET
        // SERVER_HANDSHAKE_TRAFFIC_SECRET
        // SERVER_TRAFFIC_SECRET_0
        // CLIENT_TRAFFIC_SECRET_0
        var pattern = Pattern.compile("([A-Z0-9_]+) ([a-f0-9]+) ([a-f0-9]+)");
        try (final BufferedReader in = new BufferedReader(new FileReader(keyLog))) {
            // First line must be a comment
            var line = in.readLine();
            if (line.charAt(0) != '#') {
                throw new RuntimeException("First line of log file is not a comment");
            }
            // Next two lines must both be equal and be of type CLIENT_RANDOM
            line = in.readLine();
            var matcher = pattern.matcher(line);
            if (!matcher.matches()) {
                throw new RuntimeException("Second line is not a valid key log");
            }
            if (!matcher.group(1).equals("CLIENT_RANDOM")) {
                throw new RuntimeException("Second line is not CLIENT_RANDOM");
            }
            var thirdLine = in.readLine();
            if (!line.equals(thirdLine)) {
                throw new RuntimeException("Third line does not match second line");
            }

            // Fourth line is a comment from the second run (with TLS 1.3)
            line = in.readLine();
            if (line.charAt(0) != '#') {
                throw new RuntimeException("Fourth line of log file is not a comment");
            }

            // Now things become more complicated because we can't know the ordering for certain.
            // So we use a HashMap to track what we still need to see and what's already been found.
            // Each type will be seeded with an empty string. This indicates we haven't seen it yet.
            // When we see it for the first time, we replace the empty string with the value of that line.
            // The next time we see it (which we can tell becase the Map doesn't contain an empty string)
            // we compare it against the first. If they match, we remove the item from the map entirely.
            // At the end, the map must be empty for us to pass.
            // Additionally, the second element (client random) must be the same for all of them.
            var expectedEntries = new HashMap<String, String>();
            expectedEntries.put("CLIENT_HANDSHAKE_TRAFFIC_SECRET", "");
            expectedEntries.put("SERVER_HANDSHAKE_TRAFFIC_SECRET", "");
            expectedEntries.put("SERVER_TRAFFIC_SECRET_0", "");
            expectedEntries.put("CLIENT_TRAFFIC_SECRET_0", "");
            var clientRandom = "";

            while ((line = in.readLine()) != null) {
                matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                    throw new RuntimeException("Line is not a valid key log: " + line);
                }
                var type = matcher.group(1);
                var newClientRandom = matcher.group(2);
                if (clientRandom.isEmpty()) {
                    clientRandom = newClientRandom;
                } else if (!clientRandom.equals(newClientRandom)) {
                    throw new RuntimeException("Unexpected change in client random");
                }
                var oldValue = expectedEntries.put(type, line);
                if (oldValue == null) {
                    throw new RuntimeException("Unexpected type: " + line);
                } else if (!oldValue.isEmpty()) {
                    if (!oldValue.equals(line)) {
                        throw new RuntimeException("Mismatched lines for type " + type);
                    }
                    expectedEntries.remove(type);
                }
            }

            if (!expectedEntries.isEmpty()) {
                throw new RuntimeException("Expected entries remaining: " + expectedEntries.toString());
            }
        }

    }

    @Override
    protected boolean isCustomizedClientConnection() { return true; }

    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
        var response = "Hello World!";
        var out = new DataOutputStream(socket.getOutputStream());
        try {
            // We don't need to process the data from the socket
            // Simply sending a response right away is sufficient
            // to generate the desired debug output
            var responseBytes = response.getBytes(UTF_8);

            out.writeBytes("HTTP/1.0 200 OK\r\n");
            out.writeBytes("Content-Length: " + responseBytes.length + "\r\n");
            out.writeBytes("Content-Type: text/html\r\n\r\n");
            out.write(responseBytes);
            out.flush();
        } catch (IOException e) {
            out.writeBytes("HTTP/1.0 400 " + e.getMessage() + "\r\n");
            out.writeBytes("Content-Type: text/html\r\n\r\n");
            out.flush();
        }
    }

    @Override
    protected void runClientApplication(int serverPort) throws Exception {
        var context = createClientSSLContext();
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(new NameVerifier());

        var host = serverAddress == null ? "localhost" : serverAddress.getHostAddress();
        var url = new URL("https://" + host + ":" + serverPort + "/");
        var httpsConnection = (HttpsURLConnection) url.openConnection();
        httpsConnection.disconnect();
        try (var in = new BufferedReader(new InputStreamReader(httpsConnection.getInputStream()))) {
            // Getting the input stream from the BufferedReader is sufficient to generate the desired debug output
            // We don't need to process the data
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class NameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

}
