/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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


import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicReference;

/*
 * @test
 * @bug 4424096
 * @summary Verify the specification of DatagramPacket.getData()
 */
public class ReuseBuf {
    private static final String[] msgs = {"Hello World", "Java", "Good Bye"};

    static class Server implements Runnable, AutoCloseable {

        private final DatagramSocket ds;
        private volatile boolean closed;
        private final AtomicReference<Exception> serverFailure = new AtomicReference<>();

        public Server(final InetSocketAddress bindAddr) {
            try {
                this.ds = new DatagramSocket(bindAddr);
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Server bound to address: " + this.ds.getLocalSocketAddress());
        }

        private InetSocketAddress getServerAddress() {
            return (InetSocketAddress) this.ds.getLocalSocketAddress();
        }

        private static void serverLog(final String msg) {
            System.out.println("[server] " + msg);
        }

        @Override
        public void run() {
            serverLog("server processing started");
            try {
                doRun();
            } catch (Exception e) {
                // no need to be concerned with the exception
                // if the server is already closed
                if (!closed) {
                    this.serverFailure.set(e);
                    System.err.println("server exception: " + e);
                    e.printStackTrace();
                }
            } finally {
                close();
            }
        }

        private void doRun() throws Exception {
            byte[] b = new byte[100];
            DatagramPacket dp = new DatagramPacket(b, b.length);
            while (!closed) {
                serverLog("waiting to receive a message at " + ds.getLocalSocketAddress());
                ds.receive(dp);
                String reply = new String(dp.getData(), dp.getOffset(), dp.getLength());
                serverLog("replying to " + dp.getAddress() + ":" + dp.getPort());
                ds.send(new DatagramPacket(reply.getBytes(), reply.length(),
                        dp.getAddress(), dp.getPort()));
                if (reply.equals(msgs[msgs.length - 1])) {
                    break;
                }
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            synchronized (this) {
                if (this.closed) {
                    return;
                }
                this.closed = true;
            }
            System.out.println("Server closing " + ds.getLocalSocketAddress());
            this.ds.close();
        }
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress loopbackEphemeral = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        Server server;
        Thread serverThread;
        try (var _ = server = new Server(loopbackEphemeral);
             DatagramSocket ds = new DatagramSocket(loopbackEphemeral)) {

            InetSocketAddress destAddr = server.getServerAddress();
            // start the server
            serverThread = new Thread(server);
            serverThread.start();

            byte[] b = new byte[100];
            DatagramPacket dp = new DatagramPacket(b, b.length);
            for (String msg : msgs) {
                System.out.println("sending message from " + ds.getLocalSocketAddress()
                        + " to " + destAddr);
                ds.send(new DatagramPacket(msg.getBytes(), msg.length(), destAddr));
                // wait for a reply from the server
                ds.receive(dp);
                System.out.println("received message from: " + dp.getAddress() + ":" + dp.getPort());
                String actual = new String(dp.getData(), dp.getOffset(), dp.getLength());
                if (!msg.equals(actual)) {
                    throw new RuntimeException("Msg expected: " + msg
                            + " of length: " + msg.length() +
                            ", actual received: " + actual + " of length: " + dp.getLength());
                }
            }
            System.out.println("All " + msgs.length + " replies received from the server");
        }
        // wait for the server thread to complete
        System.out.println("awaiting server thread " + serverThread + " to complete");
        serverThread.join();
        Exception serverFailure = server.serverFailure.get();
        if (serverFailure != null) {
            System.err.println("Unexpected failure on server: " + serverFailure);
            throw serverFailure;
        }
    }
}
