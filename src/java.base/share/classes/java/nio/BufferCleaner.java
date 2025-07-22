/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.nio;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Objects;
import sun.nio.Cleaner;

/**
 * BufferCleaner supports PhantomReference-based management of native memory
 * referred to by Direct-XXX-Buffers. Unreferenced DBBs may be garbage
 * collected, deactivating the associated PRefs and making them available for
 * cleanup here.
 *
 * There is a configured limit to the amount of memory that may be allocated
 * by DBBs. When that limit is reached, the allocator may invoke the garbage
 * collector directly to attempt to trigger cleaning here, hopefully
 * permitting the allocation to complete. Only if that doesn't free sufficient
 * memory does the allocation fail.  See java.nio.Bits::reserveMemory() for
 * details.
 *
 * One of the requirements for that approach is having a way to determine that
 * deactivated cleaners have been cleaned. java.lang.ref.Cleaner doesn't
 * provide such a mechanism, and adding such a mechanism to that class to
 * satisfy this unique requirement was deemed undesirable. Instead, this class
 * uses the underlying primitives (PhantomReferences, ReferenceQueues) to
 * provide the functionality needed for DBB management.
 */
class BufferCleaner {
    private static final class PhantomCleaner
        extends PhantomReference<Object>
        implements Cleaner
    {
        private final Runnable action;
        // Position in the CleanerList.
        CleanerList.Node node;
        int index;

        public PhantomCleaner(Object obj, Runnable action) {
            super(obj, queue);
            this.action = action;
        }

        @Override
        public void clean() {
            if (cleanerList.remove(this)) {
                // If being cleaned explicitly by application, rather than via
                // reference processing by BufferCleaner, clear the referent so
                // reference processing is disabled for this object.
                clear();
                try {
                    action.run();
                } catch (Throwable x) {
                    // Long-standing behavior: when cleaning fails, VM exits.
                    if (System.err != null) {
                        new Error("nio Cleaner terminated abnormally", x).printStackTrace();
                    }
                    System.exit(1);
                }
            }
        }
    }

    // Cribbed from jdk.internal.ref.CleanerImpl.
    static final class CleanerList {
        /**
         * Capacity for a single node in the list.
         * This balances memory overheads vs locality vs GC walking costs.
         */
        static final int NODE_CAPACITY = 4096;

        /**
         * Head node. This is the only node where PhantomCleanabls are
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

        public CleanerList() {
            this.head = new Node();
        }

        /**
         * Insert this PhantomCleaner in the list.
         */
        public synchronized void insert(PhantomCleaner phc) {
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
         * Remove this PhantomCleaner from the list.
         *
         * @return true if Cleaner was removed or false if not because
         * it had already been removed before
         */
        public synchronized boolean remove(PhantomCleaner phc) {
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
                PhantomCleaner mover = head.arr[lastIndex];
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
            // Array of tracked cleaners, and the amount of elements in it.
            final PhantomCleaner[] arr = new PhantomCleaner[NODE_CAPACITY];
            int size;

            // Linked list structure.
            Node next;
        }
    }

    private static final class CleaningThread extends Thread {
        public CleaningThread() {}

        @Override
        public void run() {
            while (true) {
                try {
                    Cleaner c = (Cleaner) queue.remove();
                    c.clean();
                } catch (InterruptedException e) {
                    // Ignore InterruptedException in cleaner thread.
                }
            }
        }
    }

    /**
     * Try to do some cleaning. Takes a cleaner from the queue and executes it.
     *
     * @return true if a cleaner was found and executed, false if there
     * weren't any cleaners in the queue.
     */
    public static boolean tryCleaning() {
        Cleaner c = (Cleaner) queue.poll();
        if (c == null) {
            return false;
        } else {
            c.clean();
            return true;
        }
    }

    private static final CleanerList cleanerList = new CleanerList();
    private static final ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
    private static CleaningThread cleaningThread = null;

    private static void startCleaningThreadIfNeeded() {
        synchronized (cleanerList) {
            if (cleaningThread != null) {
                return;
            }
            cleaningThread = new CleaningThread();
        }
        cleaningThread.setDaemon(true);
        cleaningThread.start();
    }

    private BufferCleaner() {}

    /**
     * Construct a new Cleaner for obj, with the associated action.
     *
     * @param obj object to track.
     * @param action cleanup action for obj.
     * @return associated cleaner.
     *
     */
    public static Cleaner register(Object obj, Runnable action) {
        Objects.requireNonNull(obj, "obj");
        Objects.requireNonNull(action, "action");
        startCleaningThreadIfNeeded();
        PhantomCleaner cleaner = new PhantomCleaner(obj, action);
        cleanerList.insert(cleaner);
        Reference.reachabilityFence(obj);
        return cleaner;
    }
}
