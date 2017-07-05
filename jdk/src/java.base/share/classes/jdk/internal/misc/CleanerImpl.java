/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

import sun.misc.InnocuousThread;
import sun.misc.ManagedLocalsThread;

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
     * Heads of a CleanableList for each reference type.
     */
    final PhantomCleanable<?> phantomCleanableList;

    final WeakCleanable<?> weakCleanableList;

    final SoftCleanable<?> softCleanableList;

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
        }
    }

    /**
     * Called to get the CleanerImpl for a Cleaner.
     * @param cleaner the cleaner
     * @return the corresponding CleanerImpl
     */
    private static CleanerImpl getCleanerImpl(Cleaner cleaner) {
        return cleanerImplAccess.apply(cleaner);
    }

    /**
     * Constructor for CleanerImpl.
     */
    public CleanerImpl() {
        queue = new ReferenceQueue<>();
        phantomCleanableList = new PhantomCleanableRef(this);
        weakCleanableList = new WeakCleanableRef(this);
        softCleanableList = new SoftCleanableRef(this);
    }

    /**
     * Starts the Cleaner implementation.
     * When started waits for Cleanables to be queued.
     * @param service the cleaner
     * @param threadFactory the thread factory
     */
    public void start(Cleaner service, ThreadFactory threadFactory) {
        // schedule a nop cleaning action for the service, so the associated thread
        // will continue to run at least until the service is reclaimable.
        new PhantomCleanableRef(service, service, () -> {});

        if (threadFactory == null) {
            threadFactory = CleanerImpl.InnocuousThreadFactory.factory();
        }

        // now that there's at least one cleaning action, for the service,
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
    public void run() {
        Thread t = Thread.currentThread();
        ManagedLocalsThread mlThread = (t instanceof ManagedLocalsThread)
                ? (ManagedLocalsThread) t
                : null;
        while (!phantomCleanableList.isListEmpty() ||
                !weakCleanableList.isListEmpty() ||
                !softCleanableList.isListEmpty()) {
            if (mlThread != null) {
                // Clear the thread locals
                mlThread.eraseThreadLocals();
            }
            try {
                // Wait for a Ref, with a timeout to avoid getting hung
                // due to a race with clear/clean
                Cleanable ref = (Cleanable) queue.remove(60 * 1000L);
                if (ref != null) {
                    ref.clean();
                }
            } catch (InterruptedException i) {
                continue;   // ignore the interruption
            } catch (Throwable e) {
                // ignore exceptions from the cleanup action
            }
        }
    }

    /**
     * PhantomCleanable subclasses efficiently encapsulate cleanup state and
     * the cleaning action.
     * Subclasses implement the abstract {@link #performCleanup()}  method
     * to provide the cleaning action.
     * When constructed, the object reference and the {@link Cleanable Cleanable}
     * are registered with the {@link Cleaner}.
     * The Cleaner invokes {@link Cleaner.Cleanable#clean() clean} after the
     * referent becomes phantom reachable.
     */
    public static abstract class PhantomCleanable<T> extends PhantomReference<T>
            implements Cleaner.Cleanable {

        /**
         * Links to previous and next in a doubly-linked list.
         */
        PhantomCleanable<?> prev = this, next = this;

        /**
         * The CleanerImpl for this Cleanable.
         */
        private final CleanerImpl cleanerImpl;

        /**
         * Constructs new {@code PhantomCleanable} with
         * {@code non-null referent} and {@code non-null cleaner}.
         * The {@code cleaner} is not retained; it is only used to
         * register the newly constructed {@link Cleaner.Cleanable Cleanable}.
         *
         * @param referent the referent to track
         * @param cleaner  the {@code Cleaner} to register with
         */
        public PhantomCleanable(T referent, Cleaner cleaner) {
            super(Objects.requireNonNull(referent), getCleanerImpl(cleaner).queue);
            this.cleanerImpl = getCleanerImpl(cleaner);
            insert();

            // TODO: Replace getClass() with ReachabilityFence when it is available
            cleaner.getClass();
            referent.getClass();
        }

        /**
         * Construct a new root of the list; not inserted.
         */
        PhantomCleanable(CleanerImpl cleanerImpl) {
            super(null, null);
            this.cleanerImpl = cleanerImpl;
        }

        /**
         * Insert this PhantomCleanable after the list head.
         */
        private void insert() {
            final PhantomCleanable<?> list = cleanerImpl.phantomCleanableList;
            synchronized (list) {
                prev = list;
                next = list.next;
                next.prev = this;
                list.next = this;
            }
        }

        /**
         * Remove this PhantomCleanable from the list.
         *
         * @return true if Cleanable was removed or false if not because
         * it had already been removed before
         */
        private boolean remove() {
            PhantomCleanable<?> list = cleanerImpl.phantomCleanableList;
            synchronized (list) {
                if (next != this) {
                    next.prev = prev;
                    prev.next = next;
                    prev = this;
                    next = this;
                    return true;
                }
                return false;
            }
        }

        /**
         * Returns true if the list's next reference refers to itself.
         *
         * @return true if the list is empty
         */
        boolean isListEmpty() {
            PhantomCleanable<?> list = cleanerImpl.phantomCleanableList;
            synchronized (list) {
                return list == list.next;
            }
        }

        /**
         * Unregister this PhantomCleanable and invoke {@link #performCleanup()},
         * ensuring at-most-once semantics.
         */
        @Override
        public final void clean() {
            if (remove()) {
                super.clear();
                performCleanup();
            }
        }

        /**
         * Unregister this PhantomCleanable and clear the reference.
         * Due to inherent concurrency, {@link #performCleanup()} may still be invoked.
         */
        @Override
        public void clear() {
            if (remove()) {
                super.clear();
            }
        }

        /**
         * The {@code performCleanup} abstract method is overridden
         * to implement the cleaning logic.
         * The {@code performCleanup} method should not be called except
         * by the {@link #clean} method which ensures at most once semantics.
         */
        protected abstract void performCleanup();

        /**
         * This method always throws {@link UnsupportedOperationException}.
         * Enqueuing details of {@link Cleaner.Cleanable}
         * are a private implementation detail.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public final boolean isEnqueued() {
            throw new UnsupportedOperationException("isEnqueued");
        }

        /**
         * This method always throws {@link UnsupportedOperationException}.
         * Enqueuing details of {@link Cleaner.Cleanable}
         * are a private implementation detail.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public final boolean enqueue() {
            throw new UnsupportedOperationException("enqueue");
        }
    }

    /**
     * WeakCleanable subclasses efficiently encapsulate cleanup state and
     * the cleaning action.
     * Subclasses implement the abstract {@link #performCleanup()}  method
     * to provide the cleaning action.
     * When constructed, the object reference and the {@link Cleanable Cleanable}
     * are registered with the {@link Cleaner}.
     * The Cleaner invokes {@link Cleaner.Cleanable#clean() clean} after the
     * referent becomes weakly reachable.
     */
    public static abstract class WeakCleanable<T> extends WeakReference<T>
            implements Cleaner.Cleanable {

        /**
         * Links to previous and next in a doubly-linked list.
         */
        WeakCleanable<?> prev = this, next = this;

        /**
         * The CleanerImpl for this Cleanable.
         */
        private final CleanerImpl cleanerImpl;

        /**
         * Constructs new {@code WeakCleanableReference} with
         * {@code non-null referent} and {@code non-null cleaner}.
         * The {@code cleaner} is not retained by this reference; it is only used
         * to register the newly constructed {@link Cleaner.Cleanable Cleanable}.
         *
         * @param referent the referent to track
         * @param cleaner  the {@code Cleaner} to register new reference with
         */
        public WeakCleanable(T referent, Cleaner cleaner) {
            super(Objects.requireNonNull(referent), getCleanerImpl(cleaner).queue);
            cleanerImpl = getCleanerImpl(cleaner);
            insert();

            // TODO: Replace getClass() with ReachabilityFence when it is available
            cleaner.getClass();
            referent.getClass();
        }

        /**
         * Construct a new root of the list; not inserted.
         */
        WeakCleanable(CleanerImpl cleanerImpl) {
            super(null, null);
            this.cleanerImpl = cleanerImpl;
        }

        /**
         * Insert this WeakCleanableReference after the list head.
         */
        private void insert() {
            final WeakCleanable<?> list = cleanerImpl.weakCleanableList;
            synchronized (list) {
                prev = list;
                next = list.next;
                next.prev = this;
                list.next = this;
            }
        }

        /**
         * Remove this WeakCleanableReference from the list.
         *
         * @return true if Cleanable was removed or false if not because
         * it had already been removed before
         */
        private boolean remove() {
            WeakCleanable<?> list = cleanerImpl.weakCleanableList;
            synchronized (list) {
                if (next != this) {
                    next.prev = prev;
                    prev.next = next;
                    prev = this;
                    next = this;
                    return true;
                }
                return false;
            }
        }

        /**
         * Returns true if the list's next reference refers to itself.
         *
         * @return true if the list is empty
         */
        boolean isListEmpty() {
            WeakCleanable<?> list = cleanerImpl.weakCleanableList;
            synchronized (list) {
                return list == list.next;
            }
        }

        /**
         * Unregister this WeakCleanable reference and invoke {@link #performCleanup()},
         * ensuring at-most-once semantics.
         */
        @Override
        public final void clean() {
            if (remove()) {
                super.clear();
                performCleanup();
            }
        }

        /**
         * Unregister this WeakCleanable and clear the reference.
         * Due to inherent concurrency, {@link #performCleanup()} may still be invoked.
         */
        @Override
        public void clear() {
            if (remove()) {
                super.clear();
            }
        }

        /**
         * The {@code performCleanup} abstract method is overridden
         * to implement the cleaning logic.
         * The {@code performCleanup} method should not be called except
         * by the {@link #clean} method which ensures at most once semantics.
         */
        protected abstract void performCleanup();

        /**
         * This method always throws {@link UnsupportedOperationException}.
         * Enqueuing details of {@link java.lang.ref.Cleaner.Cleanable}
         * are a private implementation detail.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public final boolean isEnqueued() {
            throw new UnsupportedOperationException("isEnqueued");
        }

        /**
         * This method always throws {@link UnsupportedOperationException}.
         * Enqueuing details of {@link java.lang.ref.Cleaner.Cleanable}
         * are a private implementation detail.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public final boolean enqueue() {
            throw new UnsupportedOperationException("enqueue");
        }
    }

    /**
     * SoftCleanable subclasses efficiently encapsulate cleanup state and
     * the cleaning action.
     * Subclasses implement the abstract {@link #performCleanup()}  method
     * to provide the cleaning action.
     * When constructed, the object reference and the {@link Cleanable Cleanable}
     * are registered with the {@link Cleaner}.
     * The Cleaner invokes {@link Cleaner.Cleanable#clean() clean} after the
     * referent becomes softly reachable.
     */
    public static abstract class SoftCleanable<T> extends SoftReference<T>
            implements Cleaner.Cleanable {

        /**
         * Links to previous and next in a doubly-linked list.
         */
        SoftCleanable<?> prev = this, next = this;

        /**
         * The CleanerImpl for this Cleanable.
         */
        private final CleanerImpl cleanerImpl;

        /**
         * Constructs new {@code SoftCleanableReference} with
         * {@code non-null referent} and {@code non-null cleaner}.
         * The {@code cleaner} is not retained by this reference; it is only used
         * to register the newly constructed {@link Cleaner.Cleanable Cleanable}.
         *
         * @param referent the referent to track
         * @param cleaner  the {@code Cleaner} to register with
         */
        public SoftCleanable(T referent, Cleaner cleaner) {
            super(Objects.requireNonNull(referent), getCleanerImpl(cleaner).queue);
            cleanerImpl = getCleanerImpl(cleaner);
            insert();

            // TODO: Replace getClass() with ReachabilityFence when it is available
            cleaner.getClass();
            referent.getClass();
        }

        /**
         * Construct a new root of the list; not inserted.
         */
        SoftCleanable(CleanerImpl cleanerImpl) {
            super(null, null);
            this.cleanerImpl = cleanerImpl;
        }

        /**
         * Insert this SoftCleanableReference after the list head.
         */
        private void insert() {
            final SoftCleanable<?> list = cleanerImpl.softCleanableList;
            synchronized (list) {
                prev = list;
                next = list.next;
                next.prev = this;
                list.next = this;
            }
        }

        /**
         * Remove this SoftCleanableReference from the list.
         *
         * @return true if Cleanable was removed or false if not because
         * it had already been removed before
         */
        private boolean remove() {
            SoftCleanable<?> list = cleanerImpl.softCleanableList;
            synchronized (list) {
                if (next != this) {
                    next.prev = prev;
                    prev.next = next;
                    prev = this;
                    next = this;
                    return true;
                }
                return false;
            }
        }

        /**
         * Returns true if the list's next reference refers to itself.
         *
         * @return true if the list is empty
         */
        boolean isListEmpty() {
            SoftCleanable<?> list = cleanerImpl.softCleanableList;
            synchronized (list) {
                return list == list.next;
            }
        }

        /**
         * Unregister this SoftCleanable reference and invoke {@link #performCleanup()},
         * ensuring at-most-once semantics.
         */
        @Override
        public final void clean() {
            if (remove()) {
                super.clear();
                performCleanup();
            }
        }

        /**
         * Unregister this SoftCleanable and clear the reference.
         * Due to inherent concurrency, {@link #performCleanup()} may still be invoked.
         */
        @Override
        public void clear() {
            if (remove()) {
                super.clear();
            }
        }

        /**
         * The {@code performCleanup} abstract method is overridden
         * to implement the cleaning logic.
         * The {@code performCleanup} method should not be called except
         * by the {@link #clean} method which ensures at most once semantics.
         */
        protected abstract void performCleanup();

        /**
         * This method always throws {@link UnsupportedOperationException}.
         * Enqueuing details of {@link Cleaner.Cleanable}
         * are a private implementation detail.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public final boolean isEnqueued() {
            throw new UnsupportedOperationException("isEnqueued");
        }

        /**
         * This method always throws {@link UnsupportedOperationException}.
         * Enqueuing details of {@link Cleaner.Cleanable}
         * are a private implementation detail.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public final boolean enqueue() {
            throw new UnsupportedOperationException("enqueue");
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

        /**
         * Constructor used only for root of phantom cleanable list.
         * @param cleanerImpl  the cleanerImpl
         */
        PhantomCleanableRef(CleanerImpl cleanerImpl) {
            super(cleanerImpl);
            this.action = null;
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
     * Perform cleaning on an unreachable WeakReference.
     */
    public static final class WeakCleanableRef extends WeakCleanable<Object> {
        private final Runnable action;

        /**
         * Constructor for a weak cleanable reference.
         * @param obj the object to monitor
         * @param cleaner the cleaner
         * @param action the action Runnable
         */
        WeakCleanableRef(Object obj, Cleaner cleaner, Runnable action) {
            super(obj, cleaner);
            this.action = action;
        }

        /**
         * Constructor used only for root of weak cleanable list.
         * @param cleanerImpl  the cleanerImpl
         */
        WeakCleanableRef(CleanerImpl cleanerImpl) {
            super(cleanerImpl);
            this.action = null;
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
     * Perform cleaning on an unreachable SoftReference.
     */
    public static final class SoftCleanableRef extends SoftCleanable<Object> {
        private final Runnable action;

        /**
         * Constructor for a soft cleanable reference.
         * @param obj the object to monitor
         * @param cleaner the cleaner
         * @param action the action Runnable
         */
        SoftCleanableRef(Object obj, Cleaner cleaner, Runnable action) {
            super(obj, cleaner);
            this.action = action;
        }

        /**
         * Constructor used only for root of soft cleanable list.
         * @param cleanerImpl  the cleanerImpl
         */
        SoftCleanableRef(CleanerImpl cleanerImpl) {
            super(cleanerImpl);
            this.action = null;
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
        final static ThreadFactory factory = new InnocuousThreadFactory();

        static ThreadFactory factory() {
            return factory;
        }

        public Thread newThread(Runnable r) {
            return AccessController.doPrivileged((PrivilegedAction<Thread>) () -> {
                Thread t = new InnocuousThread(r);
                t.setPriority(Thread.MAX_PRIORITY - 2);
                t.setName("Cleaner-" + t.getId());
                return t;
            });
        }
    }

}

