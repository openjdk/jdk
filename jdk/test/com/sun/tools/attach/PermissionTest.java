/*
 * Copyright (c) 2005, 2013 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;
import java.util.Properties;
import java.io.File;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.ProcessThread;

/*
 * @test
 * @bug 6173612 6273707 6277253 6335921 6348630 6342019 6381757
 * @summary Basic unit tests for the VM attach mechanism.
 * @library /lib/testlibrary
 * @run build Application Shutdown
 * @run main PermissionTest
 *
 * Unit test for Attach API -
 * this checks that a SecurityException is thrown as expected.
 */
public class PermissionTest {

    /*
     * The actual test is in the nested class TestMain.
     * The responsibility of this class is to:
     * 1. Start the Application class in a separate process.
     * 2. Find the pid and shutdown port of the running Application.
     * 3. Run the tests in TstMain that will attach to the Application.
     * 4. Shut down the Application.
     */
    public static void main(String args[]) throws Throwable {
        final String pidFile ="TestPermission.Application.pid";
        ProcessThread processThread = null;
        RunnerUtil.ProcessInfo info = null;
        try {
            processThread = RunnerUtil.startApplication(pidFile);
            info = RunnerUtil.readProcessInfo(pidFile);
            runTests(info.pid);
        } catch (Throwable t) {
            System.out.println("TestPermission got unexpected exception: " + t);
            t.printStackTrace();
            throw t;
        } finally {
            // Make sure the Application process is stopped.
            RunnerUtil.stopApplication(info.shutdownPort, processThread);
        }
    }

    /**
     * Runs the actual test the nested class TestMain.
     * The test is run in a separate process because we need to add to the classpath.
     */
    private static void runTests(int pid) throws Throwable {
        final String sep = File.separator;

        // Need to add jdk/lib/tools.jar to classpath.
        String classpath =
            System.getProperty("test.class.path", "") + File.pathSeparator +
            System.getProperty("test.jdk", ".") + sep + "lib" + sep + "tools.jar";
        String testSrc = System.getProperty("test.src", "") + sep;

        // Use a policy that will NOT allow attach. Test will verify exception.
        String[] args = {
            "-classpath",
            classpath,
            "-Djava.security.manager",
            String.format("-Djava.security.policy=%sjava.policy.deny", testSrc),
            "PermissionTest$TestMain",
            Integer.toString(pid),
            "true" };
        OutputAnalyzer output = ProcessTools.executeTestJvm(args);
        output.shouldHaveExitValue(0);

        // Use a policy that will allow attach.
        args = new String[] {
            "-classpath",
            classpath,
            "-Djava.security.manager",
            String.format("-Djava.security.policy=%sjava.policy.allow", testSrc),
            "PermissionTest$TestMain",
            Integer.toString(pid),
            "false" };
        output = ProcessTools.executeTestJvm(args);
        output.shouldHaveExitValue(0);
    }

    /**
     * This is the actual test code. It will attach to the Application and verify
     * that we get a SecurityException when that is expected.
     */
    public static class TestMain {
        public static void main(String args[]) throws Exception {
            SecurityManager sm = System.getSecurityManager();
            if (sm == null) {
                throw new RuntimeException("Test configuration error - no security manager set");
            }

            String pid = args[0];
            boolean shouldFail = Boolean.parseBoolean(args[1]);

            try {
                VirtualMachine.attach(pid).detach();
                if (shouldFail) {
                    throw new RuntimeException("SecurityException should be thrown");
                }
                System.out.println(" - attached to target VM as expected.");
            } catch (Exception x) {
                // AttachNotSupportedException thrown when no providers can be loaded
                if (shouldFail && ((x instanceof AttachNotSupportedException) ||
                    (x instanceof SecurityException))) {
                    System.out.println(" - exception thrown as expected.");
                } else {
                    throw x;
                }
            }
        }
    }
}
