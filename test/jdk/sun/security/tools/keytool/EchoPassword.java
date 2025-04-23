/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8354469
 * @summary keytool password prompt shows warning when cannot suppress echo
 * @library /test/lib
 * @run main/manual/othervm EchoPassword
 */
import javax.swing.*;

import jdk.test.lib.UIBuilder;

import java.io.File;

public class EchoPassword {

    private static final int TIMEOUT_MS = 240000;
    private volatile boolean failed = false;
    private volatile boolean aborted = false;
    private Thread currentThread = null;

    public static void main(String[] args) throws Exception {
        final String keytool = String.format("\"%s/bin/keytool\" -keystore 8354469.ks",
                System.getProperty("java.home").replace("\\", File.separator));
        boolean testFailed = new EchoPassword().validate(
                "Please copy and run the following commands in a Terminal or Windows Command Prompt window:",
                String.format("""

                1. %s -genkeypair

                   When prompted, enter "password" and press Enter. Verify that the input is hidden,
                   and no warning about password echoing appears. At the Re-enter password prompt,
                   press Ctrl-C to exit.

                2. %s -genkeypair | type

                   When prompted, enter "password" and press Enter. Verify that the input is echoed,
                   and a warning about password echoing is shown. At the Re-enter password prompt,
                   press Ctrl-C to exit.

                Press "pass" if the behavior matches expectations; otherwise, press "fail".
                """, keytool, keytool));
        if (testFailed) {
            throw new RuntimeException("Test has failed");
        }
    }

    public boolean validate(String instruction, String message) {
        failed = false;
        currentThread = Thread.currentThread();
        final JDialog dialog = new UIBuilder.DialogBuilder()
                .setTitle("Password")
                .setInstruction(instruction)
                .setMessage(message)
                .setPassAction(e -> pass())
                .setFailAction(e -> fail())
                .setCloseAction(this::abort)
                .build();

        SwingUtilities.invokeLater(() -> {
            try {
                dialog.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            Thread.sleep(TIMEOUT_MS);
            //Timed out, so fail the test
            throw new RuntimeException(
                    "Timed out after " + TIMEOUT_MS / 1000 + " seconds");
        } catch (final InterruptedException e) {
            if (aborted) {
                throw new RuntimeException("TEST ABORTED");
            }

            if (failed) {
                System.out.println("TEST FAILED");
                System.out.println(message);
            } else {
                System.out.println("TEST PASSED");
            }
        } finally {
            dialog.dispose();
        }

        return failed;
    }

    public void pass() {
        failed = false;
        currentThread.interrupt();
    }

    public void fail() {
        failed = true;
        currentThread.interrupt();
    }

    public void abort() {
        aborted = true;
        currentThread.interrupt();
    }
}
