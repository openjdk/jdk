/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.sun.hotspot.igv.connection;

import com.sun.hotspot.igv.data.GraphDocument;
import com.sun.hotspot.igv.data.serialization.Parser;
import com.sun.hotspot.igv.data.serialization.Printer.GraphContextAction;
import com.sun.hotspot.igv.settings.Settings;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Server implements PreferenceChangeListener {
    private ServerSocketChannel serverSocket;
    private final GraphDocument graphDocument;
    private final GraphContextAction contextAction;
    private int port;

    private volatile boolean isServerRunning;

    public Server(GraphDocument graphDocument, GraphContextAction contextAction) {
        this.graphDocument = graphDocument;
        this.contextAction = contextAction;
        port = Integer.parseInt(Settings.get().get(Settings.PORT, Settings.PORT_DEFAULT));
        Settings.get().addPreferenceChangeListener(this);
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        int curPort = Integer.parseInt(Settings.get().get(Settings.PORT, Settings.PORT_DEFAULT));
        if (curPort != port) {
            port = curPort;
            shutdownServer();
            startServer();
        }
    }

    public void startServer() {
        isServerRunning = true;

        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(port));
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        Runnable client = () -> {
            while (isServerRunning) {
                try {
                    SocketChannel clientSocket = serverSocket.accept();
                    if (!isServerRunning) {
                        clientSocket.close();
                        return;
                    }
                    new Thread(() -> {
                        try (clientSocket) {
                            clientSocket.configureBlocking(true);
                            clientSocket.socket().getOutputStream().write('y');
                            new Parser(clientSocket, null, graphDocument, contextAction).parse();
                        } catch (IOException ignored) {}
                    }).start();
                } catch (IOException ex) {
                    if (isServerRunning) {
                        ex.printStackTrace();
                    }
                    return;
                }
            }
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        };

        new Thread(client).start();
    }

    public void shutdownServer() {
        isServerRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
