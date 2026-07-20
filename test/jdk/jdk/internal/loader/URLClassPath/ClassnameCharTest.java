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
 * @run junit ClassnameCharTest
 */

import java.io.*;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.net.*;
import java.security.CodeSource;
import com.sun.net.httpserver.*;
import sun.net.www.ParseUtil;

import org.junit.jupiter.api.Test;

public class ClassnameCharTest {

    private static HttpServer server;
    private static final byte[] bytes =
            ClassFile.of().build(ClassDesc.of("fo o"), _ -> {});

    @Test
    void testClassName() throws IOException {
        // Build the server and set the context
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String filename = exchange.getRequestURI().getPath();
            System.out.println("getRequestURI = " + exchange.getRequestURI());
            System.out.println("filename = " + filename);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(bytes, 0, bytes.length);
            exchange.sendResponseHeaders(200, baos.size());
            try (OutputStream os = exchange.getResponseBody()) {
                baos.writeTo(os);
            }
        });
        server.start();
        try {
            URL base = new URL("http://localhost:" + server.getAddress().getPort());
            System.out.println ("Server: listening on " + base);
            MyURLClassLoader acl = new MyURLClassLoader(base);
            Class<?> class1 = acl.findClass("fo o");
            System.out.println("class1 = " + class1);
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

            // Otherwise, try loading the class from the code base URL
            //      final String path = name.replace('.', '/').concat(".class").concat(cookie);
            String encodedName = ParseUtil.encodePath(name.replace('.', '/'), false);
            final String path = encodedName + ".class" + cookie;
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
                // protocol/host/port mismatch, fail with RuntimeException
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
}
