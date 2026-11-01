/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.z;

/*
 * @test
 * @bug 8394682
 * @summary Test that ZGC completes metaspace allocations with class unloading disabled
 * @requires vm.gc.Z
 * @library /test/lib
 * @run main/othervm gc.z.TestMetaspaceWithClassUnloadingDisabled
 */

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

public class TestMetaspaceWithClassUnloadingDisabled {
    static class Template {}

    static class Loader extends ClassLoader {
        Loader() {
            super(null);
        }

        void define(byte[] bytes) {
            defineClass(null, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            // Keep the test harness outside the VM that exhausts metaspace.
            ProcessTools.executeLimitedTestJava(
                    "-XX:+UseZGC", "-XX:-ClassUnloading", "-Xms64m", "-Xmx128m",
                    "-XX:MaxMetaspaceSize=16m", "-Xlog:metaspace=info",
                    TestMetaspaceWithClassUnloadingDisabled.class.getName(),
                    Long.toString(Utils.adjustTimeout(60_000)))
                    .shouldHaveExitValue(0)
                    .shouldContain("All workers completed with metaspace OOME");
            return;
        }

        byte[] bytes;
        try (InputStream in = TestMetaspaceWithClassUnloadingDisabled.class.getResourceAsStream(
                "TestMetaspaceWithClassUnloadingDisabled$Template.class")) {
            if (in == null) {
                throw new AssertionError("Template class not found");
            }
            bytes = in.readAllBytes();
        }

        int count = 8;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        long timeout = Long.parseLong(args[0]);

        // Prepare failure reporting before exhausting metaspace.
        AssertionError startupFailure = new AssertionError("Workers did not start");
        AssertionError timeoutFailure = new AssertionError("Workers did not complete");
        startupFailure.getStackTrace();
        timeoutFailure.getStackTrace();

        for (int i = 0; i < count; i++) {
            Thread worker = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (;;) {
                        new Loader().define(bytes);
                    }
                } catch (OutOfMemoryError e) {
                    String message = e.getMessage();
                    if (!"Metaspace".equals(message) && !"Compressed class space".equals(message)) {
                        failure.compareAndSet(null, e);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
            // Allow a failed test to exit even if allocation requests remain blocked.
            worker.setDaemon(true);
            worker.start();
        }

        if (!ready.await(timeout, TimeUnit.MILLISECONDS)) {
            throw startupFailure;
        }
        System.out.println("Starting metaspace allocation workers");
        start.countDown();
        if (!done.await(timeout, TimeUnit.MILLISECONDS)) {
            System.out.println("Workers did not complete");
            throw timeoutFailure;
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        System.out.println("All workers completed with metaspace OOME");
    }
}
