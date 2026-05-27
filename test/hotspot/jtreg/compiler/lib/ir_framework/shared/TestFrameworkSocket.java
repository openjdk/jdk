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
import compiler.lib.ir_framework.driver.network.testvm.java.JavaMessageParser;
import compiler.lib.ir_framework.driver.network.testvm.java.JavaMessages;
import compiler.lib.ir_framework.test.network.TestVmSocket;

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
    private final ExecutorService acceptExecutor;
    private final ExecutorService clientExecutor;

    // Make these volatile such that the main thread can observe an update written by the worker threads in the executor
    // services to avoid stale values.
    private volatile boolean running;
    private volatile Future<JavaMessages> javaFuture;

    public TestFrameworkSocket() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        } catch (IOException e) {
            throw new TestFrameworkException("Failed to create TestFramework server socket", e);
        }
        serverSocketPort = serverSocket.getLocalPort();
        acceptExecutor = Executors.newSingleThreadExecutor();
        clientExecutor = Executors.newCachedThreadPool();
        if (TestFramework.VERBOSE) {
            System.out.println("TestFramework server socket uses port " + serverSocketPort);
        }
    }

    public String getPortPropertyFlag() {
        return "-D" + SERVER_PORT_PROPERTY + "=" + serverSocketPort;
    }

    public void start() {
        running = true;
        CountDownLatch calledAcceptLoopLatch = new CountDownLatch(1);
        startAcceptLoop(calledAcceptLoopLatch);
    }

    private void startAcceptLoop(CountDownLatch calledAcceptLoopLatch) {
        acceptExecutor.submit(() -> acceptLoop(calledAcceptLoopLatch));
        waitUntilAcceptLoopRuns(calledAcceptLoopLatch);
    }

    private void waitUntilAcceptLoopRuns(CountDownLatch calledAcceptLoopLatch) {
        try {
            if (!calledAcceptLoopLatch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("acceptLoop did not start in time");
            }
        } catch (Exception e) {
            throw new TestFrameworkException("Could not start TestFrameworkSocket", e);
        }
    }

    /**
     * Main loop to wait for new client connections and handling them upon connection request.
     */
    private void acceptLoop(CountDownLatch calledAcceptLoopLatch) {
        calledAcceptLoopLatch.countDown();
        while (running) {
            try {
                acceptNewClientConnection();
            }  catch (SocketException e) {
                if (!running || serverSocket.isClosed()) {
                    // Normal shutdown
                    return;
                }
                running = false;
                throw new TestFrameworkException("Server socket error", e);
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
     * Accept new client connection by first reading the identity of the connection (either coming from Java or C2)
     * and then submitting a task accordingly to manage incoming messages on that connection/socket.
     */
    private void acceptNewClientConnection() throws IOException {
        Socket client = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        try {
            String identity = readIdentity(client, reader).trim();
            submitTask(identity, client, reader);
        } catch (Exception e) {
            client.close();
            reader.close();
            throw e;
        }
    }

    private String readIdentity(Socket client, BufferedReader reader) throws IOException {
        String identity;
        try {
            client.setSoTimeout(10000);
            identity = reader.readLine();
            TestFramework.check(identity != null, "end of stream has been reached without reading the identity");
        } catch (SocketTimeoutException e) {
            throw new TestFrameworkException("Did not receive initial identity message after 10s", e);
        } finally {
            client.setSoTimeout(0);
        }
        return identity;
    }

    /**
     * Submit dedicated tasks which are wrapped into {@link Future} objects. The tasks will read all messages sent
     * over that connection.
     */
    private void submitTask(String identity, Socket client, BufferedReader reader) {
        if (identity.equals(TestVmSocket.IDENTITY)) {
            javaFuture = clientExecutor.submit(new TestVmMessageReader<>(client, reader, new JavaMessageParser()));
        } else {
            throw new TestFrameworkException("Unrecognized identity: " + identity);
        }
    }

    @Override
    public void close() {
        try {
            running = false;
            serverSocket.close();
        } catch (IOException e) {
            throw new TestFrameworkException("Could not close socket", e);
        }
        acceptExecutor.shutdown();
        clientExecutor.shutdown();
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
