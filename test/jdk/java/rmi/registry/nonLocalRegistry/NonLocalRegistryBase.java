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

import jdk.test.lib.UIBuilder;

import javax.swing.*;

public class NonLocalRegistryBase {
    static final String instructions =
            "This is a manual test that requires rmiregistry run on a different host"
                    + ". Login or ssh to a different host, install the latest JDK "
                    + "build and invoke:\n\n"
                    + "$JDK_HOME/bin/rmiregistry"
                    + "\n\nRegistry service is run in the background without any "
                    + "output. Enter the hostname or IP address of the different "
                    + "host below and continue the test.";
    static final String message = "Enter the hostname or IP address here and submit:";
    static final int TIMEOUT_MS = 3600000;
    private volatile boolean abort = false;

    protected String readHostInput() {
        String host = "";
        Thread currentThread = Thread.currentThread();
        UIBuilder.DialogBuilder db = new UIBuilder.DialogBuilder()
                .setTitle("NonLocalRegistrTest")
                .setInstruction(instructions)
                .setMessage(message)
                .setSubmitAction(e -> currentThread.interrupt())
                .setCloseAction(() -> {
                    abort = true;
                    currentThread.interrupt();
                });
        JTextArea input = db.getMessageText();
        JDialog dialog = db.build();

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
        } catch (InterruptedException e) {
        } finally {
            if (abort) {
                throw new RuntimeException("TEST ABORTED");
            }
            host = input.getText().replaceAll(message, "").strip().trim();
            dialog.dispose();
        }
        return host;
    }
}
