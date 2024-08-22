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
 * @summary Tests that long arguments of attach operation are not truncated
 * @bug 8334168
 * @library /test/lib
 * @modules jdk.attach/sun.tools.attach
 * @run main LongArgTest
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;

import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import jdk.test.lib.apps.LingeredApp;

public class LongArgTest {

    // current restriction: max arg size is 1024
    private static int MAX_ARG_SIZE = 1024;

    public static void main(String[] args) throws Exception {
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp();

            // sanity
            test(app)
                .mustSucceed()
                .run();

            test(app)
                .valueLength(MAX_ARG_SIZE)
                .mustSucceed()
                .run();

            test(app)
                .valueLength(MAX_ARG_SIZE + 1)
                .run();

            // more than max args (3) with MAX_ARG_SIZE
            test(app)
                .valueLength(3 * MAX_ARG_SIZE + 1)
                .run();

        } finally {
            LingeredApp.stopApp(app);
        }
    }

    private static Test test(LingeredApp app) {
        return new Test(app);
    }

    // For simplicity, the test uses internal HotSpotVirtualMachine,
    // sets/gets "HeapDumpPath" flag value (string flag, not validated by JVM).
    private static class Test {
        private LingeredApp app;
        private String flagName = "HeapDumpPath";
        private String flagValue = generateValue(5);
        private boolean setFlagMustSucceed = false;

        Test(LingeredApp app) {
            this.app = app;
        }

        Test valueLength(int len) {
            flagValue = generateValue(len);
            return this;
        }

        Test mustSucceed() {
            setFlagMustSucceed = true;
            return this;
        }

        void run() throws Exception {
            System.out.println("======== Start ========");
            System.out.println("Arg size = " + flagValue.length());

            HotSpotVirtualMachine vm = (HotSpotVirtualMachine)VirtualMachine.attach(String.valueOf(app.getPid()));

            if (setFlag(vm)) {
                String actualValue = getFlag(vm);

                if (!flagValue.equals(actualValue)) {
                    String msg = "Actual value is different: ";
                    if (actualValue == null) {
                        msg += "null";
                    } else if (flagValue.startsWith(actualValue)) {
                        msg += "truncated from " + flagValue.length() + " to " + actualValue.length();
                    } else {
                        msg += actualValue + ", expected value: " + flagValue;
                    }
                    System.out.println(msg);
                    vm.detach();
                    throw new RuntimeException(msg);
                } else {
                    System.out.println("Actual value matches: " + actualValue);
                }
            }

            vm.detach();

            System.out.println("======== End ========");
            System.out.println();
        }

        // Sets the flag value, return true on success.
        private boolean setFlag(HotSpotVirtualMachine vm) throws Exception {
            BufferedReader replyReader = null;
            try {
                replyReader = new BufferedReader(new InputStreamReader(
                    vm.setFlag(flagName, flagValue)));
            } catch (IOException ex) {
                if (setFlagMustSucceed) {
                    throw ex;
                }
                System.out.println("OK: setFlag() thrown exception:");
                ex.printStackTrace(System.out);
                return false;
            }

            String line;
            while ((line = replyReader.readLine()) != null) {
                System.out.println("setFlag: " + line);
            }
            replyReader.close();
            return true;
        }

        private String getFlag(HotSpotVirtualMachine vm) throws Exception {
            // Then read and make sure we get back the same value.
            BufferedReader replyReader = new BufferedReader(new InputStreamReader(vm.printFlag(flagName)));

            String prefix = "-XX:" + flagName + "=";
            String value = null;
            String line;
            while((line = replyReader.readLine()) != null) {
                System.out.println("getFlag: " + line);
                if (line.startsWith(prefix)) {
                    value = line.substring(prefix.length());
                }
            }
            return value;
        }

        private String generateValue(int len) {
            return "X" + "A".repeat(len - 2) + "X";
        }
    }

}
