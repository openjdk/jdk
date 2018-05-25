/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @bug 8004138
 * @modules java.base/java.util.concurrent:open
 * @summary Checks that ForkJoinTask thrown exceptions are not leaked.
 * This whitebox test is sensitive to forkjoin implementation details.
 */

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.BooleanSupplier;

public class FJExceptionTableLeak {
    static class FailingTaskException extends RuntimeException {}
    static class FailingTask extends RecursiveAction {
        public void compute() { throw new FailingTaskException(); }
    }

    static int bucketsInuse(Object[] exceptionTable) {
        int count = 0;
        for (Object x : exceptionTable)
            if (x != null) count++;
        return count;
    }

    public static void main(String[] args) throws Exception {
        final ForkJoinPool pool = new ForkJoinPool(4);
        final Field exceptionTableField =
            ForkJoinTask.class.getDeclaredField("exceptionTable");
        exceptionTableField.setAccessible(true);
        final Object[] exceptionTable = (Object[]) exceptionTableField.get(null);

        if (bucketsInuse(exceptionTable) != 0) throw new AssertionError();

        final ArrayList<FailingTask> tasks = new ArrayList<>();

        // Keep submitting failing tasks until most of the exception
        // table buckets are in use
        do {
            for (int i = 0; i < exceptionTable.length; i++) {
                FailingTask task = new FailingTask();
                pool.execute(task);
                tasks.add(task); // retain strong refs to all tasks, for now
            }
            for (FailingTask task : tasks) {
                try {
                    task.join();
                    throw new AssertionError("should throw");
                } catch (FailingTaskException success) {}
            }
        } while (bucketsInuse(exceptionTable) < exceptionTable.length * 3 / 4);

        // Retain a strong ref to one last failing task;
        // task.join() will trigger exception table expunging.
        FailingTask lastTask = tasks.get(0);

        // Clear all other strong refs, making exception table cleanable
        tasks.clear();

        BooleanSupplier exceptionTableIsClean = () -> {
            try {
                lastTask.join();
                throw new AssertionError("should throw");
            } catch (FailingTaskException expected) {}
            int count = bucketsInuse(exceptionTable);
            if (count == 0)
                throw new AssertionError("expected to find last task");
            return count == 1;
        };
        gcAwait(exceptionTableIsClean);
    }

    // --------------- GC finalization infrastructure ---------------

    /** No guarantees, but effective in practice. */
    static void forceFullGc() {
        long timeoutMillis = 1000L;
        CountDownLatch finalized = new CountDownLatch(1);
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        WeakReference<Object> ref = new WeakReference<>(
            new Object() { protected void finalize() { finalized.countDown(); }},
            queue);
        try {
            for (int tries = 3; tries--> 0; ) {
                System.gc();
                if (finalized.await(timeoutMillis, MILLISECONDS)
                    && queue.remove(timeoutMillis) != null
                    && ref.get() == null) {
                    System.runFinalization(); // try to pick up stragglers
                    return;
                }
                timeoutMillis *= 4;
            }
        } catch (InterruptedException unexpected) {
            throw new AssertionError("unexpected InterruptedException");
        }
        throw new AssertionError("failed to do a \"full\" gc");
    }

    static void gcAwait(BooleanSupplier s) {
        for (int i = 0; i < 10; i++) {
            if (s.getAsBoolean())
                return;
            forceFullGc();
        }
        throw new AssertionError("failed to satisfy condition");
    }
}
