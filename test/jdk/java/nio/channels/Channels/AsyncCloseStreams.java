/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8361495
 * @summary Test for AsynchronousCloseException from uninterruptible FileChannel
 * @run junit AsyncCloseStreams
 */

import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.LinkedTransferQueue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncCloseStreams {
    private static final Closeable STOP = () -> { };

    private static Thread startCloseThread(LinkedTransferQueue<Closeable> q) {
        return Thread.ofPlatform().start(() -> {
                try {
                    Closeable c;
                    while((c = q.take()) != STOP) {
                        try {
                            c.close();
                        } catch (IOException ignored) {
                        }
                    }
                } catch (InterruptedException ignored) {
                }
            });
    }

    @Test
    public void available() throws InterruptedException, IOException {
        var close = new LinkedTransferQueue<Closeable>();
        Thread closeThread = startCloseThread(close);

        try {
            Path path = Files.createTempFile(Path.of("."), "foo", "bar");
            path.toFile().deleteOnExit();

            do {
                InputStream in = Files.newInputStream(path);
                close.offer(in);
                int available = 0;
                try {
                    available = in.available();
                } catch (AsynchronousCloseException ace) {
                    System.err.println("AsynchronousCloseException caught");
                    break;
                } catch (ClosedChannelException ignored) {
                    continue;
                } catch (Throwable t) {
                    fail("Unexpected error", t);
                }
                if (available < 0) {
                    fail("FAILED: available < 0");
                }
            } while (true);
        } finally {
            close.offer(STOP);
            closeThread.join();
        }
    }

    @Test
    public void read() throws InterruptedException, IOException {
        var close = new LinkedTransferQueue<Closeable>();
        Thread closeThread = startCloseThread(close);

        try {
            Path path = Files.createTempFile(Path.of("."), "foo", "bar");
            path.toFile().deleteOnExit();
            byte[] bytes = new byte[100_000];
            Arrays.fill(bytes, (byte)27);
            Files.write(path, bytes);

            do {
                InputStream in = Files.newInputStream(path);
                close.offer(in);
                int value = 0;
                try {
                    value = in.read();
                } catch (AsynchronousCloseException ace) {
                    System.err.println("AsynchronousCloseException caught");
                    break;
                } catch (ClosedChannelException ignored) {
                    continue;
                } catch (Throwable t) {
                    fail("Unexpected error", t);
                }
                if (value < 0) {
                    fail("FAILED: value < 0");
                }
            } while (true);
        } finally {
            close.offer(STOP);
            closeThread.join();
        }
    }

    @Test
    public void write() throws InterruptedException, IOException {
        var close = new LinkedTransferQueue<Closeable>();
        Thread closeThread = startCloseThread(close);

        try {
            Path path = Files.createTempFile(Path.of("."), "foo", "bar");
            path.toFile().deleteOnExit();
            byte[] bytes = new byte[100_000];
            Arrays.fill(bytes, (byte)27);

            do {
                OutputStream out = Files.newOutputStream(path);
                close.offer(out);
                try {
                    out.write(bytes);
                } catch (AsynchronousCloseException ace) {
                    System.err.println("AsynchronousCloseException caught");
                    break;
                } catch (ClosedChannelException ignored) {
                } catch (Throwable t) {
                    fail("Write error", t);
                }
            } while (true);
        } finally {
            close.offer(STOP);
            closeThread.join();
        }
    }
}
