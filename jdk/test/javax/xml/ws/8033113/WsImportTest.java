/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8033113
 * @summary wsimport fails on WSDL:header parameter name customization
 * @run main/othervm WsImportTest
 */

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.*;

public class WsImportTest {

    public static void main(String[] args) throws IOException {

        String wsimport = getWsImport();
        String customization = getWSDLFilePath("customization.xml");
        String wsdl = getWSDLFilePath("Organization_List.wsdl");

        try {
            log("Importing wsdl: " + wsdl);
            String[] wsargs = {
                    wsimport,
                    "-keep",
                    "-verbose",
                    "-extension",
                    "-XadditionalHeaders",
                    "-Xdebug",
                    "-b",
                    customization,
                    wsdl
            };

            ProcessBuilder pb = new ProcessBuilder(wsargs);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            logOutput(p);
            int result = p.waitFor();
            p.destroy();

            if (result != 0) {
                fail("WsImport failed. TEST FAILED.");
            } else {
                log("Test PASSED.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            deleteGeneratedFiles();
        }
    }

    private static void fail(String message) {
        throw new RuntimeException(message);
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static void logOutput(Process p) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String s = r.readLine();
        while (s != null) {
            log(s.trim());
            s = r.readLine();
        }
    }

    private static void deleteGeneratedFiles() {
        Path p = Paths.get("generated");
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

    private static String getWSDLFilePath(String filename) {
        String testSrc = System.getProperty("test.src");
        if (testSrc == null) testSrc = ".";
        return Paths.get(testSrc).resolve(filename).toString();
    }

    private static String getWsImport() {
        String javaHome = System.getProperty("java.home");
        if (javaHome.endsWith("jre")) {
            javaHome = new File(javaHome).getParent();
        }
        String wsimport = javaHome + File.separator + "bin" + File.separator + "wsimport";
        if (System.getProperty("os.name").startsWith("Windows")) {
            wsimport = wsimport.concat(".exe");
        }
        return wsimport;
    }
}
