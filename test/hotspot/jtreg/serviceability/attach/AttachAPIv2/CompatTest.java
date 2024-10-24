/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic compatibility test for Attach API v2
 * @bug 8219896
 * @library /test/lib
 * @modules jdk.attach/sun.tools.attach
 *
 * @run main/othervm -Xlog:attach=trace CompatTest
 * @run main/othervm -Xlog:attach=trace -Djdk.attach.compat=true CompatTest
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import jdk.test.lib.apps.LingeredApp;

public class CompatTest {

    public static void main(String[] args) throws Exception {
        // if the test (client part) in the "compat" mode
        boolean clientCompat = "true".equals(System.getProperty("jdk.attach.compat"));
        System.out.println("Client is in compat mode: " + clientCompat);
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp("-Xlog:attach=trace");
            test(app, clientCompat);
        } finally {
            LingeredApp.stopApp(app);
        }

        try {
            app = LingeredApp.startApp("-Xlog:attach=trace", "-Djdk.attach.compat=true");
            // target VM in "compat" mode, always expect failure
            test(app, true);
        } finally {
            LingeredApp.stopApp(app);
        }

    }

    // The test uses HotSpotVirtualMachine.setFlag method with long flag value.
    // For attach API v1 an exception is expected to be thrown (argument cannot be longer than 1024 characters).
    private static String flagName = "HeapDumpPath";
    // long for v1
    private static String flagValue = "X" + "A".repeat(1024) + "X";

    private static void test(LingeredApp app, boolean expectFailure) throws Exception {
        System.out.println("======== Start ========");

        HotSpotVirtualMachine vm = (HotSpotVirtualMachine)VirtualMachine.attach(String.valueOf(app.getPid()));

        BufferedReader replyReader = null;
        try {
            replyReader = new BufferedReader(new InputStreamReader(
                vm.setFlag(flagName, flagValue)));

            if (expectFailure) {
                throw new RuntimeException("No expected exception is thrown");
            }

            String line;
            while ((line = replyReader.readLine()) != null) {
                System.out.println("setFlag reply: " + line);
            }
            replyReader.close();

        } catch (IOException ex) {
            System.out.println("OK: setFlag thrown expected exception:");
            ex.printStackTrace(System.out);
        } finally {
            vm.detach();
        }

        System.out.println("======== End ========");
        System.out.println();
    }
}
