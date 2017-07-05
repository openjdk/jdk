/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6270015
 * @summary  Light weight HTTP server
 */

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/* Same as Test1 but requests run in parallel.
 */

public class Test9a extends Test {

    static SSLContext serverCtx, clientCtx;
    static boolean error = false;

    public static void main (String[] args) throws Exception {
        HttpsServer server = null;
        ExecutorService executor=null;
        try {
            String root = System.getProperty ("test.src")+ "/docs";
            System.out.print ("Test9a: ");
            InetSocketAddress addr = new InetSocketAddress (0);
            server = HttpsServer.create (addr, 0);
            HttpHandler h = new FileServerHandler (root);
            HttpContext c1 = server.createContext ("/test1", h);
            executor = Executors.newCachedThreadPool();
            server.setExecutor (executor);
            serverCtx = new SimpleSSLContext(System.getProperty("test.src")).get();
            clientCtx = new SimpleSSLContext(System.getProperty("test.src")).get();
            server.setHttpsConfigurator(new HttpsConfigurator (serverCtx));
            server.start();

            int port = server.getAddress().getPort();
            error = false;
            Thread[] t = new Thread[100];

            t[0] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[1] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            t[2] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[3] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            t[4] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[5] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            t[6] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[7] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            t[8] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[9] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            t[10] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[11] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            t[12] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[13] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            t[14] = test (true, "https", root+"/test1", port, "smallfile.txt", 23);
            t[15] = test (true, "https", root+"/test1", port, "largefile.txt", 2730088);
            for (int i=0; i<16; i++) {
                t[i].join();
            }
            if (error) {
                throw new RuntimeException ("error");
            }

            System.out.println ("OK");
        } finally {
            delay();
            if (server != null)
                server.stop(2);
            if (executor != null)
                executor.shutdown();
        }
    }

    static int foo = 1;

    static ClientThread test (boolean fixedLen, String protocol, String root, int port, String f, int size) throws Exception {
        ClientThread t = new ClientThread (fixedLen, protocol, root, port, f, size);
        t.start();
        return t;
    }

    static Object fileLock = new Object();

    static class ClientThread extends Thread {

        boolean fixedLen;
        String protocol;
        String root;
        int port;
        String f;
        int size;

        ClientThread (boolean fixedLen, String protocol, String root, int port, String f, int size) {
            this.fixedLen = fixedLen;
            this.protocol = protocol;
            this.root = root;
            this.port = port;
            this.f =  f;
            this.size = size;
        }

        public void run () {
            try {
                URL url = new URL (protocol+"://localhost:"+port+"/test1/"+f);
                HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
                if (urlc instanceof HttpsURLConnection) {
                    HttpsURLConnection urlcs = (HttpsURLConnection) urlc;
                    urlcs.setHostnameVerifier (new HostnameVerifier () {
                        public boolean verify (String s, SSLSession s1) {
                            return true;
                        }
                    });
                    urlcs.setSSLSocketFactory (clientCtx.getSocketFactory());
                }
                byte [] buf = new byte [4096];

                String s = "chunk";
                if (fixedLen) {
                    urlc.setRequestProperty ("XFixed", "yes");
                    s = "fixed";
                }
                InputStream is = urlc.getInputStream();
                File temp;
                synchronized (fileLock) {
                    temp = File.createTempFile (s, null);
                    temp.deleteOnExit();
                }
                OutputStream fout = new BufferedOutputStream (new FileOutputStream(temp));
                int c, count = 0;
                while ((c=is.read(buf)) != -1) {
                    count += c;
                    fout.write (buf, 0, c);
                }
                is.close();
                fout.close();

                if (count != size) {
                    System.out.println ("wrong amount of data returned");
                    System.out.println ("fixedLen = "+fixedLen);
                    System.out.println ("protocol = "+protocol);
                    System.out.println ("root = "+root);
                    System.out.println ("port = "+port);
                    System.out.println ("f = "+f);
                    System.out.println ("size = "+size);
                    System.out.println ("temp = "+temp);
                    System.out.println ("count = "+count);
                    error = true;
                }
                String orig = root + "/" + f;
                compare (new File(orig), temp);
                temp.delete();
            } catch (IOException e) {
                error = true;
            }
        }
    }

    /* compare the contents of the two files */

    static void compare (File f1, File f2) throws IOException {
        InputStream i1 = new BufferedInputStream (new FileInputStream(f1));
        InputStream i2 = new BufferedInputStream (new FileInputStream(f2));

        int c1,c2;
        try {
            while ((c1=i1.read()) != -1) {
                c2 = i2.read();
                if (c1 != c2) {
                    throw new RuntimeException ("file compare failed 1");
                }
            }
            if (i2.read() != -1) {
                throw new RuntimeException ("file compare failed 2");
            }
        } finally {
            i1.close();
            i2.close();
        }
    }
}
