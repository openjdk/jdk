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

/*
 * @test
 * @bug 8303742
 * @summary CompletableFuture.orTimeout can leak memory if completed exceptionally
 * @modules java.base/java.util.concurrent:open
 * @run junit/othervm -Xmx128m CompletableFutureOrTimeoutExceptionallyTest
 */

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletableFutureOrTimeoutExceptionallyTest {
    static final BlockingQueue<Runnable> delayerQueue;
    static {
        try {
            var delayerClass = Class.forName("java.util.concurrent.CompletableFuture$Delayer",
                                             true,
                                             CompletableFuture.class.getClassLoader());
            var delayerField = delayerClass.getDeclaredField("delayer");
            delayerField.setAccessible(true);
            delayerQueue = ((ScheduledThreadPoolExecutor)delayerField.get(null)).getQueue();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    /**
     * Test that orTimeout task is cancelled if the CompletableFuture is completed Exceptionally
     */
    @Test
    void testOrTimeoutWithCompleteExceptionallyDoesNotLeak() throws InterruptedException {
        assertTrue(delayerQueue.peek() == null);
        var future = new CompletableFuture<>().orTimeout(12, TimeUnit.HOURS);
        assertTrue(delayerQueue.peek() != null);
        future.completeExceptionally(new RuntimeException("This is fine"));
        while (delayerQueue.peek() != null) {
            Thread.sleep(100);
        };
    }

    /**
     * Test that the completeOnTimeout task is cancelled if the CompletableFuture is completed Exceptionally
     */
    @Test
    void testCompleteOnTimeoutWithCompleteExceptionallyDoesNotLeak() throws InterruptedException {
        assertTrue(delayerQueue.peek() == null);
        var future = new CompletableFuture<>().completeOnTimeout(null, 12, TimeUnit.HOURS);
        assertTrue(delayerQueue.peek() != null);
        future.completeExceptionally(new RuntimeException("This is fine"));
        while (delayerQueue.peek() != null) {
            Thread.sleep(100);
        };
    }
}
