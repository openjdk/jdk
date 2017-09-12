/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6520665 6357133
 * @modules java.base/sun.net.www
 * @run main/othervm NTLMTest
 * @summary 6520665 & 6357133: NTLM authentication issues.
 */

import java.net.*;
import java.io.*;
import sun.net.www.MessageHeader;

public class NTLMTest
{
    public static void main(String[] args) {
        Authenticator.setDefault(new NullAuthenticator());

        try {
            // Test with direct connection.
            ServerSocket serverSS = new ServerSocket(0);
            startServer(serverSS, false);
            runClient(Proxy.NO_PROXY, serverSS.getLocalPort());

            // Test with proxy.
            serverSS = new ServerSocket(0);
            startServer(serverSS, true /*proxy*/);
            SocketAddress proxyAddr = new InetSocketAddress("localhost", serverSS.getLocalPort());
            runClient(new Proxy(java.net.Proxy.Type.HTTP, proxyAddr), 8888);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void runClient(Proxy proxy, int serverPort) {
        try {
            String urlStr = "http://localhost:" + serverPort + "/";
            URL url = new URL(urlStr);
            HttpURLConnection uc = (HttpURLConnection) url.openConnection(proxy);
            uc.getInputStream();

        } catch (ProtocolException e) {
            /* java.net.ProtocolException: Server redirected too many  times (20) */
            throw new RuntimeException("Failed: ProtocolException", e);
        } catch (IOException ioe) {
            /* IOException is OK. We are expecting "java.io.IOException: Server
             * returned HTTP response code: 401 for URL: ..."
             */
            //ioe.printStackTrace();
        } catch (NullPointerException npe) {
            throw new RuntimeException("Failed: NPE thrown ", npe);
        }
    }

    static String[] serverResp = new String[] {
                "HTTP/1.1 401 Unauthorized\r\n" +
                "Content-Length: 0\r\n" +
                "WWW-Authenticate: NTLM\r\n\r\n",

                "HTTP/1.1 401 Unauthorized\r\n" +
                "Content-Length: 0\r\n" +
                "WWW-Authenticate: NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==\r\n\r\n"};

    static String[] proxyResp = new String[] {
                "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                "Content-Length: 0\r\n" +
                "Proxy-Authenticate: NTLM\r\n\r\n",

                "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                "Content-Length: 0\r\n" +
                "Proxy-Authenticate: NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==\r\n\r\n"};

    static void startServer(ServerSocket serverSS, boolean proxy) {
        final ServerSocket ss = serverSS;
        final boolean isProxy = proxy;

        Thread thread = new Thread(new Runnable() {
            public void run() {
                boolean doing2ndStageNTLM = false;
                while (true) {
                    try {
                        Socket s = ss.accept();
                        if (!doing2ndStageNTLM) {
                            handleConnection(s, isProxy ? proxyResp : serverResp, 0, 1);
                            doing2ndStageNTLM = true;
                        } else {
                            handleConnection(s, isProxy ? proxyResp : serverResp, 1, 2);
                            doing2ndStageNTLM = false;
                        }
                        connectionCount++;
                        //System.out.println("connectionCount = " + connectionCount);

                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            } });
            thread.setDaemon(true);
            thread.start();

    }

    static int connectionCount = 0;

    static void handleConnection(Socket s, String[] resp, int start, int end) {
        try {
            OutputStream os = s.getOutputStream();

            for (int i=start; i<end; i++) {
                MessageHeader header = new MessageHeader (s.getInputStream());
                //System.out.println("Input :" + header);
                //System.out.println("Output:" + resp[i]);
                os.write(resp[i].getBytes("ASCII"));
            }

            s.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static class NullAuthenticator extends java.net.Authenticator
    {
        public int count = 0;

        protected PasswordAuthentication getPasswordAuthentication() {
            count++;
            System.out.println("NullAuthenticator.getPasswordAuthentication called " + count + " times");

            return null;
        }

        public int getCallCount() {
            return count;
        }
    }

}
