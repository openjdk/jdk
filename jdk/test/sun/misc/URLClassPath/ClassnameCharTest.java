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
 * See ClassnameCharTest.sh for details.
 */

import java.io.*;
import java.net.*;
import java.security.*;
import sun.applet.AppletClassLoader;

public class ClassnameCharTest implements HttpCallback {
    private static String FNPrefix;
    private String[] respBody = new String[52];
    private byte[][] bufs = new byte[52][8*1024];
    private static MessageDigest md5;
    private static byte[] file1Mac, file2Mac;
    public void request (HttpTransaction req) {
        try {
            String filename = req.getRequestURI().getPath();
            System.out.println("getRequestURI = "+req.getRequestURI());
            System.out.println("filename = "+filename);
            FileInputStream fis = new FileInputStream(FNPrefix+filename);
            req.setResponseEntityBody(fis);
            req.sendResponse(200, "OK");
            req.orderlyClose();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    static HttpServer server;

    public static void test () throws Exception {
        try {

            FNPrefix = System.getProperty("test.classes", ".")+"/";
            server = new HttpServer (new ClassnameCharTest(), 1, 10, 0);
            System.out.println ("Server: listening on port: " + server.getLocalPort());
            URL base = new URL("http://localhost:"+server.getLocalPort());
            MyAppletClassLoader acl = new MyAppletClassLoader(base);
            Class class1 = acl.findClass("fo o");
            System.out.println("class1 = "+class1);
            // can't test the following class unless platform in unicode locale
            // Class class2 = acl.findClass("\u624b\u518c");
            // System.out.println("class2 = "+class2);
        } catch (Exception e) {
            if (server != null) {
                server.terminate();
            }
            throw e;
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

class MyAppletClassLoader extends AppletClassLoader {
    MyAppletClassLoader(URL base) {
        super(base);
    }

    public Class findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }
}
