/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4957695
 * @library ../../httptest/
 * @build HttpCallback HttpServer ClosedChannelList HttpTransaction AbstractCallback
 * @summary URLJarFile.retrieve does not delete tmpFile on IOException
 */

import java.io.*;
import java.net.*;

public class B4957695 {

    static int count = 0;
    static boolean error = false;

    static void read (InputStream is) throws IOException {
        int c,len=0;
        while ((c=is.read()) != -1) {
            len += c;
        }
        System.out.println ("read " + len + " bytes");
    }

    static class CallBack extends AbstractCallback {

        public void request (HttpTransaction req, int count) {
            try {
                System.out.println ("Request received");
                req.setResponseEntityBody (new FileInputStream ("foo1.jar"));
                System.out.println ("content length " + req.getResponseHeader (
                        "Content-length"
                ));
                req.sendPartialResponse (200, "Ok");
                req.abortiveClose();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    };

    static HttpServer server;

    public static void main (String[] args) throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir");
        String[] list1 = listTmpFiles(tmpdir);
        //server = new HttpServer (new CallBack(), 10, 1, 0);
        server = new HttpServer (new CallBack(), 1, 5, 0);
        int port = server.getLocalPort();
        System.out.println ("Server: listening on port: " + port);
        URL url = new URL ("jar:http://localhost:"+port+"!/COPYRIGHT");
        try {
            URLConnection urlc = url.openConnection ();
            InputStream is = urlc.getInputStream();
            read (is);
            is.close();
        } catch (IOException e) {
            System.out.println ("Received IOException as expected");
        }
        server.terminate();
        String[] list2 = listTmpFiles(tmpdir);
        if (!sameList (list1, list2)) {
            throw new RuntimeException ("some jar_cache files left behind");
        }
    }


    static String[] listTmpFiles (String d) {
        File dir = new File (d);
        return dir.list (new FilenameFilter () {
            public boolean accept (File dr, String name) {
                return (name.startsWith ("jar_cache"));
            }
        });
    }

    static boolean sameList (String[] list1, String[] list2) {
        if (list1.length != list2.length) {
            return false;
        }
        for (int i=0; i<list1.length; i++) {
            String s1 = list1[i];
            String s2 = list2[i];
            if ((s1 == null && s2 != null)) {
                return false;
            } else if ((s2 == null && s1 != null)) {
                return false;
            } else if (s1 == null) {
                return true;
            } else if (!s1.equals(s2)) {
                return false;
            }
        }
        return true;
    }
}
