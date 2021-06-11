/*
 * Copyright (c) 2002, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4533243 8263364
 * @summary Closing a keep alive stream should not give NullPointerException and should accept a connection from  a
 *          client only from this test
 * @library /test/lib
 * @run main/othervm/timeout=30 KeepAliveStreamCloseWithWrongContentLength
 */

import java.net.*;
import java.io.*;

import jdk.test.lib.net.URIBuilder;

public class KeepAliveStreamCloseWithWrongContentLength {

    private final static String path = "/KeepAliveStreamCloseWithWrongContentLength";
    private final static String getRequest1stLine = "GET %s".formatted(path);

    static class XServer extends Thread {

        ServerSocket serverSocket;
        OutputStream outputStream;

        XServer (ServerSocket socket) {
            serverSocket = socket;
        }

        public void run() {
            try {

                ByteArrayOutputStream clientBytes;
                Socket socket = null;

                // in a concurrent test environment it can happen that other rouge clients connect to this server
                // so we need to identify and connect only to the client from this test
                // if the rouge client sends as least bytes as there is in getRequest1stLine it will be discarded and
                // the test should proceed otherwise it should timeout on readNBytes below
                do {
                    if (socket != null) {
                        final String client = "%s:%d".formatted(
                                socket.getInetAddress().getHostAddress(),
                                socket.getPort()
                        );
                        try {
                            socket.close();
                        }
                        catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        finally {
                            System.err.println("rogue client (%s) connection attempt, ignoring".formatted(client));
                        }
                    }

                    socket = serverSocket.accept();

                    // read HTTP request from client
                    clientBytes = new ByteArrayOutputStream();
                    clientBytes.write(socket.getInputStream().readNBytes(getRequest1stLine.getBytes().length));
                }
                while(!getRequest1stLine.equals(clientBytes.toString()));

                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream = socket.getOutputStream());
                outputStreamWriter.write("HTTP/1.0 200 OK\n");

                // Note: The client expects 10 bytes.
                outputStreamWriter.write("Content-Length: 10\n");
                outputStreamWriter.write("Content-Type: text/html\n");

                // Note: If this line is missing, everything works fine.
                outputStreamWriter.write("Connection: Keep-Alive\n");
                outputStreamWriter.write("\n");

                // Note: The (buggy) server only sends 9 bytes.
                outputStreamWriter.write("123456789");
                outputStreamWriter.flush();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {if (outputStream != null) { outputStream.close(); }} catch (IOException e) {}
            }
        }
    }

    public static void main (String[] args) throws Exception {

        final Integer port = 61234;

        final InetAddress loopback = InetAddress.getLoopbackAddress();
        final ServerSocket serversocket = new ServerSocket();
        serversocket.bind(new InetSocketAddress(loopback, port));

        try {
            XServer server = new XServer(serversocket);
            server.start ();
            URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .path(path)
                .port(port)
                .toURL();
            HttpURLConnection urlc = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
            InputStream is = urlc.getInputStream();
            int c = 0;
            while (c != -1) {
                try {
                    c=is.read();
                } catch (IOException ioe) {
                    is.read ();
                    break;
                }
            }
            is.close();
        } catch (IOException e) {
            throw new RuntimeException (e);
        } catch (NullPointerException e) {
            throw new RuntimeException (e);
        } finally {
            serversocket.close();
        }
    }
}
