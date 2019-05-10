/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class PortUnreachable {

    DatagramSocket clientSock;
    int serverPort;
    int clientPort;

    public void serverSend() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            Thread.currentThread().sleep(1000);
            // send a delayed packet which should mean a delayed icmp
            // port unreachable
            byte b[] = "A late msg".getBytes();
            DatagramPacket packet = new DatagramPacket(b, b.length, addr,
                                                       serverPort);
            clientSock.send(packet);

            DatagramSocket sock = recreateServerSocket(serverPort);
            b = "Greetings from the server".getBytes();
            packet = new DatagramPacket(b, b.length, addr, clientPort);
            sock.send(packet);
            sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    DatagramSocket recreateServerSocket (int serverPort) throws Exception {
        DatagramSocket serverSocket = null;
        int retryCount = 0;
        System.out.println("Attempting to recreate server socket with port: " +
                serverPort);
        while (serverSocket == null) {
            try {
                serverSocket = new DatagramSocket(serverPort, InetAddress.getLocalHost());
            } catch (BindException bEx) {
                if (retryCount++ < 5) {
                    Thread.sleep(500);
                } else {
                    System.out.println("Give up after 5 retries");
                    throw bEx;
                }
            }
        }

        System.out.println("PortUnreachableTest.recreateServerSocket: returning socket == "
                + serverSocket.getLocalAddress() + ":" + serverSocket.getLocalPort());
        return serverSocket;
    }

    PortUnreachable() throws Exception {
        clientSock = new DatagramSocket(new InetSocketAddress(InetAddress.getLocalHost(), 0));
        clientPort = clientSock.getLocalPort();

    }

    void execute () throws Exception{

        // pick a port for the server
        DatagramSocket sock2 = new DatagramSocket(new InetSocketAddress(InetAddress.getLocalHost(), 0));
        serverPort = sock2.getLocalPort();

        // send a burst of packets to the unbound port - we should get back
        // icmp port unreachable messages
        //
        InetAddress addr = InetAddress.getLocalHost();
        byte b[] = "Hello me".getBytes();
        DatagramPacket packet = new DatagramPacket(b, b.length, addr,
                                                   serverPort);
        //close just before sending
        sock2.close();
        for (int i=0; i<100; i++)
            clientSock.send(packet);

        serverSend();
        // try to receive
        b = new byte[25];
        packet = new DatagramPacket(b, b.length, addr, serverPort);
        clientSock.setSoTimeout(10000);
        clientSock.receive(packet);
        System.out.println("client received data packet " + new String(packet.getData()));

        // done
        clientSock.close();
    }

    public static void main(String[] args) throws Exception {
        PortUnreachable test = new PortUnreachable();
        test.execute();
    }

}

