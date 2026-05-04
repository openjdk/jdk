/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.ref;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.ReferenceQueue;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import jdk.internal.misc.InnocuousThread;

/**
 * CleanerImpl manages a set of object references and corresponding cleaning actions.
 * CleanerImpl provides the functionality of {@link java.lang.ref.Cleaner}.
 */
public final class CleanerImpl implements Runnable {

    /**
     * An object to access the CleanerImpl from a Cleaner; set by Cleaner init.
     */
    private static Function<Cleaner, CleanerImpl> cleanerImplAccess = null;

    /**
     * Currently active PhantomCleanable-s.
     */
    final CleanableList activeList;

    // The ReferenceQueue of pending cleaning actions
    final ReferenceQueue<Object> queue;

    /**
     * Called by Cleaner static initialization to provide the function
     * to map from Cleaner to CleanerImpl.
     * @param access a function to map from Cleaner to CleanerImpl
     */
    public static void setCleanerImplAccess(Function<Cleaner, CleanerImpl> access) {
        if (cleanerImplAccess == null) {
            cleanerImplAccess = access;
        } else {
            throw new InternalError("cleanerImplAccess");
        }
    }

    /**
     * Called to get the CleanerImpl for a Cleaner.
     * @param cleaner the cleaner
     * @return the corresponding CleanerImpl
     */
    static CleanerImpl getCleanerImpl(Cleaner cleaner) {
        return cleanerImplAccess.apply(cleaner);
    }

    /**
     * Constructor for CleanerImpl.
     */
    public CleanerImpl() {
        queue = new ReferenceQueue<>();
        activeList = new CleanableList();
    }

    /**
     * Starts the Cleaner implementation.
     * Ensure this is the CleanerImpl for the Cleaner.
     * When started waits for Cleanables to be queued.
     * @param cleaner the cleaner
     * @param threadFactory the thread factory
     */
    public void start(Cleaner cleaner, ThreadFactory threadFactory) {
        if (getCleanerImpl(cleaner) != this) {
            throw new AssertionError("wrong cleaner");
        }
        // schedule a nop cleaning action for the cleaner, so the associated thread
        // will continue to run at least until the cleaner is reclaimable.
        new CleanerCleanable(cleaner);

        if (threadFactory == null) {
            threadFactory = CleanerImpl.InnocuousThreadFactory.factory();
        }

        // now that there's at least one cleaning action, for the cleaner,
        // we can start the associated thread, which runs until
        // all cleaning actions have been run.
        Thread thread = threadFactory.newThread(this);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Process queued Cleanables as long as the cleanable lists are not empty.
     * A Cleanable is in one of the lists for each Object and for the Cleaner
     * itself.
     * Terminates when the Cleaner is no longer reachable and
     * has been cleaned and there are no more Cleanable instances
     * for which the object is reachable.
     * <p>
     * If the thread is a ManagedLocalsThread, the threadlocals
     * are erased before each cleanup
     */
    @Override
    public void run() {
        Thread t = Thread.currentThread();
        InnocuousThread mlThread = (t instanceof InnocuousThread)
                ? (InnocuousThread) t
                : null;
        while (!activeList.isEmpty()) {
            if (mlThread != null) {
                // Clear the thread locals
                mlThread.eraseThreadLocals();
            }
            try {
                // Wait for a Ref, with a timeout to avoid a potential hang.
                // The Cleaner may become unreachable and its cleanable run,
                // while there are registered cleanables for other objects.
                // If the application explicitly calls clean() on all remaining
                // Cleanables, there won't be any references enqueued to unblock
                // this.  Using a timeout is simpler than unblocking this by
                // having cleaning of the last registered cleanable enqueue a
                // dummy reference.
                Cleanable ref = (Cleanable) queue.remove(60 * 1000L);
                if (ref != null) {
                    ref.clean();
                }
            } catch (Throwable e) {
                // ignore exceptions from the cleanup action
                // (including interruption of cleanup thread)
            }
        }
    }

    /**
     * Perform cleaning on an unreachable PhantomReference.
     */
    public static final class PhantomCleanableRef extends PhantomCleanable<Object> {
        private final Runnable action;

        /**
         * Constructor for a phantom cleanable reference.
         * @param obj the object to monitor
         * @param cleaner the cleaner
         * @param action the action Runnable
         */
        public PhantomCleanableRef(Object obj, Cleaner cleaner, Runnable action) {
            super(obj, cleaner);
            this.action = action;
        }

        @Override
        protected void performCleanup() {
            action.run();
        }

        /**
         * Prevent access to referent even when it is still alive.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public Object get() {
            throw new UnsupportedOperationException("get");
        }

        /**
         * Direct clearing of the referent is not supported.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public void clear() {
            throw new UnsupportedOperationException("clear");
        }
    }

    /**
     * A ThreadFactory for InnocuousThreads.
     * The factory is a singleton.
     */
    static final class InnocuousThreadFactory implements ThreadFactory {
        static final ThreadFactory factory = new InnocuousThreadFactory();

        static ThreadFactory factory() {
            return factory;
        }

        final AtomicInteger cleanerThreadNumber = new AtomicInteger();

        public Thread newThread(Runnable r) {
            return InnocuousThread.newThread("Cleaner-" + cleanerThreadNumber.getAndIncrement(),
                r, Thread.MAX_PRIORITY - 2);
        }
    }

    /**
     * A PhantomCleanable implementation for tracking the Cleaner itself.
     */
    static final class CleanerCleanable extends PhantomCleanable<Cleaner> {
        CleanerCleanable(Cleaner cleaner) {
            super(cleaner, cleaner);
        }

        @Override
        protected void performCleanup() {
            // no action
        }
    }

    /**
     * A specialized implementation that tracks phantom cleanables.
     */
    static final class CleanableList {
        /**
         * Capacity for a single node in the list.
         * This balances memory overheads vs locality vs GC walking costs.
         */
        static final int NODE_CAPACITY = 4096;

        /**
         * Head node. This is the only node where PhantomCleanables are
         * added to or removed from. This is the only node with variable size,
         * all other nodes linked from the head are always at full capacity.
         */
        private Node head;

        /**
         * Cached node instance to provide better behavior near NODE_CAPACITY
         * threshold: if list size flips around NODE_CAPACITY, it would reuse
         * the cached node instead of wasting and re-allocating a new node all
         * the time.
         */
        private Node cache;

        public CleanableList() {
            reset();
        }

        /**
         * Testing support: reset list to initial state.
         */
        synchronized void reset() {
            this.head = new Node();
        }

        /**
         * Returns true if cleanable list is empty.
         *
         * @return true if the list is empty
         */
        public synchronized boolean isEmpty() {
            // Head node size is zero only when the entire list is empty.
            return head.size == 0;
        }

        /**
         * Insert this PhantomCleanable in the list.
         */
        public synchronized void insert(PhantomCleanable<?> phc) {
            if (head.size == NODE_CAPACITY) {
                // Head node is full, insert new one.
                // If possible, pick a pre-allocated node from cache.
                Node newHead;
                if (cache != null) {
                    newHead = cache;
                    cache = null;
                } else {
                    newHead = new Node();
                }
                newHead.next = head;
                head = newHead;
            }
            assert head.size < NODE_CAPACITY;

            // Put the incoming object in head node and record indexes.
            final int lastIndex = head.size;
            phc.node = head;
            phc.index = lastIndex;
            head.arr[lastIndex] = phc;
            head.size++;
        }

        /**
         * Remove this PhantomCleanable from the list.
         *
         * @return true if Cleanable was removed or false if not because
         * it had already been removed before
         */
        public synchronized boolean remove(PhantomCleanable<?> phc) {
            if (phc.node == null) {
                // Not in the list.
                return false;
            }
            assert phc.node.arr[phc.index] == phc;

            // Replace with another element from the head node, as long
            // as it is not the same element. This keeps all non-head
            // nodes at full capacity.
            final int lastIndex = head.size - 1;
            assert lastIndex >= 0;
            if (head != phc.node || (phc.index != lastIndex)) {
                PhantomCleanable<?> mover = head.arr[lastIndex];
                mover.node = phc.node;
                mover.index = phc.index;
                phc.node.arr[phc.index] = mover;
            }

            // Now we can unlink the removed element.
            phc.node = null;

            // Remove the last element from the head node.
            head.arr[lastIndex] = null;
            head.size--;

            // If head node becomes empty after this, and there are
            // nodes that follow it, replace the head node with another
            // full one. If needed, stash the now free node in cache.
            if (head.size == 0 && head.next != null) {
                Node newHead = head.next;
                if (cache == null) {
                    cache = head;
                    cache.next = null;
                }
                head = newHead;
            }

            return true;
        }

        /**
         * Segment node.
         */
        static class Node {
            // Array of tracked cleanables, and the amount of elements in it.
            final PhantomCleanable<?>[] arr = new PhantomCleanable<?>[NODE_CAPACITY];
            int size;

            // Linked list structure.
            Node next;
        }
    }
}
