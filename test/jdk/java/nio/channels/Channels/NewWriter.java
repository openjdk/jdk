/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @bug 8295797
 * @summary Test behavior of Channels.newWriter for WritableByteChannels
 * @run junit NewWriter
 */
public class NewWriter {
    private static final String STRING = "test";
    private static final int COUNT = 5;
    private static final int EXPECTED = COUNT*STRING.length();
    private int actual = 0;

    @Test
    public void customWritableByteChannel() throws IOException {
        try (Writer writer = Channels.newWriter(new WritableByteChannel() {
            @Override
            public int write(ByteBuffer src) {
                System.out.print((char) src.get());
                actual++;
                return 1;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() {
            }
        }, StandardCharsets.UTF_8)) {
            for (int i = 1; i <= COUNT; i++) {
                writer.write(STRING);
                writer.flush();
                System.out.println(i);
            }
        }
        assertEquals(EXPECTED, actual);
    }

    @Test
    public void socketChannel() throws IOException {
        Throwable thrown = assertThrows(IllegalBlockingModeException.class,
        () -> {
            try (ServerSocket ss = new ServerSocket();
                 SocketChannel sc = SocketChannel.open()) {

                InetAddress lb = InetAddress.getLoopbackAddress();
                ss.bind(new InetSocketAddress(lb, 0));
                sc.connect(ss.getLocalSocketAddress());
                sc.configureBlocking(false);
                try (Writer writer = Channels.newWriter(sc,
                    StandardCharsets.UTF_8)) {
                    for (int i = 1; i < Integer.MAX_VALUE; i++) {
                        writer.write("test" + i);
                    }
                }
            }
        });
    }
}
