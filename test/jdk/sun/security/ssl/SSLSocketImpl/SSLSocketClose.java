/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
// Please run in othervm mode.  SunJSSE does not support dynamic system
// properties, no way to re-use system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8209333
 * @summary Socket reset issue for TLS 1.3 socket close
 * @library /javax/net/ssl/templates
 * @run main/othervm SSLSocketClose
 */

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.SocketException;

public class SSLSocketClose extends SSLSocketTemplate {

    public static void main(String[] args) throws Exception {
        for (int i = 0; i<= 10; i++) {
            System.out.println("===================================");
            System.out.println("loop " + i);
            System.out.println("===================================");
            new SSLSocketClose().run();
        }
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setNeedClientAuth(false);
        socket.setEnableSessionCreation(true);
        socket.setUseClientMode(false);
    }

    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
        System.out.println("Reading data from client");
        BufferedReader serverReader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        String data = serverReader.readLine();
        System.out.println("Received data from client: " + data);

        System.out.println("Sending data to client ...");
        String serverData = "Hi, I am server";
        BufferedWriter os = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream()));
        os.write(serverData, 0, serverData.length());
        os.newLine();
        os.flush();

        System.out.println("Reading more data from client");
        data = serverReader.readLine();
        System.out.println("Received data from client: " + data);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        try {
            socket.setSoLinger(true, 3);
        } catch (SocketException e) {
            throw new RuntimeException("Could not configure client socket", e);
        }
    }

    @Override
    protected void runClientApplication(SSLSocket socket) throws Exception {
        String clientData = "Hi, I am client";
        System.out.println("Sending data to server ...");

        BufferedWriter os = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream()));
        os.write(clientData, 0, clientData.length());
        os.newLine();
        os.flush();

        System.out.println("Reading data from server");
        BufferedReader is = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        String data = is.readLine();
        System.out.println("Received Data from server: " + data);

        System.out.println("Sending more data to server ...");
        os.write(clientData, 0, clientData.length());
        os.newLine();
        os.flush();

        socket.close();
    }
}

