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

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng/othervm/timeout=180 Skip
 * @bug 8265891
 * @summary Tests whether sun.nio.ch.ChannelInputStream.skip and
 *          sun.nio.ch.SocketInputStream.skip conform to the
 *          InputStream.skip specification
 * @key randomness
 */
public class Skip extends SkipBase {

    /*
     * Provides test scenarios, i.e., input streams to be tested.
     */
    @DataProvider
    public static Object[] streams() {
        return new Object[] {
            // tests FileChannel.skip() optimized case
            fileChannelInput(),

            // tests SourceChannelImpl.skip() optimized case
            sourceChannelImplInput(),

            // tests SocketChannel.skip() optimized case
            socketChannelInput(),

            // tests InputStream.skip() default case
            readableByteChannelInput()
        };
    }

    /*
     * Testing API compliance: 0...n bytes of input stream must be
     * skipped, and the remainder of bytes must not be changed.
     */
    @Test(dataProvider = "streams")
    public void testStreamContents(InputStreamProvider inputStreamProvider) throws Exception {
        assertStreamContents(inputStreamProvider);
    }

    /*
     * Creates a provider for an input stream which wraps a file channel
     */
    private static InputStreamProvider fileChannelInput() {
        return bytes -> {
            Path path = Files.createTempFile(CWD, "fileChannelInput", null);
            Files.write(path, bytes);
            FileChannel fileChannel = FileChannel.open(path);
            return Channels.newInputStream(fileChannel);
        };
    }

    /*
     * Creates a provider for an input stream which wraps a pipe channel
     */
    private static InputStreamProvider sourceChannelImplInput() {
        return bytes -> {
            Pipe pipe = Pipe.open();
            new Thread(() -> {
                try (OutputStream os = Channels.newOutputStream(pipe.sink())) {
                    os.write(bytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            return Channels.newInputStream(pipe.source());
        };
    }

    /*
     * Creates a provider for an input stream which wraps a socket channel
     */
    private static InputStreamProvider socketChannelInput() {
        return bytes -> {
            try {
                SocketAddress loopback = new InetSocketAddress(
                        InetAddress.getLoopbackAddress(), 0);
                ServerSocketChannel serverSocket = ServerSocketChannel.open()
                        .bind(loopback);
                new Thread(() -> {
                    try (SocketChannel client = SocketChannel.open(
                                serverSocket.getLocalAddress());
                            OutputStream os = Channels.newOutputStream(client)) {
                        os.write(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }).start();
                return Channels.newInputStream(serverSocket.accept());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
