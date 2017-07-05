/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4669040
 * @summary Test DatagramChannel receive with empty buffer
 * @author Mike McCloskey
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

public class EmptyBuffer {

    static PrintStream log = System.err;

    public static void main(String[] args) throws Exception {
        test();
    }

    static void test() throws Exception {
        Sprintable server = new Server();
        Thread serverThread = new Thread(server);
        serverThread.start();
        while (!server.ready())
            Thread.sleep(50);
        DatagramChannel dc = DatagramChannel.open();
        ByteBuffer bb = ByteBuffer.allocateDirect(12);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(1).putLong(1);
        bb.flip();
        InetAddress address = InetAddress.getLocalHost();
        InetSocketAddress isa = new InetSocketAddress(address, 8888);
        dc.connect(isa);
        dc.write(bb);
        bb.rewind();
        dc.write(bb);
        bb.rewind();
        dc.write(bb);
        Thread.sleep(2000);
        serverThread.interrupt();
        server.throwException();
    }

    public interface Sprintable extends Runnable {
        public void throwException() throws Exception;
        public boolean ready();
    }

    public static class Server implements Sprintable {
        Exception e = null;
        private volatile boolean ready = false;

        public void throwException() throws Exception {
            if (e != null)
                throw e;
        }

        public boolean ready() {
            return ready;
        }

        void showBuffer(String s, ByteBuffer bb) {
            log.println(s);
            bb.rewind();
            for (int i=0; i<bb.limit(); i++) {
                byte element = bb.get();
                log.print(element);
            }
            log.println();
        }

        public void run() {
            SocketAddress sa = null;
            int numberReceived = 0;
            try {
                DatagramChannel dc = DatagramChannel.open();
                dc.socket().bind(new InetSocketAddress(8888));
                ready = true;
                ByteBuffer bb = ByteBuffer.allocateDirect(12);
                bb.clear();
                // Only one clear. The buffer will be full after
                // the first receive, but it should still block
                // and receive and discard the next two
                while (!Thread.interrupted()) {
                    try {
                        sa = dc.receive(bb);
                    } catch (ClosedByInterruptException cbie) {
                        // Expected
                        log.println("Took expected exit");
                        break;
                    }
                    if (sa != null) {
                        log.println("Client: " + sa);
                        showBuffer("RECV", bb);
                        sa = null;
                        numberReceived++;
                        if (numberReceived > 3)
                            throw new RuntimeException("Test failed");
                    }
                }
            } catch (Exception ex) {
                e = ex;
            }
        }
    }

}
