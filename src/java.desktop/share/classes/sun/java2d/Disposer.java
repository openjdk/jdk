/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedDeque;

import sun.awt.util.ThreadGroupUtils;

/**
 * This class is used for registering and disposing the native
 * data associated with java objects.
 *
 * The object can register itself by calling one of the addRecord
 * methods and providing either the pointer to the native disposal
 * method or a descendant of the DisposerRecord class with overridden
 * dispose() method.
 *
 * When the object becomes unreachable, the dispose() method
 * of the associated DisposerRecord object will be called.
 *
 * @see DisposerRecord
 */
@SuppressWarnings("restricted")
public class Disposer implements Runnable {
    private static final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private static final Hashtable<java.lang.ref.Reference<Object>, DisposerRecord> records =
        new Hashtable<>();

    private static final Disposer disposerInstance;

    static {
        System.loadLibrary("awt");
        initIDs();
        disposerInstance = new Disposer();
        String name = "Java2D Disposer";
        ThreadGroup rootTG = ThreadGroupUtils.getRootThreadGroup();
        Thread t = new Thread(rootTG, disposerInstance, name, 0, false);
        t.setContextClassLoader(null);
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    /**
     * Registers the object and the native data for later disposal.
     * @param target Object to be registered
     * @param disposeMethod pointer to the native disposal method
     * @param pData pointer to the data to be passed to the
     *              native disposal method
     */
    public static void addRecord(Object target,
                                 long disposeMethod, long pData)
    {
        disposerInstance.add(target,
                             new DefaultDisposerRecord(disposeMethod, pData));
    }

    /**
     * Registers the object and the native data for later disposal.
     * @param target Object to be registered
     * @param rec the associated DisposerRecord object
     * @see DisposerRecord
     */
    public static void addRecord(Object target, DisposerRecord rec) {
        disposerInstance.add(target, rec);
    }

    /**
     * Performs the actual registration of the target object to be disposed.
     * @param target Object to be registered, or if target is an instance
     *               of DisposerTarget, its associated disposer referent
     *               will be the Object that is registered
     * @param rec the associated DisposerRecord object
     * @see DisposerRecord
     */
    synchronized void add(Object target, DisposerRecord rec) {
        if (target instanceof DisposerTarget) {
            target = ((DisposerTarget)target).getDisposerReferent();
        }
        records.put(new PhantomReference<>(target, queue), rec);
    }

    public void run() {
        while (true) {
            try {
                Reference<?> obj = queue.remove();
                obj.clear();
                DisposerRecord rec = records.remove(obj);
                safeDispose(rec);
                obj = null;
                rec = null;
                clearDeferredRecords();
            } catch (Throwable t) {
                System.out.println("Exception while removing reference.");
            }
        }
    }

    /*
     * This is a marker interface that, if implemented, means it
     * doesn't acquire any special locks, and is safe to
     * be disposed in the poll loop on whatever thread
     * which happens to be the Toolkit thread, is in use.
     */
    public static interface PollDisposable {
    }

    private static ConcurrentLinkedDeque<DisposerRecord> deferredRecords = new ConcurrentLinkedDeque<>();

    private static void safeDispose(DisposerRecord rec) {
        try {
            rec.dispose();
        } catch (final Throwable t) {
            System.out.println("Exception while disposing deferred rec.");
        }
    }

    private static void clearDeferredRecords() {
        while (!deferredRecords.isEmpty()) {
            final DisposerRecord rec = deferredRecords.pollFirst();
            if (rec != null) {
                safeDispose(rec);
            }
        }
    }

    /*
     * Set to indicate the queue is presently being polled.
     */
    public static volatile boolean pollingQueue;

    /*
     * The pollRemove() method is called back from a dispose method
     * that is running on the toolkit thread and wants to
     * dispose any pending refs that are safe to be disposed
     * on that thread.
     */
    public static void pollRemove() {

        /* This should never be called recursively, so this check
         * is just a safeguard against the unexpected.
         */
        if (pollingQueue) {
            return;
        }
        Reference<?> obj;
        pollingQueue = true;
        int freed = 0;
        int deferred = 0;
        try {
            while ( freed < 10000 && deferred < 100 &&
                    (obj = queue.poll()) != null ) {
                freed++;
                obj.clear();
                DisposerRecord rec = records.remove(obj);
                if (rec instanceof PollDisposable) {
                    safeDispose(rec);
                    obj = null;
                    rec = null;
                } else {
                    if (rec == null) { // shouldn't happen, but just in case.
                        continue;
                    }
                    deferred++;
                    deferredRecords.offerLast(rec);
                }
            }
        } catch (Throwable t) {
            System.out.println("Exception while removing reference.");
        } finally {
            pollingQueue = false;
        }
    }

    private static native void initIDs();

    /*
     * This was added for use by the 2D font implementation to avoid creation
     * of an additional disposer thread.
     * WARNING: this thread class monitors a specific queue, so a reference
     * added here must have been created with this queue. Failure to do
     * so will clutter the records hashmap and no one will be cleaning up
     * the reference queue.
     */
    public static void addReference(Reference<Object> ref, DisposerRecord rec) {
        records.put(ref, rec);
    }

    public static void addObjectRecord(Object obj, DisposerRecord rec) {
        records.put(new WeakReference<>(obj, queue) , rec);
    }

    /* This is intended for use in conjunction with addReference(..)
     */
    public static ReferenceQueue<Object> getQueue() {
        return queue;
    }

}
