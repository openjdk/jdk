/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.ref;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.VM;

final class Finalizer extends FinalReference<Object> { /* Package-private; must be in
                                                          same package as the Reference
                                                          class */

    private static ReferenceQueue<Object> queue = new ReferenceQueue<>();

    /** Head of doubly linked list of Finalizers awaiting finalization. */
    private static Finalizer unfinalized = null;

    /** Lock guarding access to unfinalized list. */
    private static final Object lock = new Object();

    private Finalizer next, prev;

    private Finalizer(Object finalizee) {
        super(finalizee, queue);
        // push onto unfinalized
        synchronized (lock) {
            if (unfinalized != null) {
                this.next = unfinalized;
                unfinalized.prev = this;
            }
            unfinalized = this;
        }
    }

    static ReferenceQueue<Object> getQueue() {
        return queue;
    }

    static final boolean ENABLED = isFinalizationEnabled();

    private static native boolean isFinalizationEnabled();

    /* Invoked by VM */
    static void register(Object finalizee) {
        if (ENABLED) {
            new Finalizer(finalizee);
        } else {
            throw new InternalError("unexpected call to Finalizer::register when finalization is disabled");
        }
    }

    private void runFinalizer(JavaLangAccess jla) {
        synchronized (lock) {
            if (this.next == this)      // already finalized
                return;
            // unlink from unfinalized
            if (unfinalized == this)
                unfinalized = this.next;
            else
                this.prev.next = this.next;
            if (this.next != null)
                this.next.prev = this.prev;
            this.prev = null;
            this.next = this;           // mark as finalized
        }

        try {
            Object finalizee = this.get();
            assert finalizee != null;
            if (!(finalizee instanceof java.lang.Enum)) {
                jla.invokeFinalize(finalizee);
                reportComplete(finalizee);

                // Clear stack slot containing this variable, to decrease
                // the chances of false retention with a conservative GC
                finalizee = null;
            }
        } catch (Throwable x) { }
        super.clear();
    }

    private static native void reportComplete(Object finalizee);

    /* Create a privileged secondary finalizer thread in the system thread
     * group for the given Runnable, and wait for it to complete.
     *
     * This method is used by runFinalization.
     *
     * It could have been implemented by offloading the work to the
     * regular finalizer thread and waiting for that thread to finish.
     * The advantage of creating a fresh thread, however, is that it insulates
     * invokers of that method from a stalled or deadlocked finalizer thread.
     */
    private static void forkSecondaryFinalizer(final Runnable proc) {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent());
        Thread sft = new Thread(tg, proc, "Secondary finalizer", 0, false);
        sft.start();
        try {
            sft.join();
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
    }

    /* Called by Runtime.runFinalization() */
    static void runFinalization() {
        if (VM.initLevel() == 0 || ! ENABLED) {
            return;
        }

        forkSecondaryFinalizer(new Runnable() {
            private volatile boolean running;
            public void run() {
                // in case of recursive call to run()
                if (running)
                    return;
                final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
                running = true;
                for (Finalizer f; (f = (Finalizer)queue.poll()) != null; )
                    f.runFinalizer(jla);
            }
        });
    }

    private static class FinalizerThread extends Thread {
        private volatile boolean running;
        FinalizerThread(ThreadGroup g) {
            super(g, null, "Finalizer", 0, false);
        }
        public void run() {
            // in case of recursive call to run()
            if (running)
                return;

            final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
            running = true;
            for (;;) {
                try {
                    Finalizer f = (Finalizer)queue.remove();
                    f.runFinalizer(jla);
                } catch (InterruptedException x) {
                    // ignore and continue
                }
            }
        }
    }

    /**
     * Start the Finalizer thread as a daemon thread.
     */
    static void startFinalizerThread(ThreadGroup tg) {
        if (ENABLED) {
            Thread finalizer = new FinalizerThread(tg);
            finalizer.setPriority(Thread.MAX_PRIORITY - 2);
            finalizer.setDaemon(true);
            finalizer.start();
        }
    }
}
