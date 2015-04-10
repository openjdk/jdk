/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test to see if jimage tool extracts and recreates correctly.
 * @run main/timeout=360 JImageTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Basic test for jimage tool.
 */
public class JImageTest {
    private static Runtime runtime = Runtime.getRuntime();
    private static int errors = 0;

    static class Tool {
        private int exit = 0;
        private String out = "";
        private String err = "";

        private void feedInputStream(String in, OutputStream out) {
            try (OutputStreamWriter outputStream = new OutputStreamWriter(out)) {
                if (in != null) {
                    outputStream.write(in, 0, in.length());
                }
            } catch (final IOException ex) {
                // Process was not expecting input.  May be normal state of affairs.
            }
        }

        private Thread getOutputStreamThread(InputStream in, StringBuilder out) {
            return new Thread(() -> {
                final char buffer[] = new char[1024];

                try (final InputStreamReader inputStream = new InputStreamReader(in)) {
                    for (int length; (length = inputStream.read(buffer, 0, buffer.length)) != -1; ) {
                        out.append(buffer, 0, length);
                    }
                } catch (final IOException ex) {
                    out.append(ex.toString());
                }
            });
        }

        Tool(String[] args) {
            this(null, args);
        }

        Tool(String in, String[] args) {
            ProcessBuilder processBuilder = new ProcessBuilder(args);
            exec(processBuilder, in);
        }

        private void exec(ProcessBuilder processBuilder, String in) {
            StringBuilder outBuilder = new StringBuilder();
            StringBuilder errBuilder = new StringBuilder();

            try {
                Process process = processBuilder.start();

                Thread outThread = getOutputStreamThread(process.getInputStream(), outBuilder);
                Thread errThread = getOutputStreamThread(process.getErrorStream(), errBuilder);

                outThread.start();
                errThread.start();

                feedInputStream(in, process.getOutputStream());

                exit = process.waitFor();
                outThread.join();
                errThread.join();
            } catch (IOException | InterruptedException ex) {
                ex.printStackTrace();
                exit = -1;
            }

            out = outBuilder.toString();
            err = errBuilder.toString();
        }

        int getExit() {
            return exit;
        }

        String getOut() {
            return out;
        }

        String getErr() {
            return err;
        }
    }

    private static void exec(String... args) {
        Tool tool = new Tool(args);
        int exit = tool.getExit();

        if (exit != 0) {
            errors++;
            System.err.println("----------Tool.out----------");
            System.err.append(tool.getOut());
            System.err.println("----------Tool.err----------");
            System.err.append(tool.getErr());
            System.err.println("----------Tool.exit----------");
            System.err.println("Error code = " + exit);
            throw new RuntimeException("JImageTest FAIL");
        }
    }

    public static void main(String[] args) {
        final String JAVA_HOME = System.getProperty("java.home");
        Path jimagePath = Paths.get(JAVA_HOME, "bin", "jimage");
        Path bootimagePath = Paths.get(JAVA_HOME, "lib", "modules", "bootmodules.jimage");

        if (Files.exists(jimagePath) && Files.exists(bootimagePath)) {
            String jimage = jimagePath.toAbsolutePath().toString();
            String bootimage = bootimagePath.toAbsolutePath().toString();
            String extractDir = Paths.get(".", "extract").toAbsolutePath().toString();
            String recreateImage = Paths.get(".", "recreate.jimage").toAbsolutePath().toString();

            exec(new String[] {jimage, "extract", "--dir", extractDir, bootimage});
            exec(new String[] {jimage, "recreate", "--dir", extractDir, recreateImage});

            System.out.println("Test successful");
         } else {
            System.out.println("Test skipped, no module image");
         }
    }
}
