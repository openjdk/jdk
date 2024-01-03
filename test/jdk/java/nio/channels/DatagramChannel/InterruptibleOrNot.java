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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import sun.nio.ch.DefaultSelectorProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

public class InterruptibleOrNot {

    // used for scheduling thread interrupt or close
    private static ScheduledExecutorService scheduler;

    @BeforeAll
    static void setup() throws Exception {
        ThreadFactory factory = Executors.defaultThreadFactory();
        scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    @AfterAll
    static void finish() {
        scheduler.shutdown();
    }

    @Test
    public void testInterruptBeforeInterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            Thread.currentThread().interrupt();
            assertThrows(ClosedByInterruptException.class, () -> dc.receive(buf));
        } finally {
            Thread.interrupted();  // clear interrupt
        }
    }

    @Test
    public void testInterruptDuringInterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            Future<?> interruptTask = scheduleInterrupt(Thread.currentThread(), Duration.ofSeconds(2));
            try {
                assertThrows(ClosedByInterruptException.class, () -> dc.receive(buf));
            } finally {
                interruptTask.cancel(false);
            }
        } finally {
            Thread.interrupted();  // clear interrupt
        }
    }

    @Test
    public void testInterruptBeforeUninterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(false)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            // give thread enough time to block in receive
            Future<?> closeTask = scheduleClose(dc, Duration.ofSeconds(5));
            try {
                Thread.currentThread().interrupt();
                assertThrows(AsynchronousCloseException.class, () -> dc.receive(buf));
            } finally {
                closeTask.cancel(false);
            }
        } finally {
            Thread.interrupted();  // clear interrupt
        }
    }

    @Test
    public void testInterruptDuringUninterruptibleReceive() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            // the interrupt should not cause the receive to wakeup
            Future<?> interruptTask = scheduleInterrupt(Thread.currentThread(), Duration.ofSeconds(2));
            Future<?> closeTask = scheduleClose(dc, Duration.ofSeconds(5));
            try {
                assertThrows(AsynchronousCloseException.class, () -> dc.receive(buf));
            } finally {
                closeTask.cancel(false);
                interruptTask.cancel(false);
            }
        } finally {
            Thread.interrupted();  // clear interrupt
        }
    }

    @Test
    public void testInterruptBeforeInterruptibleSend() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(true)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            SocketAddress target = dc.getLocalAddress();
            Thread.currentThread().interrupt();
            assertThrows(ClosedByInterruptException.class, () -> dc.send(buf, target));
        } finally {
            Thread.interrupted();  // clear interrupt
        }
    }

    @Test
    public void testInterruptBeforeUninterruptibleSend() throws Exception {
        try (DatagramChannel dc = boundDatagramChannel(false)) {
            ByteBuffer buf = ByteBuffer.allocate(100);
            SocketAddress target = dc.getLocalAddress();
            Thread.currentThread().interrupt();
            int n = dc.send(buf, target);
            assertTrue(n == 100);
        } finally {
            Thread.interrupted();  // clear interrupt
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
     * Schedule the given object to be closed.
     */
    static Future<?> scheduleClose(Closeable c, Duration timeout) {
        long nanos = TimeUnit.NANOSECONDS.convert(timeout);
        return scheduler.schedule(() -> {
            c.close();
            return null;
        }, nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Schedule the given thread to be interrupted.
     */
    static Future<?> scheduleInterrupt(Thread t, Duration timeout) {
        long nanos = TimeUnit.NANOSECONDS.convert(timeout);
        return scheduler.schedule(t::interrupt, nanos, TimeUnit.NANOSECONDS);
    }
}
