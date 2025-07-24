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
 * @run junit ChannelInputStreamTest
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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;

public class ChannelInputStreamTest {
    private static Thread startCloseThread(ConcurrentLinkedQueue<Object> q) {
        return Thread.ofPlatform().start(() -> {
            do {
                Object obj;
                if ((obj = q.poll()) != null) {
                    if (obj instanceof Closeable c) {
                        try {
                            c.close();
                        } catch (IOException ignored) {
                        }
                    } else {
                        break;
                    }
                }
            } while (true);
        });
    }

/*
    public static void main(String[] args) throws IOException {
        int failures = 0;

        try {
            testAvailable();
        } catch (RuntimeException e) {
            failures++;
        }

        try {
            testRead();
        } catch (RuntimeException e) {
            failures++;
        }
        try {
            testWrite();
        } catch (RuntimeException e) {
            failures++;
        }

        if (failures != 0) {
            throw new RuntimeException("FAILED with " + failures + " failures");
        }
    }
*/

    @Test
    public void available() throws IOException {
        var close = new ConcurrentLinkedQueue<Object>();
        Thread closeThread = startCloseThread(close);

        Path path = Files.createTempFile(Path.of("."), "foo", "bar");
        path.toFile().deleteOnExit();

        do {
            InputStream in;
            try {
                in = Files.newInputStream(path);
            } catch (IOException ignored) {
                continue;
            }
            close.offer(in);
            int available = 0;
            try {
                available = in.available();
            } catch (AsynchronousCloseException ace) {
                System.out.println("AsynchronousCloseException caught");
                close.offer(new Object());
                break;
            } catch (ClosedChannelException ignored) {
                System.out.println("ClosedChannelException ignored");
                continue;
            } catch (Throwable t) {
                close.offer(new Object());
                throw new RuntimeException("Unexpected error", t);
            }
            if (available < 0) {
                close.offer(new Object());
                throw new RuntimeException("FAILED: available < 0");
            }
        } while (true);

        try {
            closeThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    @Test
    public void read() throws IOException {
        var close = new ConcurrentLinkedQueue<Object>();
        Thread closeThread = startCloseThread(close);

        Path path = Files.createTempFile(Path.of("."), "foo", "bar");
        path.toFile().deleteOnExit();
        byte[] bytes = new byte[100_000];
        Arrays.fill(bytes, (byte)27);
        Files.write(path, bytes);

        do {
            InputStream in;
            try {
                in = Files.newInputStream(path);
            } catch (IOException ignored) {
                continue;
            }
            close.offer(in);
            int value = 0;
            try {
                value = in.read();
            } catch (AsynchronousCloseException ace) {
                System.out.println("AsynchronousCloseException caught");
                close.offer(new Object());
                break;
            } catch (ClosedChannelException ignored) {
                System.out.println("ClosedChannelException ignored");
                continue;
            } catch (Throwable t) {
                close.offer(new Object());
                throw new RuntimeException("Unexpected error", t);
            }
            if (value < 0) {
                close.offer(new Object());
                throw new RuntimeException("FAILED: value < 0");
            }
        } while (true);

        try {
            closeThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    @Test
    public void write() throws IOException {
        var close = new ConcurrentLinkedQueue<Object>();
        Thread closeThread = startCloseThread(close);
        Path path = Files.createTempFile(Path.of("."), "foo", "bar");
        path.toFile().deleteOnExit();

        do {
            OutputStream out;
            try {
                out = Files.newOutputStream(path);
            } catch (IOException ignored) {
                continue;
            }
            close.offer(out);
            try {
                out.write(27);
            } catch (AsynchronousCloseException ace) {
                System.out.println("AsynchronousCloseException caught");
                close.offer(new Object());
                break;
            } catch (ClosedChannelException ignored) {
                System.out.println("ClosedChannelException ignored");
            } catch (Throwable t) {
                close.offer(new Object());
                throw new RuntimeException("Write error", t);
            }
        } while (true);

        try {
            closeThread.join();
        } catch (InterruptedException ignored) {
        }
    }
}
