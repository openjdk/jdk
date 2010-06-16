/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6575373
 * @summary verify default segment limit
 * @compile SegmentLimit.java
 * @run main SegmentLimit
 */

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

/*
 * Run this against a large jar file, by default the packer should generate only
 * one segment, parse the output of the packer to verify if this is indeed true.
 */

public class SegmentLimit {

    private static final File  javaHome = new File(System.getProperty("java.home"));

    public static void main(String... args) {
        if (!javaHome.getName().endsWith("jre")) {
            throw new RuntimeException("Error: requires an SDK to run");
        }

        File out = new File("test" + Pack200Test.PACKEXT);
        out.delete();
        runPack200(out);
    }

    static void close(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignore) {}
    }

    static void runPack200(File outFile) {
        File binDir = new File(javaHome, "bin");
        File pack200Exe = System.getProperty("os.name").startsWith("Windows")
                ? new File(binDir, "pack200.exe")
                : new File(binDir, "pack200");
        File sdkHome = javaHome.getParentFile();
        File testJar = new File(new File(sdkHome, "lib"), "tools.jar");

        System.out.println("using pack200: " + pack200Exe.getAbsolutePath());

        String[] cmds = { pack200Exe.getAbsolutePath(),
                          "--effort=1",
                          "--verbose",
                          "--no-gzip",
                          outFile.getName(),
                          testJar.getAbsolutePath()
        };
        InputStream is = null;
        BufferedReader br = null;
        InputStreamReader ir = null;

        FileOutputStream fos = null;
        PrintStream ps = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(cmds);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            is = p.getInputStream();
            ir = new InputStreamReader(is);
            br = new BufferedReader(ir);

            File logFile = new File("pack200.log");
            fos = new FileOutputStream(logFile);
            ps  = new PrintStream(fos);

            String line = br.readLine();
            int count = 0;
            while (line != null) {
                line = line.trim();
                if (line.matches(".*Transmitted.*files of.*input bytes in a segment of.*bytes")) {
                    count++;
                }
                ps.println(line);
                line=br.readLine();
            }
            p.waitFor();
            if (p.exitValue() != 0) {
                throw new RuntimeException("pack200 failed");
            }
            p.destroy();
            if (count > 1) {
                throw new Error("test fails: check for multiple segments(" +
                        count + ") in: " + logFile.getAbsolutePath());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        } catch (InterruptedException ignore){
        } finally {
            close(is);
            close(ps);
            close(fos);
        }
    }
}

