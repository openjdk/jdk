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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng/othervm/timeout=180 Skip_2GB
 * @bug 8278268
 * @summary Tests if ChannelInputStream.skip and SocketInputStream.skip
 *          correctly skip 2GB+.
 * @key randomness
 */
public class Skip_2GB extends SkipBase {

    /*
     * Provides test scenarios, i.e., input streams to be tested.
     */
    @DataProvider
    public static Object[] streams() {
        return new Object[] {
            // tests FileChannel.skip() optimized case
            fileChannelInput_2G(),

            // tests SourceChannelImpl.skip() optimized case
            sourceChannelImplInput_2G(),

            // tests SourceChannelImpl.skip() optimized case
            socketChannelInput_2G(),

            // tests InputStream.skip() default case
            readableByteChannelInput_2G()
        };
    }

    /*
     * Testing API compliance: > 0...n bytes of input stream must be
     * skipped, and the remainder of bytes must not be changed.
     */
    @Test(dataProvider = "streams")
    public void testStreamContents(InputStreamProvider_2G inputStreamProvider) throws Exception {
        assertStreamContentsUsingFiles(inputStreamProvider);
    }

    /*
     * Testing API compliance: > 0...n bytes of input stream must be
     * skipped, and the remainder of bytes must not be changed.
     */
    static void assertStreamContentsUsingFiles(InputStreamProvider_2G inputStreamProvider) throws Exception {
        Path inBytes = createRandomBytesFile(Integer.MAX_VALUE - 1L,
                Integer.MAX_VALUE + 1L, Integer.MAX_VALUE);
        try {
            checkSkippedContents(inputStreamProvider, inBytes);
        } finally {
            Files.deleteIfExists(inBytes);
        }
    }

    /*
     * Asserts that the skipped content is correct, i.e. compares the bytes
     * still in the input stream to those expected. The position of the input
     * stream before the skip is zero (BOF), and the number of bytes to skip is
     * "all bytes til EOF".
     */
    static void checkSkippedContents(InputStreamProvider_2G inputStreamProvider,
            Path inBytes) throws Exception {
        checkSkippedContents(inputStreamProvider, inBytes, Files.size(inBytes));
    }

    /*
     * Asserts that the skipped content is correct, i.e. compares the bytes
     * still in the input stream to those expected. The position of the input
     * stream before the skip is zero (BOF). The number of bytes to skip is
     * provided by the caller.
     */
    static void checkSkippedContents(InputStreamProvider_2G inputStreamProvider,
            Path inBytes, long count) throws Exception {
        checkSkippedContents(inputStreamProvider, inBytes, 0, count, false);
    }

    /*
     * Asserts that the skipped content is correct, i.e. compares the bytes
     * still in the input stream to those expected. The position of the input
     * stream before the skip and the number of bytes to skip are provided by
     * the caller.
     */
    static void checkSkippedContents(InputStreamProvider_2G inputStreamProvider,
            Path inBytes, long posIn, long count, boolean mustNotSkipAnything)
            throws Exception {
        try (InputStream in = inputStreamProvider.input(inBytes)) {
            // consume bytes until starting position
            for (long bytesToConsume = posIn; bytesToConsume > 0;
                    bytesToConsume -= in.readNBytes(Math.toIntExact(
                            Math.min(Integer.MAX_VALUE, bytesToConsume))).length);

            long reported = in.skip(count);

            if (mustNotSkipAnything)
                assertTrue(reported == 0,
                        format("must not skip any bytes, but skipped %d", reported));

            // store all remaining bytes in a file
            Path actualRemainderFile = CWD.resolve(
                    format("test3GBActual_skip%s.tmp", RND.nextInt(Integer.MAX_VALUE)));
            try {
                try (OutputStream os = Files.newOutputStream(actualRemainderFile,
                        CREATE_NEW, WRITE, SPARSE)) {
                    in.transferTo(os);
                }

                assertTrue(reported >= 0 && reported <= count,
                        format("reported %d bytes but should report between 0 and %d", reported, count));

                long expectedRemainderLength = Files.size(inBytes) - posIn - reported;
                long actualRemainderLength = Files.size(actualRemainderFile);
                assertEquals(actualRemainderLength, expectedRemainderLength,
                        format("remaining %d bytes but should remain %d", actualRemainderLength, expectedRemainderLength));

                // store expected remaining bytes in a file
                Path expectedRemainderFile = CWD.resolve(
                        format("test3GBExpected_skip%s.tmp", RND.nextInt(Integer.MAX_VALUE)));
                try (OutputStream os = Files.newOutputStream(expectedRemainderFile,
                        CREATE_NEW, WRITE, SPARSE)) {
                    try (FileChannel fc = FileChannel.open(inBytes)) {
                        fc.position(posIn + reported);
                        try (InputStream is = Channels.newInputStream(fc)) {
                            is.transferTo(os);
                        }
                    }

                    // Check similarity of content
                    assertEquals(Files.mismatch(expectedRemainderFile,
                            actualRemainderFile), -1, "remaining bytes are dissimilar");
                } finally {
                    Files.deleteIfExists(expectedRemainderFile);
                }
            } finally {
                Files.deleteIfExists(actualRemainderFile);
            }
        }
    }

    interface InputStreamProvider_2G {
        InputStream input(Path bytes) throws Exception;
    }

    /*
     * Creates a provider for an input stream which wraps a file channel
     */
    private static InputStreamProvider_2G fileChannelInput_2G() {
        return bytes -> {
            FileChannel fileChannel = FileChannel.open(bytes);
            return Channels.newInputStream(fileChannel);
        };
    }

    /*
     * Creates a provider for an input stream which wraps a pipe channel
     */
    private static InputStreamProvider_2G sourceChannelImplInput_2G() {
        return bytes -> {
            Pipe pipe = Pipe.open();
            new Thread(() -> {
                try (OutputStream os = Channels.newOutputStream(pipe.sink());
                        InputStream is = Files.newInputStream(bytes)) {
                    is.transferTo(os);
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
    private static InputStreamProvider_2G socketChannelInput_2G() {
        return bytes -> {
            try {
                SocketAddress loopback = new InetSocketAddress(
                        InetAddress.getLoopbackAddress(), 0);
                ServerSocketChannel serverSocket = ServerSocketChannel.open()
                        .bind(loopback);
                new Thread(() -> {
                    try (SocketChannel client = SocketChannel.open(
                                serverSocket.getLocalAddress());
                            OutputStream os = Channels.newOutputStream(client);
                            InputStream is = Files.newInputStream(bytes)) {
                        is.transferTo(os);
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

    /*
     * Creates a provider for an input stream which wraps a readable byte
     * channel but is not a file channel.
     */
    static InputStreamProvider_2G readableByteChannelInput_2G() {
        return bytes -> Channels.newInputStream(Channels.newChannel(
                new BufferedInputStream(Files.newInputStream(bytes))));
    }

    /*
     * Creates a sparse file of random size (between min and min + maxRandomAdditive)
     * filled with random bytes starting at the provided position.
     */
    static Path createRandomBytesFile(long pos, long min, long maxRandomAdditive) throws IOException {
        Path randomBytesFile = CWD.resolve(
            format("test3GBSource_skip%s.tmp", RND.nextInt(Integer.MAX_VALUE)));
        try (FileChannel fc = FileChannel.open(randomBytesFile, CREATE_NEW, WRITE, SPARSE)) {
            fc.position(pos);
            try (OutputStream os = Channels.newOutputStream(fc)) {
                long remaining = min +
                        (maxRandomAdditive == 0 ? 0 : RND.nextLong(maxRandomAdditive)) - pos;
                while (remaining > 0) {
                    int n = Math.toIntExact(Math.min(16384, remaining));
                    byte[] bytes = createRandomBytes(n, 0);
                    os.write(bytes);
                    remaining -= n;
                }
            }
        }
        return randomBytesFile;
    }

}
