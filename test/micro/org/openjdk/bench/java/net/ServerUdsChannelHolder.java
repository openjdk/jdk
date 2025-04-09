/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.net;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A thread-safe utility class to create and destroy {@link ServerSocketChannel}s using unix domain sockets.
 */
final class ServerUdsChannelHolder implements AutoCloseable {

    private final Path socketFilePath;

    final ServerSocketChannel channel;

    private ServerUdsChannelHolder(String tempDirPrefix) {
        try {
            // Socket file will be created by `bind()`, hence, we must point to a non-existent file.
            this.socketFilePath = Files.createTempDirectory(tempDirPrefix).resolve("sock");
            UnixDomainSocketAddress socketAddress = UnixDomainSocketAddress.of(socketFilePath);
            this.channel = ServerSocketChannel
                    .open(StandardProtocolFamily.UNIX)
                    .bind(socketAddress);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    static ServerUdsChannelHolder forClass(Class<?> clazz) {
        return new ServerUdsChannelHolder(clazz.getSimpleName() + '-');
    }

    @Override
    public void close() {
        try {
            channel.close();
            Files.delete(socketFilePath);
            Files.delete(socketFilePath.getParent());
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    @Override
    public String toString() {
        return "" + socketFilePath;
    }

}
