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
 */

import java.io.InputStream;
import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChannelInputStreamTest {
    private static final InputStream CLOSE = new InputStream() {
        @Override
        public int read() throws IOException {
            return 27;
        }
    };

    public static void main(String[] args) throws IOException {
        var close = new ConcurrentLinkedQueue<InputStream>();
        Thread closeThread = Thread.ofPlatform().start(() -> {
            do {
                InputStream in;
                if ((in = close.poll()) != null)
                    if (in == CLOSE) {
                        break;
                    } else {
                        try {
                            in.close();
                        } catch (IOException ignored) {
                        }
                    }
            } while (true);
        });

        Path path = Files.createTempFile(Path.of("."), "foo", "bar");
        path.toFile().deleteOnExit();

        Thread availableThread = Thread.ofPlatform().start(() -> {
            do {
                InputStream in;
                try {
                    in = Files.newInputStream(path);
                } catch (IOException ignored) {
                    continue;
                }
                close.offer(in);
                int available;
                try {
                    available = in.available();
                } catch (Throwable t) {
                    if (AsynchronousCloseException.class.isInstance(t)) {
                        System.out.println("AsynchronousCloseException");
                        close.offer(CLOSE);
                        break;
                    }
                    continue;
                }
                if (available < 0) {
                    close.offer(CLOSE);
                    throw new RuntimeException("FAILED: available < 0");
                }
            } while (true);
        });

        try {
            availableThread.join();
            closeThread.join();
        } catch (InterruptedException ignored) {
        }
    }
}
