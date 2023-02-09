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

/* @test
 * @bug 7174305
 * @summary Verify that Reader returned by Channels::newReader throws
 * IllegalBlockingMode if read from while configured non-blocking
 */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.Pipe;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.channels.Pipe.SourceChannel;
import java.util.Scanner;

/**
 * This test will fail by timing out if no IllegalBlockingMode is thrown.
 */
public class NonBlockingReader {
    public static void main(String[] args) throws IOException {
        Pipe pipe = Pipe.open();
        Pipe.SinkChannel sink = pipe.sink();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    do {
                        sink.write(ByteBuffer.wrap(new byte[] {'A'}));
                        Thread.sleep(1000);
                    } while(true);
                } catch (IOException | InterruptedException e) {
                    throw new AssertionError(e);
                }

            }
        }).start();

        Pipe.SourceChannel source = pipe.source();
        source.configureBlocking(false);

        Scanner scanner = new Scanner(source);
        try {
            while(scanner.hasNextLine()) {
                System.out.println(scanner.nextLine());
            }
            throw new RuntimeException("IllegalBlockingModeException expected");
        } catch (IllegalBlockingModeException expected) {
        }
    }
}
