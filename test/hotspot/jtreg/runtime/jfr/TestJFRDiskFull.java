/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8280684
 * @summary JfrRecorderService failes with guarantee(num_written > 0) when no space left on device.
 * @library /test/lib 
 * @run main/manual TestJFRDiskFull
 */

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;
import jdk.jfr.Configuration;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestJFRDiskFull {

    @Name("test.JFRDiskFull")
    @Label("JFRDiskFull")
    @Description("JFRDiskFull Event")
    static class JFRDiskFullEvent extends Event {
        @Label("Message")
        String message;
    }
    
    private static final long LEFT_SIZE = 307200L;

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            Configuration profConfig = Configuration.getConfiguration("profile");
            Recording recording = new Recording(profConfig);
            recording.setName("TestJFR");
            recording.start();
            for (int i = 0; i < 1000000000; i++) {
                try {
                    JFRDiskFullEvent event = new JFRDiskFullEvent();
                    event.message = "JFRDiskFull";
                    event.begin();
                    event.commit();
                } catch (Exception ex) {
                }
            }
            recording.stop();
            System.out.println("should not reach here");  
        } else {
            runtest();
        }
    }

    private static void runtest() throws Exception {
        // disk full
        File file = File.createTempFile("largefile", null, new File("."));
        file.deleteOnExit();
        long spaceavailable = file.getUsableSpace();
        System.out.println("spaceavailable = " + spaceavailable);
        long filesize = spaceavailable - LEFT_SIZE;
        if (filesize > 0) {
            createLargeFile(filesize, file);
        }
        System.out.println("spaceavailable = " + file.getUsableSpace());

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:FlightRecorderOptions=maxchunksize=1M",
                "TestJFRDiskFull", "Recording"); 
        OutputAnalyzer oa = ProcessTools.executeProcess(pb);
        long pid = oa.pid();
        oa.shouldMatch(
                "\\[[0-9]+\\.[0-9]+s\\]\\[error\\]\\[jfr,system\\] Failed to write to jfr stream because no space left on device")
                .shouldMatch(
                        "\\[[0-9]+\\.[0-9]+s\\]\\[error\\]\\[jfr,system\\] An irrecoverable error in Jfr. Shutting down VM\\.\\.\\.")
                .shouldNotContain("Internal Error")
                .shouldNotContain("should not reach here");

        File errLog = new File("hs_err_pid" + pid + "\\.log");
        if (errLog.exists()) {
            throw new RuntimeException("hs_err log is created");
        }
    }

    private static void createLargeFile(long filesize, File file) throws Exception {
        file.delete();
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        byte[] x = new byte[1024];
        System.out.println("  Writing large file...");
        long t0 = System.nanoTime();
        while (file.length() < filesize) {
            raf.write(x);
        }
        long t1 = System.nanoTime();
        System.out.printf("  Wrote large file in %d ns (%d ms) %n", t1 - t0, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        raf.close();
    }
}
