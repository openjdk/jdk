/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5054016
 * @run main/othervm/timeout=300 checkError
 * @summary get the failure immediately when writing individual chunks over socket fail
 */

import java.io.*;
import java.net.*;
import java.util.StringTokenizer;


public class checkError {
    static final int TEST_PASSED = 95;
    static final int TEST_FAILED = 97;

    static int testStatus = TEST_PASSED;

    static String serverName = "localhost";
    static int bufferSize = 8192; // 8k
    static int totalBytes = 1048576; // 1M

    static int j = 0;

    static public Object threadStarting = new Object();
    static public Object threadWaiting = new Object();


    public static void main(String[] args) throws Exception {
        HttpURLConnection conn = null;
        OutputStream toServer = null;
        byte[] buffer = null;
        HTTPServer server = null;
        synchronized(threadWaiting) {
            System.out.println("HTTP-client>Starting default Http-server");
            synchronized(threadStarting) {
                server = new HTTPServer();
                server.start();
                try {
                    System.out.println("waiting server to be start");
                    threadStarting.wait();
                } catch (InterruptedException e) {
                }
            }
            int port = server.getPort();
            URL url = new URL("http://" + serverName + ":" + port);
            conn = (HttpURLConnection )url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            System.out.println("assigning 1024 to the chunk length");
            conn.setChunkedStreamingMode(1024);
            conn.connect();

            toServer = conn.getOutputStream();
            buffer = getThickBuffer(bufferSize);
            System.out.println("sending " + totalBytes + " bytes");
        }

        int byteAtOnce = 0;
        int sendingBytes = totalBytes;
        try {
            while (sendingBytes > 0) {
                if (sendingBytes > bufferSize) {
                    byteAtOnce = bufferSize;
                } else {
                    byteAtOnce = sendingBytes;
                }
                toServer.write(buffer, 0, byteAtOnce);
                sendingBytes -= byteAtOnce;
                // System.out.println((totalBytes - sendingBytes) + " was sent");
                toServer.flush();
            }
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            System.out.println("***ERR***> UNEXPECTED error: " + e);
            testStatus = TEST_FAILED;
            testExit();
        } catch (IOException e) {
            // e.printStackTrace();
            // this is the expected IOException
            // due to server.close()
            testStatus = TEST_PASSED;
            testExit();
        } finally {
            toServer.close();
        }

        // we have not received the expected IOException
        // test fail
        testStatus = TEST_FAILED;
        testExit();

    }

    static void testExit() {
        if (testStatus == TEST_FAILED) {
            throw new RuntimeException("Test Failed: haven't received the expected IOException");
        } else {
            System.out.println("TEST PASSED");
        }
        System.exit(testStatus);
    }

    static byte[] getThickBuffer(int size) {

        byte[] buffer = new byte[size];

        for (int i = 0; i < size; i++) {
            if (j > 9)
                j = 0;
            String s = Integer.toString(j);
            buffer[i] = (byte )s.charAt(0);
            j++;
        }

        return buffer;
    }
}


class HTTPServer extends Thread {

    static volatile boolean isCompleted;

    Socket client;
    ServerSocket serverSocket;

    int getPort() {
        return serverSocket.getLocalPort();
    }

    public void run() {

        synchronized(checkError.threadStarting) {

            try {
                serverSocket = new ServerSocket(0, 100);
            } catch (Exception e) {
                e.printStackTrace();
                checkError.testStatus = checkError.TEST_FAILED;
                return;
            }
            checkError.threadStarting.notify();
        }

        try {
            client = serverSocket.accept();
        } catch (Exception e) {
            e.printStackTrace();
            checkError.testStatus = checkError.TEST_FAILED;
            return;
        }

        System.out.println("Server started");

        BufferedReader in = null;
        PrintStream out = null;
        InputStreamReader reader = null;
        String version = null;
        String line;
        String method;

        synchronized(checkError.threadWaiting) {
            try {
                reader = new InputStreamReader(client.getInputStream());
                in = new BufferedReader(reader);
                line = in.readLine();

            } catch (Exception e) {
                e.printStackTrace();
                checkError.testStatus = checkError.TEST_FAILED;
                return;
            }
            StringTokenizer st = new StringTokenizer(line);
            method = st.nextToken();
            String fileName = st.nextToken();

            // save version for replies
            if (st.hasMoreTokens()) version = st.nextToken();

            System.out.println("HTTP version: " + version);

        }

        try {

            while (line != null && line.length() > 0) {
                line = in.readLine();
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            checkError.testStatus = checkError.TEST_FAILED;
            return;
        }

        if (method.equals("POST")) {
            System.out.println("receiving data");
            byte[] buf = new byte[1024];
            try {
                //reading bytes until chunk whose size is zero,
                // see 19.4.6 Introduction of Transfer-Encoding in RFC2616
                int count = 0;
                while (count <=5) {
                    count++;
                    in.readLine();
                }

                System.out.println("Server socket is closed");
                in.close();
                client.close();
                serverSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
                checkError.testStatus = checkError.TEST_FAILED;
                return;
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                checkError.testStatus = checkError.TEST_FAILED;
                return;
            }

        }
    }

}
