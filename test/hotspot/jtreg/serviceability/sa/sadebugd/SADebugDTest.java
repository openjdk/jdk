/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Checks that the jshdb debugd utility sucessfully starts
 *          and tries to attach to a running process
 * @requires vm.hasSAandCanAttach
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 *
 * @run main/othervm SADebugDTest
 */

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Reader;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.process.ProcessTools.startProcess;

public class SADebugDTest {

    private static final String GOLDEN = "Attaching to process ID %d and starting RMI services, please wait...";

    private static final String JAVA_HOME = (System.getProperty("test.jdk") != null)
            ? System.getProperty("test.jdk") : System.getProperty("java.home");

    private static final String JAVA_BIN_DIR
            = JAVA_HOME + File.separator + "bin" + File.separator;

    private static final String JHSDB = JAVA_BIN_DIR + "jhsdb";

    public static void main(String[] args) throws Exception {

        long ourPid = ProcessHandle.current().pid();

        // The string we are expecting in the debugd ouput
        String golden = String.format(GOLDEN, ourPid);

        // We are going to run 'jhsdb debugd <our pid>'
        // The startProcess will block untl the 'golden' string appears in either process' stdout or stderr
        // In case of timeout startProcess kills the debugd process
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(JHSDB, "debugd", String.valueOf(ourPid));
        Process debugd = startProcess("debugd", pb, null, (line) -> line.trim().contains(golden), 0, TimeUnit.SECONDS);

        // If we are here, this means we have received the golden line and the test has passed
        // The debugd remains running, we have to kill it
        debugd.destroy();

    }

    private static void log(String string) {
        System.out.println(string);
    }

}
