/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8236246
 * @modules java.base/sun.nio.ch
 * @run junit InterruptibleOrNot
 * @summary Test SelectorProviderImpl.openDatagramChannel(boolean) to create
 *     DatagramChannel objects that optionally support interrupt
 */

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.Arrays;
import sun.nio.ch.DefaultSelectorProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

public class InterruptibleOrNot {
    // DatagramChannel implementation class
    private static String dcImplClassName;

    @BeforeAll
    static void setup() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            dcImplClassName = dc.getClass().getName();
        }
    }

    /**
     * Call DatagramChannel.receive with the interrupt status set, the DatagramChannel
     * is interruptible.
     */
    @Test
    public void testInterruptBeforeInterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            Thread.currentThread().interrupt();
            assertThrows(ClosedByInterruptException.class, () -> dc.receive(buf));
            assertFalse(dc.isOpen());
        } finally {
            Thread.interrupted();  // clear interrupt status
        }
    }

    /**
     * Test interrupting a thread blocked in DatagramChannel.receive, the DatagramChannel
     * is interruptible.
     */
    @Test
    public void testInterruptDuringInterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            Thread thread = Thread.currentThread();
            onReceive(thread::interrupt);
            assertThrows(ClosedByInterruptException.class, () -> dc.receive(buf));
            assertFalse(dc.isOpen());
        } finally {
            Thread.interrupted();  // clear interrupt status
        }
    }

    /**
     * Call DatagramChannel.receive with the interrupt status set, the DatagramChannel
     * is not interruptible.
     */
    @Test
    public void testInterruptBeforeUninterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(false)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            onReceive(() -> {
                // close the channel after a delay to ensure receive wakes up
                Thread.sleep(1000);
                dc.close();
            });
            Thread.currentThread().interrupt();
            assertThrows(AsynchronousCloseException.class, () -> dc.receive(buf));
            assertFalse(dc.isOpen());
        } finally {
            Thread.interrupted();  // clear interrupt status
        }
    }

    /**
     * Test interrupting a thread blocked in DatagramChannel.receive, the DatagramChannel
     * is not interruptible.
     */
    @Test
    public void testInterruptDuringUninterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);

            Thread thread = Thread.currentThread();
            onReceive(() -> {
                // interrupt should not cause the receive to wakeup
                thread.interrupt();

                // close the channel after a delay to ensure receive wakes up
                Thread.sleep(1000);
                dc.close();
            });
            assertThrows(AsynchronousCloseException.class, () -> dc.receive(buf));
            assertFalse(dc.isOpen());
        } finally {
            Thread.interrupted();  // clear interrupt status
        }
    }

    /**
     * Call DatagramChannel.send with the interrupt status set, the DatagramChannel
     * is interruptible.
     */
    @Test
    public void testInterruptBeforeInterruptibleSend() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            SocketAddress target = dc.getLocalAddress();
            Thread.currentThread().interrupt();
            assertThrows(ClosedByInterruptException.class, () -> dc.send(buf, target));
            assertFalse(dc.isOpen());
        } finally {
            Thread.interrupted();  // clear interrupt
        }
    }

    /**
     * Call DatagramChannel.send with the interrupt status set, the DatagramChannel
     * is not interruptible.
     */
    @Test
    public void testInterruptBeforeUninterruptibleSend() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(false)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            SocketAddress target = dc.getLocalAddress();
            Thread.currentThread().interrupt();
            int n = dc.send(buf, target);
            assertEquals(100, n);
            assertTrue(dc.isOpen());
        } finally {
            Thread.interrupted();  // clear interrupt status
        }
    }

    /**
     * Creates a DatagramChannel that is interruptible or not, and bound to the loopback
     * address.
     */
    static DatagramChannel boundDatagramChannel(boolean interruptible) throws IOException {
        DatagramChannel dc;
        if (interruptible) {
            dc = DatagramChannel.open();
        } else {
            dc = DefaultSelectorProvider.get().openUninterruptibleDatagramChannel();
        }
        try {
            dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        } catch (IOException ioe) {
            dc.close();
            throw ioe;
        }
        return dc;
    }

    /**
     * Runs the given action when the current thread is sampled in DatagramChannel.receive.
     */
    static void onReceive(Executable action) {
        Thread target = Thread.currentThread();
        Thread.ofPlatform().daemon().start(() -> {
            try {
                boolean found = false;
                while (!found) {
                    Thread.sleep(20);
                    StackTraceElement[] stack = target.getStackTrace();
                    found = Arrays.stream(stack)
                            .anyMatch(e -> dcImplClassName.equals(e.getClassName())
                                    && "receive".equals(e.getMethodName()));
                }
                action.execute();
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        });
    }
}
