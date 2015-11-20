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
 * @test Testlibadimalloc.java
 * @bug 8141445
 * @summary make sure the Solaris Sparc M7 libadimalloc.so library generates SIGSEGV's on buffer overflow
 * @requires (os.family == "solaris" & os.arch == "sparcv9")
 * @library /testlibrary
 * @build jdk.test.lib.*
 * @compile SEGVOverflow.java
 * @run driver Testlibadimalloc
 */

import java.io.*;
import java.nio.file.*;
import java.util.*;
import jdk.test.lib.ProcessTools;

public class Testlibadimalloc {

    // Expected return value when java program cores
    static final int EXPECTED_RET_VAL = 6;

    public static void main(String[] args) throws Throwable {

        // See if the libadimalloc.so library exists
        Path path = Paths.get("/usr/lib/64/libadimalloc.so");

        // If the libadimalloc.so file does not exist, pass the test
        if (!(Files.isRegularFile(path) || Files.isSymbolicLink(path))) {
            System.out.println("Test skipped; libadimalloc.so does not exist");
            return;
        }

        // Get the JDK, library and class path properties
        String libpath = System.getProperty("java.library.path");

        // Create a new java process for the SEGVOverflow Java/JNI test
        ProcessBuilder builder = ProcessTools.createJavaProcessBuilder(
            "-Djava.library.path=" + libpath + ":.", "SEGVOverflow");

        // Add the LD_PRELOAD_64 value to the environment
        Map<String, String> env = builder.environment();
        env.put("LD_PRELOAD_64", "libadimalloc.so");

        // Start the process, get the pid and then wait for the test to finish
        Process process = builder.start();
        long pid = process.getPid();
        int retval = process.waitFor();

        // make sure the SEGVOverflow test crashed
        boolean found = false;
        if (retval == EXPECTED_RET_VAL) {
            String filename = "hs_err_pid" + pid + ".log";
            Path filepath = Paths.get(filename);
            // check to see if hs_err_file exists
            if (Files.isRegularFile(filepath)) {
                // see if the crash was due to a SEGV_ACCPERR signal
                File hs_err_file = new File(filename);
                Scanner scanner = new Scanner(hs_err_file);
                while (!found && scanner.hasNextLine()) {
                    String nextline = scanner.nextLine();
                    if (nextline.contains("SEGV_ACCPERR")) {
                         found = true;
                    }
                }
            } else {
                System.out.println("Test failed; hs_err_file does not exist: "
                                   + filepath);
            }
        } else {
            System.out.println("Test failed; java test program did not " +
                               "return expected error: expected = " +
                               EXPECTED_RET_VAL + ", retval = " + retval);
        }
        // If SEGV_ACCPERR was not found in the hs_err file fail the test
        if (!found) {
            System.out.println("FAIL: SEGV_ACCPERR not found");
            throw new RuntimeException("FAIL: SEGV_ACCPERR not found");
        }
    }
}
