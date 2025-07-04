/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that Response Message gets set even if a non 100 response
 *          gets return when Expect Continue is set
 * @bug 8352502
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm -Djdk.internal.httpclient.debug=true
 *                    -Djdk.httpclient.HttpClient.log=all
 *                    ExpectContinueResponseMessageTest
 */

import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExpectContinueResponseMessageTest {
    class Control {
        volatile ServerSocket serverSocket = null;
        volatile boolean stop = false;
        volatile String statusLine = null;
    }

    private Thread serverThread = null;
    private volatile Control control;
    static final Logger logger;

    static {
        logger = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");
        logger.setLevel(Level.ALL);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
    }

    public Object[][] args() {
        return new Object[][]{
                // Expected Status Code, Status Line, Expected responseMessage
                { 404, "HTTP/1.1 404 Not Found", "Not Found" },
                { 405, "HTTP/1.1 405 Method Not Allowed", "Method Not Allowed" },
                { 401, "HTTP/1.1 401 Unauthorized", "Unauthorized"}
        };
    }

    @BeforeAll
    public void startServerSocket() throws Exception {
        Control control = this.control = new Control();

        control.serverSocket = new ServerSocket();
        control.serverSocket.setReuseAddress(true);
        control.serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        Runnable runnable = () -> {
            while (!control.stop) {
                try {
                    Socket socket = control.serverSocket.accept();
                    InputStream inputStream = socket.getInputStream();
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                    StringBuilder stringBuilder = new StringBuilder();

                    // Read initial request
                    byte b;
                    while (true) {
                        b = (byte) inputStreamReader.read();
                        stringBuilder.append((char) b);

                        if (stringBuilder.length() >= 4) {
                            char[] lastBytes = new char[4];
                            stringBuilder.getChars(
                                    stringBuilder.length() - 4,
                                    stringBuilder.length(), lastBytes, 0);
                            if (Arrays.equals(lastBytes, new char[]{'\r', '\n', '\r', '\n'})) {
                                break;
                            }
                        }
                    }
                    OutputStream outputStream = socket.getOutputStream();

                    //send a wrong response
                    outputStream.write(control.statusLine.getBytes());
                    outputStream.flush();
                } catch (SocketException e) {
                    // ignore
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        serverThread = new Thread(runnable);
        serverThread.start();
    }

    @AfterAll
    public void stopServerSocket() throws Exception {
        Control control = this.control;
        control.stop = true;
        control.serverSocket.close();
        serverThread.join();
    }

    @ParameterizedTest
    @MethodSource("args")
    public void test(int expectedCode, String statusLine, String expectedMessage) throws Exception {
        String body = "Testing: " + expectedCode;
        Control control = this.control;
        control.statusLine = statusLine + "\r\n\r\n";

        HttpURLConnection connection = createConnection();
        connection.setFixedLengthStreamingMode(body.getBytes().length);
        try {
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(body.getBytes());
            outputStream.close();
        } catch (Exception ex) {
            // swallow the exception
        }

        int responseCode = connection.getResponseCode();
        String responseMessage = connection.getResponseMessage();
        assertTrue(responseCode == expectedCode,
                String.format("Expected %s response, instead received %s", expectedCode, responseCode));
        assertTrue(expectedMessage.equals(responseMessage),
                String.format("Expected Response Message  %s, instead received %s",
                        expectedMessage, responseMessage));
    }

    // Creates a connection with all the common settings used in each test
    private HttpURLConnection createConnection() throws Exception {
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(control.serverSocket.getLocalPort())
                    .toURL();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "Close");
            connection.setRequestProperty("Expect", "100-Continue");

            return connection;
        }
}
