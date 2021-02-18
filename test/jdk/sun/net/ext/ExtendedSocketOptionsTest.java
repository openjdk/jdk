/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @test
 * @bug 8260366
 * @summary Verify that concurrent classloading of sun.net.ext.ExtendedSocketOptions and
 * jdk.net.ExtendedSocketOptions doesn't lead to a deadlock
 * @modules java.base/sun.net.ext:open
 *          jdk.net
 * @run testng/othervm ExtendedSocketOptionsTest
 * @run testng/othervm ExtendedSocketOptionsTest
 * @run testng/othervm ExtendedSocketOptionsTest
 * @run testng/othervm ExtendedSocketOptionsTest
 * @run testng/othervm ExtendedSocketOptionsTest
 */
public class ExtendedSocketOptionsTest {

    /**
     * Loads {@code jdk.net.ExtendedSocketOptions} and {@code sun.net.ext.ExtendedSocketOptions}
     * concurrently in a thread of their own and expects the classloading of both those classes
     * to succeed. Additionally, after the classloading is successfully done, calls the
     * sun.net.ext.ExtendedSocketOptions#getInstance() and expects it to return a registered
     * ExtendedSocketOptions instance.
     */
    @Test
    public void testConcurrentClassLoad() throws Exception {
        final CountDownLatch classLoadingTriggerLatch = new CountDownLatch(2);
        final Callable<Class<?>> task1 = new Task("jdk.net.ExtendedSocketOptions", classLoadingTriggerLatch);
        final Callable<Class<?>> task2 = new Task("sun.net.ext.ExtendedSocketOptions", classLoadingTriggerLatch);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<Class<?>>[] results = new Future[2];
            // submit
            for (int i = 0; i < 2; i++) {
                results[i] = executor.submit(i == 0 ? task1 : task2);
            }
            // wait for completion
            for (int i = 0; i < 2; i++) {
                final Class<?> k = results[i].get();
                System.out.println("Completed loading " + k.getName());
            }
        } finally {
            executor.shutdownNow();
        }
        // check that the sun.net.ext.ExtendedSocketOptions#getInstance() does indeed return
        // the registered instance
        final Class<?> k = Class.forName("sun.net.ext.ExtendedSocketOptions");
        final Object extSocketOptions = k.getDeclaredMethod("getInstance").invoke(null);
        Assert.assertNotNull(extSocketOptions, "sun.net.ext.ExtendedSocketOptions#getInstance()" +
                " unexpectedly returned null");
    }

    private static class Task implements Callable<Class<?>> {
        private final String className;
        private final CountDownLatch classLoadingTriggerLatch;

        private Task(final String className, final CountDownLatch classLoadingTriggerLatch) {
            this.className = className;
            this.classLoadingTriggerLatch = classLoadingTriggerLatch;
        }

        public Class<?> call() {
            System.out.println(Thread.currentThread().getName() + " loading " + this.className);
            try {
                // let the other task know we are ready to load the class
                classLoadingTriggerLatch.countDown();
                // wait for the other task to let us know it's ready too, to load the class
                classLoadingTriggerLatch.await();
                return Class.forName(this.className);
            } catch (Exception e) {
                System.err.println("Failed to load " + this.className);
                throw new RuntimeException(e);
            }
        }
    }
}