/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6672144
 * @summary HttpURLConnection.getInputStream sends POST request after failed chunked send
 */

import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamingRetry implements Runnable {
    static final int ACCEPT_TIMEOUT = 20 * 1000; // 20 seconds
    ServerSocket ss;

    public static void main(String[] args) throws IOException {
        (new StreamingRetry()).instanceMain();
    }

    void instanceMain() throws IOException {
        test();
        if (failed > 0) throw new RuntimeException("Some tests failed");
    }

    void test() throws IOException {
        ss = new ServerSocket(0);
        ss.setSoTimeout(ACCEPT_TIMEOUT);
        int port = ss.getLocalPort();

        (new Thread(this)).start();

        try {
            URL url = new URL("http://localhost:" + port + "/");
            HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            uc.setDoOutput(true);
            uc.setChunkedStreamingMode(4096);
            OutputStream os = uc.getOutputStream();
            os.write("Hello there".getBytes());

            InputStream is = uc.getInputStream();
            is.close();
        } catch (IOException expected) {
            //expected.printStackTrace();
        } finally {
            ss.close();
        }
    }

    // Server
    public void run() {
        try {
            (ss.accept()).close();
            (ss.accept()).close();
            ss.close();
            fail("The server shouldn't accept a second connection");
         } catch (IOException e) {
            //OK, the clien will close the server socket if successfull
        }
    }

    volatile int failed = 0;
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
}
