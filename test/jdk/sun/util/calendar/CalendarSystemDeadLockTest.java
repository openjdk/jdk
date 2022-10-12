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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @test
 * @bug 8273790
 * @summary Verify that concurrent classloading of sun.util.calendar.Gregorian and
 * sun.util.calendar.CalendarSystem doesn't lead to a deadlock
 * @modules java.base/sun.util.calendar:open
 * @run main/othervm CalendarSystemDeadLockTest
 * @run main/othervm CalendarSystemDeadLockTest
 * @run main/othervm CalendarSystemDeadLockTest
 * @run main/othervm CalendarSystemDeadLockTest
 * @run main/othervm CalendarSystemDeadLockTest
 */
public class CalendarSystemDeadLockTest {

    public static void main(final String[] args) throws Exception {
        testConcurrentClassLoad();
    }

    /**
     * Loads {@code sun.util.calendar.Gregorian} and {@code sun.util.calendar.CalendarSystem}
     * and invokes {@code sun.util.calendar.CalendarSystem#getGregorianCalendar()} concurrently
     * in a thread of their own and expects the classloading of both those classes
     * to succeed. Additionally, after these tasks are done, calls the
     * sun.util.calendar.CalendarSystem#getGregorianCalendar() and expects it to return a singleton
     * instance
     */
    private static void testConcurrentClassLoad() throws Exception {
        final int numTasks = 7;
        final CountDownLatch taskTriggerLatch = new CountDownLatch(numTasks);
        final List<Callable<?>> tasks = new ArrayList<>();
        // add the sun.util.calendar.Gregorian and sun.util.calendar.CalendarSystem for classloading.
        // there are main 2 classes which had a cyclic call in their static init
        tasks.add(new ClassLoadTask("sun.util.calendar.Gregorian", taskTriggerLatch));
        tasks.add(new ClassLoadTask("sun.util.calendar.CalendarSystem", taskTriggerLatch));
        // add a few other classes for classloading, those which call CalendarSystem#getGregorianCalendar()
        // or CalendarSystem#forName() during their static init
        tasks.add(new ClassLoadTask("java.util.GregorianCalendar", taskTriggerLatch));
        tasks.add(new ClassLoadTask("java.util.Date", taskTriggerLatch));
        tasks.add(new ClassLoadTask("java.util.JapaneseImperialCalendar", taskTriggerLatch));
        // add a couple of tasks which directly invoke sun.util.calendar.CalendarSystem#getGregorianCalendar()
        tasks.add(new GetGregorianCalTask(taskTriggerLatch));
        tasks.add(new GetGregorianCalTask(taskTriggerLatch));
        // before triggering the tests make sure we have created the correct number of tasks
        // the countdown latch uses/expects
        if (numTasks != tasks.size()) {
            throw new RuntimeException("Test setup failure - unexpected number of tasks " + tasks.size()
                    + ", expected " + numTasks);
        }
        final ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            final Future<?>[] results = new Future[tasks.size()];
            // submit
            int i = 0;
            for (final Callable<?> task : tasks) {
                results[i++] = executor.submit(task);
            }
            // wait for completion
            for (i = 0; i < tasks.size(); i++) {
                results[i].get();
            }
        } finally {
            executor.shutdownNow();
        }
        // check that the sun.util.calendar.CalendarSystem#getGregorianCalendar() does indeed return
        // a proper instance
        final Object gCal = callCalSystemGetGregorianCal();
        if (gCal == null) {
            throw new RuntimeException("sun.util.calendar.CalendarSystem#getGregorianCalendar()" +
                    " unexpectedly returned null");
        }
        // now verify that each call to getGregorianCalendar(), either in the tasks or here, returned the exact
        // same instance
        if (GetGregorianCalTask.instances.size() != 2) {
            throw new RuntimeException("Unexpected number of results from call " +
                    "to sun.util.calendar.CalendarSystem#getGregorianCalendar()");
        }
        // intentional identity check since sun.util.calendar.CalendarSystem#getGregorianCalendar() is
        // expected to return a singleton instance
        if ((gCal != GetGregorianCalTask.instances.get(0)) || (gCal != GetGregorianCalTask.instances.get(1))) {
            throw new RuntimeException("sun.util.calendar.CalendarSystem#getGregorianCalendar()" +
                    " returned different instances");
        }
    }

    /**
     * Reflectively calls sun.util.calendar.CalendarSystem#getGregorianCalendar() and returns
     * the result
     */
    private static Object callCalSystemGetGregorianCal() throws Exception {
        final Class<?> k = Class.forName("sun.util.calendar.CalendarSystem");
        return k.getDeclaredMethod("getGregorianCalendar").invoke(null);
    }

    private static class ClassLoadTask implements Callable<Class<?>> {
        private final String className;
        private final CountDownLatch latch;

        private ClassLoadTask(final String className, final CountDownLatch latch) {
            this.className = className;
            this.latch = latch;
        }

        @Override
        public Class<?> call() {
            System.out.println(Thread.currentThread().getName() + " loading " + this.className);
            try {
                // let the other tasks know we are ready to trigger our work
                latch.countDown();
                // wait for the other task to let us know they are ready to trigger their work too
                latch.await();
                return Class.forName(this.className);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class GetGregorianCalTask implements Callable<Object> {
        // keeps track of the instances returned by calls to sun.util.calendar.CalendarSystem#getGregorianCalendar()
        // by this task
        private static final List<Object> instances = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch latch;

        private GetGregorianCalTask(final CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public Object call() {
            System.out.println(Thread.currentThread().getName()
                    + " calling  sun.util.calendar.CalendarSystem#getGregorianCalendar()");
            try {
                // let the other tasks know we are ready to trigger our work
                latch.countDown();
                // wait for the other task to let us know they are ready to trigger their work too
                latch.await();
                final Object inst = callCalSystemGetGregorianCal();
                instances.add(inst);
                return inst;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
