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

package jdk.jfr.event.io;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static jdk.test.lib.Asserts.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;

import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @summary Verify that file and socket event classes are not loaded when JFR is off.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestEventsNotLoadedWithoutJfr
 */
public class TestEventsNotLoadedWithoutJfr {
    public static void main(String[] args) throws Throwable {
        List<String> a = new ArrayList<>();
        a.add("-Xlog:class+load=trace");
        a.add(TestEventsNotLoadedWithoutJfr.Helper.class.getName());
        Process p = ProcessTools.createTestJavaProcessBuilder(a).start();
        OutputAnalyzer output = new OutputAnalyzer(p);
        output.waitFor();
        output.shouldHaveExitValue(0);
        output.shouldNotContain("jdk.internal.event.FileForceEvent");
        output.shouldNotContain("jdk.internal.event.SocketWriteEvent");
        output.shouldNotContain("jdk.internal.event.SocketReadEvent");
    }

    public static class Helper {
        private static final byte[] SERVER_MSG = "server message".getBytes();
        private static final byte[] CLIENT_MSG = "client message".getBytes();

        public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
            exerciseFileForce();
            exerciseSocket();
            exerciseSocketChannelImpl();
            exerciseSocketInputStream();
        }

        private static void exerciseFileForce() throws IOException, ExecutionException, InterruptedException {
            Path tmp = Files.createTempFile(Path.of(""), "TestEventsNotLoadedWithoutJfr", ".tmp");
            try {
                String s = "short";
                ByteBuffer data = ByteBuffer.allocate(s.length());
                data.put(s.getBytes());
                data.flip();

                // Check both AsynchronousFileChannel and FileChannelImpl
                try (AsynchronousFileChannel afc = AsynchronousFileChannel.open(tmp, READ, WRITE)) {
                    int expectedWritten = data.remaining();
                    int actualWritten = afc.write(data, 0).get();
                    assertEquals(actualWritten, expectedWritten, "Unexpected amount written.");
                    afc.force(true);
                }
                data.flip();
                try (FileChannel fc = FileChannel.open(tmp, READ, WRITE)) {
                    int expectedWritten = data.remaining();
                    int actualWritten = fc.write(data);
                    assertEquals(actualWritten, expectedWritten, "Unexpected amount written.");
                    fc.force(true);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        // Exercises inner Socket$SocketInputStream / Socket$SocketOutputStream
        private static void exerciseSocket() throws IOException, InterruptedException {
            try (ServerSocket ss = new ServerSocket()) {
                ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

                Thread clientThread = new Thread(() -> {
                    try (Socket client = new Socket()) {
                        client.connect(ss.getLocalSocketAddress());
                        try (InputStream in = client.getInputStream();
                             OutputStream out = client.getOutputStream()) {
                            out.write(CLIENT_MSG);
                            byte[] buf = new byte[SERVER_MSG.length];
                            in.read(buf);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                clientThread.start();

                try (Socket server = ss.accept();
                     InputStream in = server.getInputStream();
                     OutputStream out = server.getOutputStream()) {
                    byte[] buf = new byte[CLIENT_MSG.length];
                    in.read(buf);
                    out.write(SERVER_MSG);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                clientThread.join();
            }
        }

        private static void exerciseSocketChannelImpl() {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

                Thread serverThread = new Thread(() -> {
                    try (SocketChannel server = ssc.accept()) {
                        // Exercise write(ByteBuffer) and read(ByteBuffer)
                        ByteBuffer buf = ByteBuffer.allocate(CLIENT_MSG.length);
                        server.read(buf);
                        server.write(ByteBuffer.wrap(SERVER_MSG));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                serverThread.start();

                try (SocketChannel client = SocketChannel.open()) {
                    client.connect(ssc.getLocalAddress());
                    // Exercise write(ByteBuffer[], int, int) and read(ByteBuffer[], int, int)
                    client.write(new ByteBuffer[]{ByteBuffer.wrap(CLIENT_MSG)}, 0, 1);
                    ByteBuffer buf = ByteBuffer.allocate(SERVER_MSG.length);
                    client.read(new ByteBuffer[]{buf}, 0, 1);
                }

                serverThread.join();
            } catch (IOException |InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private static void exerciseSocketInputStream() {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

                Thread serverThread = new Thread(() -> {
                   try (SocketChannel server = ssc.accept()) {
                       try (InputStream in = server.socket().getInputStream();
                            OutputStream out = server.socket().getOutputStream()) {
                           byte[] buf = new byte[CLIENT_MSG.length];
                           in.read(buf);
                           out.write(SERVER_MSG);
                       }
                   } catch (Exception e) {
                       throw new RuntimeException(e);
                   }
                });
                serverThread.start();

                try (SocketChannel client = SocketChannel.open()) {
                    client.connect(ssc.getLocalAddress());
                    try (InputStream in = client.socket().getInputStream();
                         OutputStream out = client.socket().getOutputStream()) {
                        out.write(CLIENT_MSG);
                        byte[] buf = new byte[SERVER_MSG.length];
                        in.read(buf);
                    }
                }

                serverThread.join();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

