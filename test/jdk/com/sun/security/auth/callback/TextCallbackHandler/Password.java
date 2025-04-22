/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6825240 6829785
 * @summary Password.readPassword() echos the input when System.Console is null
 * @library /test/lib
 * @run main/manual/othervm Password
 */

import com.sun.security.auth.callback.TextCallbackHandler;


import javax.security.auth.callback.*;
import javax.swing.*;

import jdk.test.lib.UIBuilder;

import java.util.Arrays;

public class Password {

    private static final int TIMEOUT_MS = 240000;
    private volatile boolean failed = false;
    private volatile boolean aborted = false;
    private Thread currentThread = null;

    public static void password() throws Exception {

        TextCallbackHandler h = new TextCallbackHandler();
        PasswordCallback nc =
                new PasswordCallback("Please input something, your input should be VISIBLE: ", true);
        PasswordCallback nc2 =
                new PasswordCallback("Please input something again, your input should be INVISIBLE: ", false);
        Callback[] callbacks = {nc, nc2};
        h.handle(callbacks);
        System.out.println("You input " + new String(nc.getPassword()) +
                " and " + new String(nc2.getPassword()));
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--password")) {
            password();
        } else {
            final String instructions = String.format("%s/bin/java -cp \"%s\" Password --password",
                    System.getProperty("java.home").replace("\\","/"),
                    System.getProperty("java.class.path").replace("\\","/")
            );

            boolean testFailed = new Password().validate(
                    "Please copy and execute the following script in the terminal / Windows Command Prompt window. " +
                            "Two passwords will be prompted for.\n" +
                            "Enter something at each prompt and press Enter/Return.\n" +
                            "If the first input is visible and the second is invisible, this test PASSES. Otherwise, this test FAILS.\n" +
                            "Once the test is complete please select whether the test has passed.\n",
                    instructions);

            if (testFailed) {
                throw new RuntimeException("Test has failed");
            }
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
