/*
 * Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 4333920
 * @library ../../../../../sun/net/www/httptest/
 * @build HttpCallback HttpServer ClosedChannelList HttpTransaction
 * @run main ChunkedEncodingTest
 * @summary ChunkedEncodingTest unit test
 */

import java.io.*;
import java.net.*;
import java.security.*;

public class ChunkedEncodingTest implements HttpCallback {
    private static String FNPrefix;
    private String[] respBody = new String[52];
    private byte[][] bufs = new byte[52][8*1024];
    private static MessageDigest md5;
    private static byte[] file1Mac, file2Mac;
    public void request (HttpTransaction req) {
        try {
            FileInputStream fis = new FileInputStream(FNPrefix+"test.txt");
            DigestInputStream dis = null;
            md5.reset();
            dis = new DigestInputStream(fis, md5);
            for (int i = 0; i < 52; i++) {
                int n = dis.read(bufs[i]);
                respBody[i] = new String(bufs[i], 0, n);
            }
            file1Mac = dis.getMessageDigest().digest();
            dis.close();
            req.setResponseEntityBody(respBody);
            req.sendResponse(200, "OK");
            req.orderlyClose();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void read (InputStream is) throws IOException {
        int c;
        System.out.println ("reading");

        DigestInputStream dis = null;
        md5.reset();
        dis = new DigestInputStream(is, md5);
        while ((c=dis.read()) != -1);
        file2Mac = dis.getMessageDigest().digest();
        dis.close();
        System.out.println ("finished reading");
    }

    static void client (String u) throws Exception {
        URL url = new URL (u);
        System.out.println ("client opening connection to: " + u);
        URLConnection urlc = url.openConnection ();
        InputStream is = urlc.getInputStream ();
        read (is);
        is.close();
    }

    static HttpServer server;

    public static void test () throws Exception {
        try {

            FNPrefix = System.getProperty("test.src", ".")+"/";
            md5 = MessageDigest.getInstance("MD5");
            server = new HttpServer (new ChunkedEncodingTest(), 1, 10, 0);
            System.out.println ("Server: listening on port: " + server.getLocalPort());
            client ("http://localhost:"+server.getLocalPort()+"/d1/foo.html");
        } catch (Exception e) {
            if (server != null) {
                server.terminate();
            }
            throw e;
        }
        if (!MessageDigest.isEqual(file1Mac, file2Mac)) {
            except ("The file sent by server is different from the original file");
        }

        server.terminate();
    }

    public static void main(String[] args) throws Exception {
        test();
    }

    public static void except (String s) {
        server.terminate();
        throw new RuntimeException (s);
    }
}
