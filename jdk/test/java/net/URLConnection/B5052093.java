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
 * @bug 5052093
 * @library ../../../sun/net/www/httptest/
 * @build HttpCallback HttpServer ClosedChannelList HttpTransaction
 * @run main B5052093
 * @summary URLConnection doesn't support large files
 */
import java.net.*;
import java.io.*;
import sun.net.www.protocol.file.FileURLConnection;

public class B5052093 implements HttpCallback {
    private static HttpServer server;
    private static long testSize = ((long) (Integer.MAX_VALUE)) + 2;

    public static class LargeFile extends File {
        public LargeFile() {
            super("/dev/zero");
        }

        public long length() {
            return testSize;
        }
    }

    public static class LargeFileURLConnection extends FileURLConnection {
        public LargeFileURLConnection(LargeFile f) throws IOException {
                super(new URL("file:///dev/zero"), f);
        }
    }

    public void request(HttpTransaction req) {
        try {
            req.setResponseHeader("content-length", Long.toString(testSize));
            req.sendResponse(200, "OK");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        server = new HttpServer(new B5052093(), 1, 10, 0);
        try {
            URL url = new URL("http://localhost:"+server.getLocalPort()+"/foo");
            URLConnection conn = url.openConnection();
            int i = conn.getContentLength();
            long l = conn.getContentLengthLong();
            if (i != -1 || l != testSize) {
                System.out.println("conn.getContentLength = " + i);
                System.out.println("conn.getContentLengthLong = " + l);
                System.out.println("testSize = " + testSize);
                throw new RuntimeException("Wrong content-length from http");
            }

            URLConnection fu = new LargeFileURLConnection(new LargeFile());
            i = fu.getContentLength();
            l = fu.getContentLengthLong();
            if (i != -1 || l != testSize) {
                System.out.println("fu.getContentLength = " + i);
                System.out.println("fu.getContentLengthLong = " + l);
                System.out.println("testSize = " + testSize);
                throw new RuntimeException("Wrong content-length from file");
            }
        } finally {
            server.terminate();
        }
    }
}
