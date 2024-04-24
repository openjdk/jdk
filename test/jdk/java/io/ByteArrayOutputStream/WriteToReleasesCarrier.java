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

/*
 * @test
 * @bug 8330748
 * @summary Test ByteArrayOutputStream.writeTo releases carrier thread
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run main WriteToReleasesCarrier
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class WriteToReleasesCarrier {
    public static void main(String[] args) throws Exception {
        byte[] bytes = "Hello".getBytes(StandardCharsets.UTF_8);

        var baos = new ByteArrayOutputStream();
        baos.write(bytes);

        var target = new ParkingOutputStream();

        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            Thread.Builder builder = virtualThreadBuilder(scheduler);
            var started = new CountDownLatch(1);
            var vthread1 = builder.start(() -> {
                started.countDown();
                try {
                    baos.writeTo(target);
                } catch (IOException ioe) { }
            });
            try {
                started.await();
                await(vthread1, Thread.State.WAITING);

                // carrier should be released, use it for another thread
                var executed = new AtomicBoolean();
                var vthread2 = builder.start(() -> {
                    executed.set(true);
                });
                vthread2.join();
                if (!executed.get()) {
                    throw new RuntimeException("Second virtual thread did not run");
                }
            } finally {
                LockSupport.unpark(vthread1);
                vthread1.join();
            }
        }

        if (!Arrays.equals(target.toByteArray(), bytes)) {
            throw new RuntimeException("Expected bytes not written");
        }
    }

    /**
     * Waits for a thread to get to the expected state.
     */
    private static void await(Thread thread, Thread.State expectedState) throws Exception {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    /**
     * An OutputStream that parks when writing.
     */
    static class ParkingOutputStream extends OutputStream {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        @Override
        public void write(int i) {
            LockSupport.park();
            baos.write(i);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            LockSupport.park();
            baos.write(b, off, len);
        }

        byte[] toByteArray() {
            return baos.toByteArray();
        }
    }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     */
    static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) throws Exception {
        Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
        Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
        ctor.setAccessible(true);
        return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
    }
}
