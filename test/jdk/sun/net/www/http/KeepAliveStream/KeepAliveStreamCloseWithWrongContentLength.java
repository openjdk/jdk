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
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class KeepAliveStreamCloseWithWrongContentLength {

    private final static String path = "/KeepAliveStreamCloseWithWrongContentLength";
    private final static String getRequest1stLine = "GET %s".formatted(path);

    static class XServer extends Thread implements AutoCloseable {

        final ServerSocket serverSocket;
        volatile Socket clientSocket;

        XServer (InetAddress address) throws IOException {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serversocket = serverSocketChannel.socket();
            serversocket.bind(new InetSocketAddress(address, 0));
            this.serverSocket = serversocket;
        }

        public int getLocalPort() {
            return serverSocket.getLocalPort();
        }

        public void run() {

            try {
                ByteArrayOutputStream clientBytes;
                clientSocket = null;

                // in a concurrent test environment it can happen that other rouge clients connect to this server
                // so we need to identify and connect only to the client from this test
                // if the rouge client sends as least bytes as there is in getRequest1stLine it will be discarded and
                // the test should proceed otherwise it should timeout on readNBytes below
                do {
                    if (clientSocket != null) {
                        final String client = "%s:%d".formatted(
                                clientSocket.getInetAddress().getHostAddress(),
                                clientSocket.getPort()
                        );
                        try {
                            clientSocket.close();
                        }
                        catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        finally {
                            System.err.println("rogue client (%s) connection attempt, ignoring".formatted(client));
                        }
                    }
                    clientSocket = serverSocket.accept();
                    // read HTTP request from client
                    clientBytes = new ByteArrayOutputStream();
                    clientBytes.write(clientSocket.getInputStream().readNBytes(getRequest1stLine.getBytes().length));
                }
                while(!getRequest1stLine.equals(clientBytes.toString()));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            try  {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream());
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
                clientSocket.getChannel().shutdownOutput();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void close() throws Exception {
            final var clientSocket = this.clientSocket;
            try {
                long drained = drain(clientSocket.getChannel());
                System.err.printf("Server drained %d bytes from the channel%n", drained);
            } catch (Exception x) {
                System.err.println("Server failed to drain client socket: " + x);
                x.printStackTrace();
            }
            serverSocket.close();
        }

    }

    static long drain(SocketChannel channel) throws IOException {
        if (!channel.isOpen()) return 0;
        System.err.println("Not reading server: draining socket");
        var blocking = channel.isBlocking();
        if (blocking) channel.configureBlocking(false);
        long count = 0;
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024);
            int read;
            while ((read = channel.read(buffer)) > 0) {
                count += read;
                buffer.clear();
            }
            return count;
        } finally {
            if (blocking != channel.isBlocking()) {
                channel.configureBlocking(blocking);
            }
        }
    }


    public static void main (String[] args) throws Exception {

        final InetAddress loopback = InetAddress.getLoopbackAddress();

        try (XServer server = new XServer(loopback)) {
            server.start();
            URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .path(path)
                .port(server.getLocalPort())
                .toURL();
            HttpURLConnection urlc = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
            InputStream is = urlc.getInputStream();
            int c = 0;
            while (c != -1) {
                try {
                    c=is.read();
                    System.out.println("client reads: "+c);
                } catch (IOException ioe) {
                    is.read ();
                    break;
                }
            }
            is.close();
        }

    }
}
