/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8224829
 * @summary AsyncSSLSocketClose.java has timing issue.
 * @library /javax/net/ssl/templates
 * @run main/othervm BlockedAsyncClose
 */

import javax.net.ssl.*;
import java.io.*;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * To manually verify that the write thread was blocked when socket.close() is called,
 * run the test with -Djavax.net.debug=ssl. You should see the message
 * "SSLSocket output duplex close failed: SO_LINGER timeout, close_notify message cannot be sent."
 */
public class BlockedAsyncClose extends SSLContextTemplate implements Runnable {
    SSLSocket socket;
    SSLServerSocket ss;

    // Is the socket ready to close?
    private final CountDownLatch closeCondition = new CountDownLatch(1);
    private final Lock writeLock = new ReentrantLock();

    public static void main(String[] args) throws Exception {
        new BlockedAsyncClose().runTest();
    }

    public void runTest() throws Exception {
        SSLServerSocketFactory sslssf = createServerSSLContext().getServerSocketFactory();
        InetAddress loopback = InetAddress.getLoopbackAddress();
        ss = (SSLServerSocket)sslssf.createServerSocket();
        ss.bind(new InetSocketAddress(loopback, 0));

        SSLSocketFactory sslsf = createClientSSLContext().getSocketFactory();
        socket = (SSLSocket)sslsf.createSocket(loopback, ss.getLocalPort());
        SSLSocket serverSoc = (SSLSocket)ss.accept();
        ss.close();

        new Thread(this).start();
        serverSoc.startHandshake();

        boolean closeIsReady = closeCondition.await(90L, TimeUnit.SECONDS);
        if (!closeIsReady) {
            System.out.println(
                    "Ignore, the closure is not ready yet in 90 seconds.");
            return;
        }

        socket.setSoLinger(true, 10);

        // if the writeLock is not released by the other thread within 10
        // seconds it is probably blocked, and we can try to close the socket
        while (writeLock.tryLock(10, TimeUnit.SECONDS)) {
            writeLock.unlock();
        }

        System.out.println("Calling socket.close()");
        socket.close();
        System.out.flush();
    }

    // block in write
    public void run() {
        byte[] ba = new byte[1024];
        Arrays.fill(ba, (byte) 0x7A);

        try {
            OutputStream os = socket.getOutputStream();
            int count = 0;

            // 1st round write
            count += ba.length;
            System.out.println(count + " bytes to be written");
            os.write(ba);
            System.out.println(count + " bytes written");

            // Signal, ready to close.
            closeCondition.countDown();

            // write more
            while (true) {
                count += ba.length;

                System.out.println(count + " bytes to be written");

                writeLock.lock();
                os.write(ba);
                // This isn't in a try/finally. If an exception is thrown
                // and the lock is released, the main thread will
                // loop until the test times out. So don't release it.
                writeLock.unlock();

                System.out.println(count + " bytes written");
            }
        } catch (SocketException se) {
            // the closing may be in progress
            System.out.println("interrupted? " + se);
        } catch (Exception e) {
            if (socket.isClosed() || socket.isOutputShutdown()) {
                System.out.println("interrupted, the socket is closed");
            } else {
                throw new RuntimeException("interrupted?", e);
            }
        }
    }
}
