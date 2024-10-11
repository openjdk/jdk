/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8050499
 * @summary Attempt to provoke error 316 on OS X in NativeSignal.signal()
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CountDownLatch;

public class StressNativeSignal {
    private UDPThread udpThread;
    private ServerSocketThread serverSocketThread;

    StressNativeSignal() {
        serverSocketThread = initServerSocketThread();
        if (serverSocketThread != null) {
            serverSocketThread.start();
        }

        udpThread = initUDPThread();
        if (udpThread != null) {
            udpThread.start();
        }
    }

    private UDPThread initUDPThread() {
        UDPThread aUDPThread = null;
        try {
            aUDPThread = new UDPThread();
        } catch (Exception z) {
            System.err.println("failed to create and start a UDPThread");
            z.printStackTrace();
        }
        return aUDPThread;
    }

    private ServerSocketThread initServerSocketThread() {
        ServerSocketThread aServerSocketThread = null;
        try {
            aServerSocketThread = new ServerSocketThread();

        } catch (Exception z) {
            System.err.println("failed to create and start a ServerSocketThread");
            z.printStackTrace();
        }
        return aServerSocketThread;
    }

    public static void main(String[] args) throws Throwable {
        StressNativeSignal test = new StressNativeSignal();
        test.waitForTestThreadsToStart();
        test.shutdown();
    }

    public void shutdown() {
        if ((udpThread != null) && udpThread.isAlive()) {
            udpThread.terminate();
            try {
                udpThread.join();
            } catch (Exception z) {
                z.printStackTrace(System.err);
            }
        } else {
            System.out.println("UDPThread test scenario was not run");
        }

        if ((serverSocketThread != null) && (serverSocketThread.isAlive())) {
            serverSocketThread.terminate();
            try {
                serverSocketThread.join();
            } catch (Exception z) {
                z.printStackTrace(System.err);
            }
        } else {
            System.out.println("ServerSocketThread test scenario was not run");
        }
    }

    public void waitForTestThreadsToStart() {
        if ((udpThread != null) && udpThread.isAlive()) {
            udpThread.waitTestThreadStart();
        }
        if ((serverSocketThread != null) && (serverSocketThread.isAlive())) {
            serverSocketThread.waitTestThreadStart();
        }
    }

    public class ServerSocketThread extends Thread {
        private volatile boolean shouldTerminate;
        private ServerSocket socket;
        private final CountDownLatch threadStarted = new CountDownLatch(1);

        public ServerSocketThread () throws Exception {
            socket = new ServerSocket(1122);
        }

        public void run() {

            try {
                threadStarted.countDown();
                Socket client = socket.accept();
                client.close();
                throw new RuntimeException("Unexpected return from accept call");
            } catch (Exception z) {
                System.err.println("ServerSocketThread: caught exception " + z.getClass().getName());
                if (!shouldTerminate) {
                    z.printStackTrace(System.err);
                }
            }
        }

        public void terminate() {
            shouldTerminate = true;
            try {
                socket.close();
            } catch (Exception z) {
                z.printStackTrace(System.err);
                // ignore
            }
        }

        public void waitTestThreadStart() {
            try {
                threadStarted.await();
            } catch (Exception z) {
                z.printStackTrace(System.err);
                // ignore
            }
        }
    }

    public class UDPThread extends Thread {
        private DatagramChannel channel;
        private volatile boolean shouldTerminate;
        private final CountDownLatch threadStarted = new CountDownLatch(1);

        public UDPThread () throws Exception {

            channel = DatagramChannel.open();
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 6553600);
            channel.bind(new InetSocketAddress(19870));
        }

        @Override
        public void run() {

            ByteBuffer buf = ByteBuffer.allocate(6553600);
            threadStarted.countDown();
            do {
                try {
                    buf.rewind();
                    channel.receive(buf);
                } catch (IOException z) {
                    System.err.println("UDPThread: caught exception " + z.getClass().getName());
                    if (!shouldTerminate) {
                        z.printStackTrace(System.err);
                    }
                }
            } while (!shouldTerminate);
        }

        public void terminate() {
            shouldTerminate = true;
            try {
                channel.close();
            } catch (Exception z) {
                System.err.println("UDPThread: caught exception " + z.getClass().getName());
                z.printStackTrace(System.err);
                // ignore
            }
        }

        public void waitTestThreadStart() {
            try {
                threadStarted.await();
            } catch (Exception z) {
                z.printStackTrace(System.err);
                // ignore
            }
        }
    }

}
