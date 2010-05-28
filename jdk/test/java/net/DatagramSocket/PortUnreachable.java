/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4361783
 * @summary  Test to see if ICMP Port Unreachable on non-connected
 *           DatagramSocket causes a SocketException "socket closed"
 *           exception on Windows 2000.
 */
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.io.InterruptedIOException;

public class PortUnreachable implements Runnable {

    DatagramSocket clientSock;
    int serverPort;
    int clientPort;

    public void run() {
        try {
            InetAddress addr = InetAddress.getLocalHost();

            Thread.currentThread().sleep(2000);

            // send a delayed packet which should mean a delayed icmp
            // port unreachable
            byte b[] = "A late msg".getBytes();
            DatagramPacket packet = new DatagramPacket(b, b.length, addr,
                                                       serverPort);
            clientSock.send(packet);

            // wait before bringing the server up
            Thread.currentThread().sleep(5000);

            DatagramSocket sock = new DatagramSocket(serverPort);
            b = "Grettings from the server".getBytes();
            packet = new DatagramPacket(b, b.length, addr, clientPort);
            sock.send(packet);
            sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    PortUnreachable() throws Exception {

        clientSock = new DatagramSocket();
        clientPort = clientSock.getLocalPort();

        // pick a port for the server
        DatagramSocket sock2 = new DatagramSocket();
        serverPort = sock2.getLocalPort();
        sock2.close();

        // send a burst of packets to the unbound port - we should get back
        // icmp port unreachable messages
        //
        InetAddress addr = InetAddress.getLocalHost();
        byte b[] = "Hello me".getBytes();
        DatagramPacket packet = new DatagramPacket(b, b.length, addr,
                                                   serverPort);
        for (int i=0; i<100; i++)
            clientSock.send(packet);

        // start the server thread
        Thread thr = new Thread(this);
        thr.start();

        // try to receive
        clientSock.setSoTimeout(10000);
        clientSock.receive(packet);

        // done
        clientSock.close();
    }

    public static void main(String[] args) throws Exception {
        new PortUnreachable();
    }

}
