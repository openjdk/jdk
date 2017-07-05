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
 * @test TestGCLogRotationViaJcmd.java
 * @bug 7090324
 * @summary test for gc log rotation via jcmd
 * @library /testlibrary
 * @run main/othervm -Xloggc:test.log -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=3 TestGCLogRotationViaJcmd
 *
 */
import com.oracle.java.testlibrary.*;
import java.io.File;
import java.io.FilenameFilter;

public class TestGCLogRotationViaJcmd {

    static final File currentDirectory = new File(".");
    static final String LOG_FILE_NAME = "test.log";
    static final int NUM_LOGS = 3;

    static FilenameFilter logFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith(LOG_FILE_NAME);
        }
    };

    public static void main(String[] args) throws Exception {
        // Grab the pid from the current java process
        String pid = Integer.toString(ProcessTools.getProcessId());

        // Create a JDKToolLauncher
        JDKToolLauncher jcmd = JDKToolLauncher.create("jcmd")
                                              .addToolArg(pid)
                                              .addToolArg("GC.rotate_log");

        for (int times = 1; times < NUM_LOGS; times++) {
            // Run jcmd <pid> GC.rotate_log
            ProcessBuilder pb = new ProcessBuilder(jcmd.getCommand());

            // Make sure we didn't crash
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
        }

        // GC log check
        File[] logs = currentDirectory.listFiles(logFilter);
        if (logs.length != NUM_LOGS) {
            throw new Error("There are only " + logs.length
                                              + " logs instead " + NUM_LOGS);
        }

    }

}

