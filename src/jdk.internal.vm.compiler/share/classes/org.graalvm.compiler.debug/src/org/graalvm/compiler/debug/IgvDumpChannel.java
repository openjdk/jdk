/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;
import static org.graalvm.compiler.debug.DebugOptions.PrintBinaryGraphPort;
import static org.graalvm.compiler.debug.DebugOptions.PrintGraphHost;
import org.graalvm.compiler.options.OptionValues;

final class IgvDumpChannel implements WritableByteChannel {
    private final Supplier<Path> pathProvider;
    private final OptionValues options;
    private WritableByteChannel sharedChannel;
    private boolean closed;

    IgvDumpChannel(Supplier<Path> pathProvider, OptionValues options) {
        this.pathProvider = pathProvider;
        this.options = options;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return channel().write(src);
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
    }

    void realClose() throws IOException {
        closed = true;
        if (sharedChannel != null) {
            sharedChannel.close();
            sharedChannel = null;
        }
    }

    WritableByteChannel channel() throws IOException {
        if (closed) {
            throw new IOException();
        }
        if (sharedChannel == null) {
            if (DebugOptions.PrintGraphFile.getValue(options)) {
                sharedChannel = createFileChannel(pathProvider);
            } else {
                sharedChannel = createNetworkChannel(pathProvider, options);
            }
        }
        return sharedChannel;
    }

    private static WritableByteChannel createNetworkChannel(Supplier<Path> pathProvider, OptionValues options) throws IOException {
        String host = PrintGraphHost.getValue(options);
        int port = PrintBinaryGraphPort.getValue(options);
        try {
            WritableByteChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
            TTY.println("Connected to the IGV on %s:%d", host, port);
            return channel;
        } catch (ClosedByInterruptException | InterruptedIOException e) {
            /*
             * Interrupts should not count as errors because they may be caused by a cancelled Graal
             * compilation. ClosedByInterruptException occurs if the SocketChannel could not be
             * opened. InterruptedIOException occurs if new Socket(..) was interrupted.
             */
            return null;
        } catch (IOException e) {
            if (!DebugOptions.PrintGraphFile.hasBeenSet(options)) {
                return createFileChannel(pathProvider);
            } else {
                throw new IOException(String.format("Could not connect to the IGV on %s:%d", host, port), e);
            }
        }
    }

    private static WritableByteChannel createFileChannel(Supplier<Path> pathProvider) throws IOException {
        Path path = pathProvider.get();
        try {
            return FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new IOException(String.format("Failed to open %s to dump IGV graphs", path), e);
        }
    }

}
