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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A JUnit extension containing an ephemeral {@link ServerSocket} that tests can use to verify connectivity-related issues.
 */
final class ServerSocketExtension implements BeforeAllCallback, AfterAllCallback {

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    ServerSocket serverSocket;

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(0));
        // Avoid blocking the JUnit pipeline; accept connections on a separate thread
        executorService.submit(() -> acceptConnections(executorService, serverSocket));
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    private static void acceptConnections(ExecutorService executorService, ServerSocket serverSocket) {
        System.err.println("[Test socket server] Accepting connections");
        while (true) {
            try {

                Socket clientSocket = serverSocket.accept();
                System.err.format(
                        "[Test socket server] Accepted port %d to port %d%n",
                        ((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getPort(),
                        clientSocket.getLocalPort());

                // Server is shared by multiple tests, hence don't block the `accept()` loop.
                // Consume the established connections on a separate thread.
                executorService.submit(() -> {
                    try (clientSocket; InputStream inputStream = clientSocket.getInputStream()) {
                        // Instead of directly closing the socket, we try to read some to block. Directly closing
                        // the socket will invalidate the client socket tests checking the established connection
                        // status.
                        inputStream.read();
                    } catch (IOException _) {
                        // Do nothing
                    }
                    // Do nothing
                });

            } catch (IOException _) {
                break;
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        System.err.println("[Test socket server] Shutting down");
        executorService.shutdownNow();
        serverSocket.close();
    }

}
