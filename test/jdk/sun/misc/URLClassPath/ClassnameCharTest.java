/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4957669 5017871
 * @summary cannot load class names containing some JSR 202 characters;
 *          plugin does not escape unicode character in http request
 * @modules java.desktop/sun.applet
 *          jdk.httpserver
 * @compile -XDignore.symbol.file=true ClassnameCharTest.java
 * @run main ClassnameCharTest
 */

import java.io.*;
import java.net.*;
import java.util.jar.*;
import com.sun.net.httpserver.*;
import sun.applet.AppletClassLoader;

public class ClassnameCharTest {
    static String FNPrefix = System.getProperty("test.src", ".") + File.separator;
    static File classesJar = new File(FNPrefix + "testclasses.jar");
    static HttpServer server;

    public static void realMain(String[] args) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    String filename = exchange.getRequestURI().getPath();
                    System.out.println("getRequestURI = " + exchange.getRequestURI());
                    System.out.println("filename = " + filename);
                    try (FileInputStream fis = new FileInputStream(classesJar);
                         JarInputStream jis = new JarInputStream(fis)) {
                        JarEntry entry;
                        while ((entry = jis.getNextJarEntry()) != null) {
                            if (filename.endsWith(entry.getName())) {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                byte[] buf = new byte[8092];
                                int count = 0;
                                while ((count = jis.read(buf)) != -1)
                                    baos.write(buf, 0, count);
                                exchange.sendResponseHeaders(200, baos.size());
                                try (OutputStream os = exchange.getResponseBody()) {
                                    baos.writeTo(os);
                                }
                                return;
                            }
                        }
                        fail("Failed to find " + filename);
                    }
                } catch (IOException e) {
                    unexpected(e);
                }
            }
        });
        server.start();
        try {
            URL base = new URL("http://localhost:" + server.getAddress().getPort());
            System.out.println ("Server: listening on " + base);
            MyAppletClassLoader acl = new MyAppletClassLoader(base);
            Class<?> class1 = acl.findClass("fo o");
            System.out.println("class1 = " + class1);
            pass();
            // can't test the following class unless platform in unicode locale
            // Class class2 = acl.findClass("\u624b\u518c");
            // System.out.println("class2 = "+class2);
        } finally {
            server.stop(0);
        }
    }

    static class MyAppletClassLoader extends AppletClassLoader {
        MyAppletClassLoader(URL base) {
            super(base);
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            return super.findClass(name);
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;

    static boolean pass() {
        passed++;
        return true;
    }

    static boolean fail() {
        failed++;
        if (server != null) {
            server.stop(0);
        }
        Thread.dumpStack();
        return false;
    }

    static boolean fail(String msg) {
        System.out.println(msg);
        return fail();
    }

    static void unexpected(Throwable t) {
        failed++;
        if (server != null) {
            server.stop(0);
        }
        t.printStackTrace();
    }

    static boolean check(boolean cond) {
        if (cond) {
            pass();
        } else {
            fail();
        }
        return cond;
    }

    static boolean equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) {
            return pass();
        } else {
            return fail(x + " not equal to " + y);
        }
    }

    public static void main(String[] args) throws Throwable {
        try {
            realMain(args);
        } catch (Throwable t) {
            unexpected(t);
        }
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) {
            throw new AssertionError("Some tests failed");
        }
    }
}
