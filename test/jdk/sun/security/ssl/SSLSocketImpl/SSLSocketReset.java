/*
 * Copyright (c) 2021, 2022, Azul, Inc. All rights reserved.
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
 * @bug 8268965
 * @summary Socket reset issue for TLS socket close
 * @run main/othervm -Djdk.net.usePlainSocketImpl=true SSLSocketReset
 */

import javax.net.ssl.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

public class SSLSocketReset {

    public static void main(String[] args){
        ServerThread serverThread = null;
        Exception clientException = null;
        try {
            SSLContext sslContext = SSLContext.getDefault();;
            SSLServerSocket sslServerSocket =
                    (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(0);
            serverThread = new ServerThread(sslServerSocket);
            serverThread.start();
            Socket socket = null;
            try {
                socket = new Socket(sslServerSocket.getInetAddress(), sslServerSocket.getLocalPort());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                String msg = "Hello";
                out.writeUTF(msg);
                out.flush();
                in.readUTF();
            } catch (Exception e) {
                clientException = e;
                System.out.println("Client side exception: " + e);
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ie) {
                        System.out.println("Ignore IOException at socket " +
                                "close on client side");
                    }
                }
            }
            serverThread.join();
        } catch(Exception e) {
            throw new RuntimeException("Fails to start SSL server", e);
        }
        if (serverThread.exception instanceof SSLException &&
                serverThread.exception.getMessage().equals("Unsupported or unrecognized SSL message") &&
                clientException instanceof EOFException) {
            System.out.println("Test PASSED");
        } else {
            throw new RuntimeException("TCP connection reset");
        }
    }

    // Thread handling the server socket
    private static class ServerThread extends Thread {
        private SSLServerSocket sslServerSocket = null;
        Exception exception = null;

        ServerThread(SSLServerSocket sslServerSocket){
            this.sslServerSocket = sslServerSocket;
        }

        @Override
        public void run(){
            SSLSocket sslSocket = null;
            try {
                while (true) {
                    sslSocket = (SSLSocket) sslServerSocket.accept();
                    DataInputStream in = new DataInputStream(sslSocket.getInputStream());
                    DataOutputStream out = new DataOutputStream(sslSocket.getOutputStream());
                    String string;
                    while ((string = in.readUTF()) != null) {
                        out.writeUTF(string);
                        out.flush();
                    }
                }
            } catch (Exception e) {
                exception = e;
                System.out.println("Server side exception: " + e);
                e.printStackTrace();
            } finally {
                if (sslSocket != null) {
                    try {
                        sslSocket.close();
                    } catch (IOException ie) {
                        System.out.println("Ignore IOException at socket " +
                                "close on server side");
                    }
                }
            }
        }
    }
}
