/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7059085
 * @summary Check that Thread.stop(Throwable) throws UOE
 * @run testng StopThrowable
 */

import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;

public class StopThrowable {

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testStopSelf() {
        Thread.currentThread().stop(new ThreadDeath());
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            // should not happen
            throw new RuntimeException(e);
        }
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testStopOther() throws Throwable {
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread( () ->  { ready.countDown(); awaitUnchecked(done); } );
        t.start();
        try {
            ready.await();
            t.stop(new ThreadDeath());
        } finally {
            done.countDown();
        }
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testNull() {
        Thread.currentThread().stop(null);
    }
}
