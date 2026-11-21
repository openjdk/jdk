/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/*
 * @test
 * @bug 8393035
 * @summary test that MT initialization of font metrics does not crash
 * @run main/othervm MultiThreadFontMetrics run
 */
public final class MultiThreadFontMetrics extends Thread {

    static BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    public static void main(String[] args) throws Exception {
       if (args.length == 0) {
           runMetricsTest();
           return;
       }
       for (int i = 0; i < 20; i++) {
           System.out.println(i);
           runChild();
       }
    }

    static void runChild() {
        String cpDir = System.getProperty("java.class.path");
        String jHome = System.getProperty("java.home");
        String jCmd = jHome + File.separator + "bin" + File.separator + "java";
        int exitValue = doExec(jCmd, "-Xcheck:jni", "-cp", cpDir, "MultiThreadFontMetrics");
        if (exitValue != 0) {
            throw new RuntimeException("Test Failed");
        }
    }

    static int doExec(String... cmds) {
        Process p = null;
        ProcessBuilder pb = new ProcessBuilder(cmds);
        BufferedReader rdr = null;
        try {
            pb.redirectErrorStream(true);
            p = pb.start();
            rdr = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String in;
            while ((in = rdr.readLine()) != null) {
                System.out.println(in);
            }
            p.waitFor();
            p.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return p.exitValue();
    }

    static void runMetricsTest() throws Exception {

        System.setProperty("java.awt.headless", "true");

        int numThreads = 16;

        MultiThreadFontMetrics[] threads = new MultiThreadFontMetrics[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadNumber = i;
            threads[i] = new MultiThreadFontMetrics();

        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    public void run() {
        Graphics2D graphics = image.createGraphics();
        graphics.getFontMetrics();
        graphics.dispose();
    }
}
