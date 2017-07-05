/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test socketchannel vector IO
 * @library ..
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import sun.misc.*;


public class VectorIO {

    static Random generator = new Random();

    static int testSize;

    public static void main(String[] args) throws Exception {
        testSize = 1;
        runTest();
        for(int i=15; i<18; i++) {
            testSize = i;
            runTest();
        }
    }

    static void runTest() throws Exception {
        System.err.println("Length " + testSize);
        Server sv = new Server(testSize);
        sv.start();
        bufferTest(sv.port());
        if (sv.finish(8000) == 0)
            throw new Exception("Failed: Length = " + testSize);
    }

    static void bufferTest(int port) throws Exception {
        ByteBuffer[] bufs = new ByteBuffer[testSize];
        for(int i=0; i<testSize; i++) {
            String source = "buffer" + i;
            if (generator.nextBoolean())
                bufs[i] = ByteBuffer.allocateDirect(source.length());
            else
                bufs[i] = ByteBuffer.allocate(source.length());

            bufs[i].put(source.getBytes("8859_1"));
            bufs[i].flip();
        }

        // Get a connection to the server
        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress isa = new InetSocketAddress(lh, port);
        SocketChannel sc = SocketChannel.open();
        sc.connect(isa);
        sc.configureBlocking(false);

        // Write the data out
        long bytesWritten = 0;
        do {
            bytesWritten = sc.write(bufs);
        } while (bytesWritten > 0);

        try {
            Thread.currentThread().sleep(500);
        } catch (InterruptedException ie) { }

        // Clean up
        sc.close();
    }

    static class Server
        extends TestThread
    {
        static Random generator = new Random();

        final int testSize;
        final ServerSocketChannel ssc;

        Server(int testSize) throws IOException {
            super("Server " + testSize);
            this.testSize = testSize;
            this.ssc = ServerSocketChannel.open().bind(new InetSocketAddress(0));
        }

        int port() {
            return ssc.socket().getLocalPort();
        }

        void go() throws Exception {
            bufferTest();
        }

        void bufferTest() throws Exception {
            ByteBuffer[] bufs = new ByteBuffer[testSize];
            for(int i=0; i<testSize; i++) {
                String source = "buffer" + i;
                if (generator.nextBoolean())
                    bufs[i] = ByteBuffer.allocateDirect(source.length());
                else
                    bufs[i] = ByteBuffer.allocate(source.length());
            }

            // Get a connection from client
            SocketChannel sc = null;

            try {

                ssc.configureBlocking(false);

                for (;;) {
                    sc = ssc.accept();
                    if (sc != null)
                        break;
                    Thread.sleep(50);
                }

                // Read data into multiple buffers
                long bytesRead = 0;
                do {
                    bytesRead = sc.read(bufs);
                } while (bytesRead > 0);

                // Check results
                for(int i=0; i<testSize; i++) {
                    String expected = "buffer" + i;
                    bufs[i].flip();
                    int size = bufs[i].capacity();
                    byte[] data = new byte[size];
                    for(int j=0; j<size; j++)
                        data[j] = bufs[i].get();
                    String message = new String(data, "8859_1");
                    if (!message.equals(expected))
                        throw new Exception("Wrong data: Got "
                                            + message + ", expected "
                                            + expected);
                }

            } finally {
                // Clean up
                ssc.close();
                if (sc != null)
                    sc.close();
            }

        }

    }

}
