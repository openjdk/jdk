/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8016271 8026405
 * @summary wsimport -clientjar does not create portable jar on windows due to hardcoded '\'
 * @run main/othervm TestWsImport
 */

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.FileVisitResult.*;
import java.util.Enumeration;
import java.util.jar.JarFile;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

public class TestWsImport {

    public static void main(String[] args) throws IOException {

        String javaHome = System.getProperty("java.home");
        if (javaHome.endsWith("jre")) {
            javaHome = new File(javaHome).getParent();
        }
        String wsimport = javaHome + File.separator + "bin" + File.separator + "wsimport";
        if (System.getProperty("os.name").startsWith("Windows")) {
            wsimport = wsimport.concat(".exe");
        }

        Endpoint endpoint = Endpoint.create(new TestService());
        HttpServer httpServer = null;
        try {
            // Manually create HttpServer here using ephemeral address for port
            // so as to not end up with attempt to bind to an in-use port
            httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            HttpContext httpContext = httpServer.createContext("/hello");
            int port = httpServer.getAddress().getPort();
            System.out.println("port = " + port);
            httpServer.start();
            endpoint.publish(httpContext);
            String address = "http://localhost:" + port + "/hello";

            Service service = Service.create(new URL(address + "?wsdl"),
                new QName("http://test/jaxws/sample/", "TestService"));

            String[] wsargs = {
                wsimport,
                "-p",
                "wstest",
                "-J-Djavax.xml.accessExternalSchema=all",
                "-J-Dcom.sun.tools.internal.ws.Invoker.noSystemProxies=true",
                address + "?wsdl",
                "-clientjar",
                "wsjar.jar"
            };
            ProcessBuilder pb = new ProcessBuilder(wsargs);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String s = r.readLine();
            while (s != null) {
                System.out.println(s.trim());
                s = r.readLine();
            }
            p.waitFor();
            p.destroy();

            try (JarFile jarFile = new JarFile("wsjar.jar")) {
                for (Enumeration em = jarFile.entries(); em.hasMoreElements();) {
                    String fileName = em.nextElement().toString();
                    if (fileName.contains("\\")) {
                        throw new RuntimeException("\"\\\" character detected in jar file: " + fileName);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } finally {
            endpoint.stop();
            if (httpServer != null) {
                httpServer.stop(0);
            }
            Path p = Paths.get("wsjar.jar");
            Files.deleteIfExists(p);
            p = Paths.get("wstest");
            if (Files.exists(p)) {
                try {
                    Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file,
                            BasicFileAttributes attrs) throws IOException {

                            Files.delete(file);
                            return CONTINUE;
                        }
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir,
                            IOException exc) throws IOException {

                            if (exc == null) {
                                Files.delete(dir);
                                return CONTINUE;
                            } else {
                                throw exc;
                            }
                        }
                    });
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
    }
}
