/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8237858
 * @summary Verify that when an application is blocked in InputStream.read() on a Socket's
 *          input stream and the native thread doing the read() on the socket's file descriptor
 *          receives a EINTR, then it won't result in the application receiving an exception
 *          from InputStream.read().
 * @requires (os.family != "windows")
 * @compile NativeThread.java
 *
 * @comment the second argument to the SocketReadInterruptTest application is a
 *          reasonably large socket read timeout to exercise a timed wait for the
 *          InputStream.read() call
 * @run main/othervm/native SocketReadInterruptTest 2000 1800000
 *
 * @comment the second argument, "0", to the SocketReadInterruptTest application is
 *          to exercise the InputStream.read() without a timeout
 * @run main/othervm/native SocketReadInterruptTest 2000 0
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class SocketReadInterruptTest {

    public static void main(String[] args) throws Exception {
        System.loadLibrary("NativeThread");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ServerSocket ss = new ServerSocket(0, 0, loopback);
             Socket s1 = new Socket(loopback, ss.getLocalPort())) {

            // amount of time to wait before writing a response on the
            // socket's output stream
            int delayBeforeWrite = Integer.parseInt(args[0]);
            Server server = new Server(ss, (InetSocketAddress) s1.getLocalSocketAddress(),
                    delayBeforeWrite);
            Future<Void> f1 = executor.submit(server);

            // read timeout to be configured on the socket before doing a InputStream.read()
            int readTimeout = Integer.parseInt(args[1]);
            Client client = new Client(s1, readTimeout);
            Future<Void> f2 = executor.submit(client);
            // wait for the client thread to reach a point where it is going to call
            // the socket input stream's read()
            client.ready.join();
            // wait just some more for the client thread to block on InputStream.read(), before
            // we send a signal to interrupt that thread
            sleep(200);
            long nativeTheadId = client.getThreadId();
            System.out.println("Sending SIGPIPE to client thread " + nativeTheadId);
            if (NativeThread.signal(nativeTheadId, NativeThread.SIGPIPE) != 0) {
                throw new RuntimeException("Failed to interrupt the thread");
            }
            // wait for the client to complete
            f2.get();
            // wait for the server to complete
            f1.get();
            System.out.println("OK!");
        } finally {
            executor.shutdown();
        }
    }

    private static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static class Client implements Callable<Void> {

        // completes right before the Client is about to initiate
        // a blocking read() call on the socket's InputStream
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final Socket socket;
        private final int readTimeout;
        private volatile long nativeThreadId = -1;

        public Client(Socket s, int readTimeout) {
            this.socket = s;
            this.readTimeout = readTimeout;
        }

        @Override
        public Void call() throws Exception {
            try {
                doCall();
                return null;
            } catch (Throwable t) {
                if (!ready.isDone()) {
                    ready.completeExceptionally(t);
                }
                System.err.println("Exception in client: " + t);
                t.printStackTrace();
                throw t;
            }
        }

        private void doCall() throws Exception {
            // capture the native thread id of the current thread
            nativeThreadId = NativeThread.getID();
            // set a timeout and then read from the socket's input stream
            try (InputStream in = socket.getInputStream()) {
                socket.setSoTimeout(readTimeout);
                int totalRead = 0;
                int n = 0;
                // let the main thread know that we are about to do a
                // InputStream.read() on the socket
                ready.complete(null);
                while ((n = in.read(new byte[100])) != -1) {
                    totalRead += n;
                }
                System.out.println("read() completed with " + totalRead + " bytes");
                // just the byte count check is OK
                if (totalRead != Server.RESPONSE.length) {
                    throw new AssertionError("unexpected number of bytes read: "
                            + totalRead + ", expected: " + Server.RESPONSE.length);
                }
            }
        }

        /**
         * Returns the id of thread which is executing the {@link #call()} method.
         * Must only be called after {@link #ready} has completed normally.
         */
        private long getThreadId() {
            if (!ready.isDone()) {
                throw new IllegalStateException("Client thread is not yet ready");
            }
            if (ready.isCompletedExceptionally()) {
                throw new IllegalStateException("Client's native thread id unavailable",
                        ready.exceptionNow());
            }
            return nativeThreadId;
        }
    }

    static class Server implements Callable<Void> {
        private static final byte[] RESPONSE = "This is just a test string.".getBytes(US_ASCII);
        private final ServerSocket serverSocket;
        private final InetSocketAddress expectedClientAddr;
        private final int delayBeforeWrite;

        /**
         * Constructs a server
         *
         * @param ss                 The ServerSocket
         * @param expectedClientAddr The InetSocketAddress of the socket, constructed in this
         *                           test, from which a connect() is expected
         * @param delayBeforeWrite   delay in milliseconds before the response is written by the
         *                           server on the accepted socket
         */
        public Server(ServerSocket ss, InetSocketAddress expectedClientAddr, int delayBeforeWrite) {
            serverSocket = ss;
            this.expectedClientAddr = expectedClientAddr;
            this.delayBeforeWrite = delayBeforeWrite;
        }

        @Override
        public Void call() throws Exception {
            try {
                doCall();
                return null;
            } catch (Throwable t) {
                System.err.println("Exception in server: " + t);
                t.printStackTrace();
                throw t;
            }
        }

        private void doCall() throws Exception {
            System.out.println("server listening at " + serverSocket);
            while (true) {
                System.out.println("waiting for connection from " + this.expectedClientAddr);
                Socket client = this.serverSocket.accept();
                System.out.println("accepted connection from " + client);
                // if the connection is from some unexpected client, then close
                // it and wait for a connection from this test's client
                if (!this.expectedClientAddr.equals(client.getRemoteSocketAddress())) {
                    System.out.println("closing unexpected connection from " + client);
                    closeQuietly(client);
                    continue;
                }
                sendResponse(client);
                // we don't expect any more connection from this test
                return;
            }
        }

        private static void closeQuietly(Socket socket) {
            try {
                socket.close();
            } catch (IOException ioe) {
                // ignore
            }
        }

        private void sendResponse(final Socket client) throws IOException {
            try (OutputStream outputStream = client.getOutputStream()) {
                sleep(delayBeforeWrite);
                System.out.println("Sending " + RESPONSE.length + " bytes of response to " + client);
                outputStream.write(RESPONSE);
            }
        }
    }
}
