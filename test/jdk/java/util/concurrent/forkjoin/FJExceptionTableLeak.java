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
 * @bug 8004138 8205576
 * @modules java.base/java.util.concurrent:open
 * @run testng FJExceptionTableLeak
 * @summary Checks that ForkJoinTask thrown exceptions are not leaked.
 * This whitebox test is sensitive to forkjoin implementation details.
 */

import static org.testng.Assert.*;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

@Test
public class FJExceptionTableLeak {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final VarHandle NEXT, EX;
    final Object[] exceptionTable;
    final ReentrantLock exceptionTableLock;

    FJExceptionTableLeak() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
            ForkJoinTask.class, MethodHandles.lookup());
        Class<?> nodeClass = Class.forName(
            ForkJoinTask.class.getName() + "$ExceptionNode");
        VarHandle exceptionTableHandle = lookup.findStaticVarHandle(
            ForkJoinTask.class, "exceptionTable", arrayClass(nodeClass));
        VarHandle exceptionTableLockHandle = lookup.findStaticVarHandle(
            ForkJoinTask.class, "exceptionTableLock", ReentrantLock.class);
        exceptionTable = (Object[]) exceptionTableHandle.get();
        exceptionTableLock = (ReentrantLock) exceptionTableLockHandle.get();

        NEXT = lookup.findVarHandle(nodeClass, "next", nodeClass);
        EX = lookup.findVarHandle(nodeClass, "ex", Throwable.class);
    }

    static <T> Class<T[]> arrayClass(Class<T> klazz) {
        try {
            return (Class<T[]>) Class.forName("[L" + klazz.getName() + ";");
        } catch (ReflectiveOperationException ex) {
            throw new Error(ex);
        }
    }

    Object next(Object node) { return NEXT.get(node); }
    Throwable ex(Object node) { return (Throwable) EX.get(node); }

    static class FailingTaskException extends RuntimeException {}
    static class FailingTask extends RecursiveAction {
        public void compute() { throw new FailingTaskException(); }
    }

    /** Counts all FailingTaskExceptions still recorded in exceptionTable. */
    int retainedExceptions() {
        exceptionTableLock.lock();
        try {
            int count = 0;
            for (Object node : exceptionTable)
                for (; node != null; node = next(node))
                    if (ex(node) instanceof FailingTaskException)
                        count++;
            return count;
        } finally {
            exceptionTableLock.unlock();
        }
    }

    @Test
    public void exceptionTableCleanup() throws Exception {
        ArrayList<FailingTask> failedTasks = failedTasks();

        // Retain a strong ref to one last failing task
        FailingTask lastTask = failedTasks.get(rnd.nextInt(failedTasks.size()));

        // Clear all other strong refs, making exception table cleanable
        failedTasks.clear();

        BooleanSupplier exceptionTableIsClean = () -> {
            try {
                // Trigger exception table expunging as side effect
                lastTask.join();
                throw new AssertionError("should throw");
            } catch (FailingTaskException expected) {}
            int count = retainedExceptions();
            if (count == 0)
                throw new AssertionError("expected to find last task");
            return count == 1;
        };
        gcAwait(exceptionTableIsClean);
    }

    /** Sequestered into a separate method to inhibit GC retention. */
    ArrayList<FailingTask> failedTasks()
        throws Exception {
        final ForkJoinPool pool = new ForkJoinPool(rnd.nextInt(1, 4));

        assertEquals(0, retainedExceptions());

        final ArrayList<FailingTask> tasks = new ArrayList<>();

        for (int i = exceptionTable.length; i--> 0; ) {
            FailingTask task = new FailingTask();
            pool.execute(task);
            tasks.add(task); // retain strong refs to all tasks, for now
            task = null;     // excessive GC retention paranoia
        }
        for (FailingTask task : tasks) {
            try {
                task.join();
                throw new AssertionError("should throw");
            } catch (FailingTaskException success) {}
            task = null;     // excessive GC retention paranoia
        }

        if (rnd.nextBoolean())
            gcAwait(() -> retainedExceptions() == tasks.size());

        return tasks;
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
