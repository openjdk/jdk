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

import jdk.test.lib.Utils;

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
    private static final int SOCKET_TIMEOUT_IN_MS = (int)Utils.adjustTimeout(10_000L);

    private final int serverSocketPort;
    private final ServerSocket serverSocket;
    private final ExecutorService acceptExecutor;
    private final ExecutorService clientExecutor;

    /*
     * CompletableFuture shared by the Driver VM and the accept/reader threads.
     *
     * Lifecycle:
     * 1. The future is created before the accept loop task is submitted.
     * 2. The Driver VM starts the Test VM. The socket and the executors remain open while the Driver VM waits on the
     *    future to be completed.
     * 3. The accept thread accepts the Test VM connection and reads the identity handshake.
     * 4. The accept thread now schedules a reader for incoming Test VM messages.
     * 5. During normal execution, the Test VM closes the connection before exiting. The reader completes the future
     *    with the parsed Test VM messages.
     * 6. Accepting, identity handshake, task submission, or message reading failures complete the future exceptionally.
     * 7. The Driver VM obtains the result, observes a failure, or times out before the socket and executors are closed.
     *
     * Note: The future must be created eagerly such that the Driver VM can wait on it even before the accept thread
     *       has accepted the Test VM connection. The accept thread might only be scheduled after the Test VM has exited.
     *       This is possible because the server socket is already listening and the OS can queue the connection until
     *       the accept thread processes it.
     */
    private final CompletableFuture<JavaMessages> javaMessagesFuture;

    // Written by the Driver VM thread and read by the accept thread.
    private volatile boolean running;

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
        javaMessagesFuture = new CompletableFuture<>();
        if (TestFramework.VERBOSE) {
            System.out.println("TestFramework server socket uses port " + serverSocketPort);
        }
    }

    public String getPortPropertyFlag() {
        return "-D" + SERVER_PORT_PROPERTY + "=" + serverSocketPort;
    }

    public void start() {
        running = true;
        acceptExecutor.submit(this::acceptLoop);
    }

    /**
     * Main loop to wait for new client connections and handling them upon connection request.
     */
    private void acceptLoop() {
        while (running) {
            try {
                acceptNewClientConnection();
            } catch (SocketException e) {
                if (!running || serverSocket.isClosed()) {
                    // Normal shutdown
                    return;
                }
                throwServerSocketError(e);
            } catch (TestFrameworkException e) {
                throwTestFrameworkException(e);
            } catch (Exception e) {
                throwServerSocketError(e);
            }
        }
    }

    private void throwServerSocketError(Exception e) {
        throwTestFrameworkException(new TestFrameworkException("Server socket error", e));
    }

    private void throwTestFrameworkException(TestFrameworkException testFrameworkException) {
        running = false;
        javaMessagesFuture.completeExceptionally(testFrameworkException);
        throw testFrameworkException;
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
            client.setSoTimeout(SOCKET_TIMEOUT_IN_MS);
            identity = reader.readLine();
            TestFramework.check(identity != null, "end of stream has been reached without reading the identity");
        } catch (SocketTimeoutException e) {
            throw new TestFrameworkException("Timed out while waiting for initial identity message", e);
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
            TestVmMessageReader<JavaMessages> messageReader =
                    new TestVmMessageReader<>(client, reader, new JavaMessageParser());
            javaMessagesFuture.completeAsync(messageReader::call, clientExecutor);
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
            // Note: The Test VM may have already exited while the accept and message reader thread are still processing
            //       the connection. Let's wait until they are finished.
            return javaMessagesFuture.get(SOCKET_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new TestFrameworkException("No test VM messages were received", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out while waiting for Test VM messages." + System.lineSeparator() +
                                        System.lineSeparator() +
                                        "Did any of the following happen?" + System.lineSeparator() +
                                        "(1) TestFramework.addFlags(-DReproduce=true)" + System.lineSeparator() +
                                        "(2) TestFramework.addFlags(--version) or any other VM flag that prevents " +
                                        " TestVM.main() from being called?" + System.lineSeparator() +
                                        "(3) The Test VM crashed before calling TestVM.main()" + System.lineSeparator() +
                                        System.lineSeparator() +
                                        "(1) and (2) are unsupported and are expected to fail." + System.lineSeparator() +
                                        "-> Please change your test!" + System.lineSeparator() +
                                        "(3) The IR Framework cannot handle early VM crashes." + System.lineSeparator() +
                                        "-> Please change your test if such a crash was anticipated!" +
                                        System.lineSeparator() + System.lineSeparator() +
                                        "In all other cases, please file an IR Framework bug!", e);
        } catch (Exception e) {
            throw new TestFrameworkException("Error while fetching Test VM Future", e);
        }
    }
}
