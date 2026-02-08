/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.test.network;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.TestRunException;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class TestVmSocket {
    private static final boolean REPRODUCE = Boolean.getBoolean("Reproduce");
    private static final String SERVER_PORT_PROPERTY = "ir.framework.server.port";
    private static final int SERVER_PORT = Integer.getInteger(SERVER_PORT_PROPERTY, -1);

    private static Socket socket = null;
    private static PrintWriter writer = null;

    /**
     * Send a message to the Driver VM which is unconditionally shown in the Driver VM output.
     */
    public static void send(String message) {
        sendWithTag(MessageTag.STDOUT, message);
    }

    /**
     * Send a message to the Driver VM with a {@link MessageTag}. Not all messages are shown by default in the
     * Driver VM output and require setting some property flags first like {@code -DPrintTimes=true}.
     */
    public static void sendWithTag(String tag, String message) {
        if (REPRODUCE) {
            // Debugging Test VM: Skip writing due to -DReproduce;
            return;
        }

        TestFramework.check(socket != null, "must be connected");
        writer.println(tag + " " + message);
    }

    public static void connect() {
        if (REPRODUCE) {
            // Debugging Test VM: Skip writing due to -DReproduce;
            return;
        }

        TestFramework.check(SERVER_PORT != -1, "Server port was not set correctly for flag and/or test VM "
                + "or method not called from flag or test VM");

        try {
            // Keep the client socket open until the test VM terminates (calls closeClientSocket before exiting main()).
            socket = new Socket(InetAddress.getLoopbackAddress(), SERVER_PORT);
            writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            // When the test VM is directly run, we should ignore all messages that would normally be sent to the
            // driver VM.
            String failMsg = System.lineSeparator() + System.lineSeparator() + """
                             ###########################################################
                              Did you directly run the test VM (TestVM class)
                              to reproduce a bug?
                              => Append the flag -DReproduce=true and try again!
                             ###########################################################
                             """;
            throw new TestRunException(failMsg, e);
        }

    }

    /**
     * Closes (and flushes) the printer to the socket and the socket itself. Is called as last thing before exiting
     * the main() method of the flag and the test VM.
     */
    public static void close() {
        if (socket != null) {
            writer.close();
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException("Could not close TestVM socket", e);
            }
        }
    }
}
