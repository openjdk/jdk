/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4957669 5017871 8358729
 * @summary cannot load class names containing some JSR 202 characters;
 *          plugin does not escape unicode character in http request
 * @modules java.base/sun.net.www
 *          jdk.httpserver
 * @run main ClassnameCharTest
 */

import java.io.*;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.net.*;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.jar.*;
import com.sun.net.httpserver.*;
import sun.net.www.ParseUtil;

public class ClassnameCharTest {
    private static final Path JAR_PATH = Path.of("testclasses.jar");
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
                    try (FileInputStream fis = new FileInputStream(JAR_PATH.toFile());
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
            MyURLClassLoader acl = new MyURLClassLoader(base);
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

    static class MyURLClassLoader extends URLClassLoader {
        private URL base;   /* code base URL */
        private CodeSource codesource; /* codesource for the base URL */
        MyURLClassLoader(URL base) {
            super(new URL[0]);
            this.base = base;
            this.codesource =
                    new CodeSource(base, (java.security.cert.Certificate[]) null);
        }

        @Override
        public Class<?> findClass(String name) {
            int index = name.indexOf(';');
            String cookie = "";
            if(index != -1) {
                cookie = name.substring(index, name.length());
                name = name.substring(0, index);
            }

            // check loaded JAR files
            try {
                return super.findClass(name);
            } catch (ClassNotFoundException _) {}

            // Otherwise, try loading the class from the code base URL
            //      final String path = name.replace('.', '/').concat(".class").concat(cookie);
            String encodedName = ParseUtil.encodePath(name.replace('.', '/'), false);
            final String path = (new StringBuffer(encodedName)).append(".class").append(cookie).toString();
            Exception exc = null;
            // try block used for checked exceptions as well as ClassFormatError
            // from defineClass call
            try {
                URL finalURL = new URL(base, path);
                // Make sure the codebase won't be modified
                if (base.getProtocol().equals(finalURL.getProtocol()) &&
                        base.getHost().equals(finalURL.getHost()) &&
                        base.getPort() == finalURL.getPort()) {
                    byte[] b = getBytes(finalURL);
                    return defineClass(name, b, 0, b.length, codesource);
                }
                // protocol/host/port mismatch, fail with RuntimeExc
            } catch (Exception underlyingE) {
                exc = underlyingE; // Most likely CFE from defineClass
            }
            // Fail if there was either a protocol/host/port mismatch
            // or an exception was thrown (which is propagated)
            throw new RuntimeException(name, exc);
        }

        /*
         * Returns the contents of the specified URL as an array of bytes.
         */
        private static byte[] getBytes(URL url) throws IOException {
            URLConnection uc = url.openConnection();
            if (uc instanceof java.net.HttpURLConnection) {
                java.net.HttpURLConnection huc = (java.net.HttpURLConnection) uc;
                int code = huc.getResponseCode();
                if (code >= java.net.HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new IOException("open HTTP connection failed.");
                }
            }
            int len = uc.getContentLength();

            InputStream in = new BufferedInputStream(uc.getInputStream());

            byte[] b;
            try {
                b = in.readAllBytes();
                if (len != -1 && b.length != len)
                    throw new EOFException("Expected:" + len + ", read:" + b.length);
            } finally {
                in.close();
            }
            return b;
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

    // Create the class file and write it to the testable jar
    static void buildJar() throws IOException {
        var bytes = ClassFile.of().build(ClassDesc.of("fo o"), _ -> {});
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(JAR_PATH.toFile()))) {
            jos.putNextEntry(new JarEntry("fo o.class"));
            jos.write(bytes, 0, bytes.length);
            jos.closeEntry();
        }
    }

    public static void main(String[] args) throws Throwable {
        try {
            buildJar();
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
