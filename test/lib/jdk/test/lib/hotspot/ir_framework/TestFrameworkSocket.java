/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.hotspot.ir_framework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.FutureTask;

/**
 * Dedicated socket to send data from the flag and test VM back to the driver VM.
 */
class TestFrameworkSocket implements AutoCloseable {
    static final String SERVER_PORT_PROPERTY = "ir.framework.server.port";

    // Static fields used by flag and test VM only.
    private static final int SERVER_PORT = Integer.getInteger(SERVER_PORT_PROPERTY, -1);

    private static final boolean REPRODUCE = Boolean.getBoolean("Reproduce");
    private static final String HOSTNAME = null;
    private static final String STDOUT_PREFIX = "[STDOUT]";
    private static Socket clientSocket = null;
    private static PrintWriter clientWriter = null;

    private final String serverPortPropertyFlag;
    private FutureTask<String> socketTask;
    private final ServerSocket serverSocket;

    private static TestFrameworkSocket singleton = null;

    private TestFrameworkSocket() {
        try {
            serverSocket = new ServerSocket(0);
        } catch (IOException e) {
            throw new TestFrameworkException("Failed to create TestFramework server socket", e);
        }
        int port = serverSocket.getLocalPort();
        if (TestFramework.VERBOSE) {
            System.out.println("TestFramework server socket uses port " + port);
        }
        serverPortPropertyFlag = "-D" + SERVER_PORT_PROPERTY + "=" + port;
    }

    public static TestFrameworkSocket getSocket() {
        if (singleton == null || singleton.serverSocket.isClosed()) {
            singleton = new TestFrameworkSocket();
            return singleton;
        }
        return singleton;
    }

    public String getPortPropertyFlag() {
        return serverPortPropertyFlag;
    }

    public void start() {
        socketTask = initSocketTask();
        Thread socketThread = new Thread(socketTask);
        socketThread.start();
    }

    /**
     * Waits for client sockets (created by flag or test VM) to connect. Return the messages received by the clients.
     */
    private FutureTask<String> initSocketTask() {
        return new FutureTask<>(() -> {
            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
            ) {
                StringBuilder builder = new StringBuilder();
                String next;
                while ((next = in.readLine()) != null) {
                    builder.append(next).append(System.lineSeparator());
                }
                return builder.toString();
            } catch (IOException e) {
                throw new TestFrameworkException("Server socket error", e);
            }
        });
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new TestFrameworkException("Could not close socket", e);
        }
    }

    /**
     * Only called by flag and test VM to write to server socket.
     */
    public static void write(String msg, String type) {
        write(msg, type, false);
    }

    /**
     * Only called by flag and test VM to write to server socket.
     */
    public static void write(String msg, String type, boolean stdout) {
        if (REPRODUCE) {
            System.out.println("Debugging Test VM: Skip writing due to -DReproduce");
            return;
        }
        TestFramework.check(SERVER_PORT != -1, "Server port was not set correctly for flag and/or test VM "
                                               + "or method not called from flag or test VM");
        try {
            // Keep the client socket open until the flag or test VM terminates (calls closeClientSocket before exiting
            // main()).
            if (clientSocket == null) {
                clientSocket = new Socket(HOSTNAME, SERVER_PORT);
                clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
            }
            if (stdout) {
                msg = STDOUT_PREFIX + msg;
            }
            clientWriter.println(msg);
        } catch (Exception e) {
            // When the test VM is directly run, we should ignore all messages that would normally be sent to the
            // driver VM.
            String failMsg = System.lineSeparator() + System.lineSeparator() + """
                             ###########################################################
                              Did you directly run the test VM (TestFrameworkExecution)
                              to reproduce a bug?
                              => Append the flag -DReproduce=true and try again!
                             ###########################################################
                             """;
            throw new TestRunException(failMsg, e);
        }
        if (TestFramework.VERBOSE) {
            System.out.println("Written " + type + " to socket:");
            System.out.println(msg);
        }
    }

    /**
     * Closes (and flushes) the printer to the socket and the socket itself. Is called as last thing before exiting
     * the main() method of the flag and the test VM.
     */
    public static void closeClientSocket() {
        if (clientSocket != null) {
            try {
                clientWriter.close();
                clientSocket.close();
            } catch (IOException e) {
                throw new RuntimeException("Could not close TestFrameworkExecution socket", e);
            }
        }
    }

    /**
     * Get the socket output of the flag VM.
     */
    public String getOutput() {
        try {
            return socketTask.get();

        } catch (Exception e) {
            throw new TestFrameworkException("Could not read from socket task", e);
        }
    }

    /**
     * Get the socket output from the test VM by stripping all lines starting with a [STDOUT] output and printing them
     * to the standard output.
     */
    public String getOutputPrintStdout() {
        try {
            String output = socketTask.get();
            if (TestFramework.TESTLIST || TestFramework.EXCLUDELIST) {
                StringBuilder builder = new StringBuilder();
                Scanner scanner = new Scanner(output);
                System.out.println(System.lineSeparator() + "Run flag defined test list");
                System.out.println("--------------------------");
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith(STDOUT_PREFIX)) {
                        line = "> " + line.substring(STDOUT_PREFIX.length());
                        System.out.println(line);
                    } else {
                        builder.append(line).append(System.lineSeparator());
                    }
                }
                System.out.println();
                return builder.toString();
            }
            return output;

        } catch (Exception e) {
            throw new TestFrameworkException("Could not read from socket task", e);
        }
    }
}
