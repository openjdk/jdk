/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.shared;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.network.*;
import compiler.lib.ir_framework.driver.network.testvm.TestVmMessageReader;
import compiler.lib.ir_framework.driver.network.testvm.java.JavaMessages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.concurrent.*;

/**
 * Dedicated Driver VM socket to receive data from the Test VM. Could either be received from Java and C2 code.
 */
public class TestFrameworkSocket implements AutoCloseable {
    private static final String SERVER_PORT_PROPERTY = "ir.framework.server.port";

    private final int serverSocketPort;
    private final ServerSocket serverSocket;
    private boolean running;
    private final ExecutorService executor;
    private Future<JavaMessages> javaFuture;

    public TestFrameworkSocket() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        } catch (IOException e) {
            throw new TestFrameworkException("Failed to create TestFramework server socket", e);
        }
        serverSocketPort = serverSocket.getLocalPort();
        executor = Executors.newCachedThreadPool();
        if (TestFramework.VERBOSE) {
            System.out.println("TestFramework server socket uses port " + serverSocketPort);
        }
        start();
    }

    public String getPortPropertyFlag() {
        return "-D" + SERVER_PORT_PROPERTY + "=" + serverSocketPort;
    }

    private void start() {
        running = true;
        executor.submit(this::acceptLoop);
    }

    /**
     * Main loop to wait for new client connections and handling them upon connection request.
     */
    private void acceptLoop() {
        while (running) {
            try {
                acceptNewClientConnection();
            } catch (TestFrameworkException e) {
                running = false;
                throw e;
            } catch (Exception e) {
                running = false;
                throw new TestFrameworkException("Server socket error", e);
            }
        }
    }

    /**
     * Accept new client connection and then submit a task accordingly to manage incoming message on that connection/socket.
     */
    private void acceptNewClientConnection() throws IOException {
        Socket client = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        submitTask(client, reader);
    }

    /**
     * Submit dedicated tasks which are wrapped into {@link Future} objects. The tasks will read all messages sent
     * over that connection.
     */
    private void submitTask(Socket client, BufferedReader reader) {
        javaFuture = executor.submit(new TestVmMessageReader(client, reader));
    }

    @Override
    public void close() {
        try {
            running = false;
            serverSocket.close();
        } catch (IOException e) {
            throw new TestFrameworkException("Could not close socket", e);
        }
        executor.shutdown();
    }

    public TestVMData testVmData(String hotspotPidFileName, boolean allowNotCompilable) {
        JavaMessages javaMessages = testVmMessages();
        return new TestVMData(javaMessages, hotspotPidFileName, allowNotCompilable);
    }

    private JavaMessages testVmMessages() {
        try {
            return javaFuture.get();
        } catch (ExecutionException e) {
            throw new TestFrameworkException("No test VM messages were received", e);
        } catch (Exception e) {
            throw new TestFrameworkException("Error while fetching Test VM Future", e);
        }
    }
}
