/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4268317 8132306 8175797
 * @summary Test if Reference.enqueue() works properly with GC
 * @run main ReferenceEnqueue
 */

import java.lang.ref.*;
import java.util.ArrayList;
import java.util.List;

public class ReferenceEnqueue {

    public static void main(String args[]) throws Exception {
        for (int i=0; i < 5; i++) {
            new WeakRef().run();
            new ExplicitEnqueue().run();
        }
        System.out.println("Test passed.");
    }

    static class WeakRef {
        final ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
        final Reference<Object> ref;
        final int iterations = 1000;

        WeakRef() {
            this.ref = new WeakReference<Object>(new Object(), queue);
        }

        void run() throws InterruptedException {
            System.gc();
            for (int i = 0; i < iterations; i++) {
                System.gc();
                if (ref.isEnqueued()) {
                    break;
                }

                Thread.sleep(100);
            }

            if (ref.isEnqueued() == false) {
                // GC have not enqueued refWeak for the timeout period
                System.out.println("Reference not enqueued yet");
                return;
            }

            if (ref.enqueue() == true) {
                // enqueue() should return false since
                // ref is already enqueued by the GC
                throw new RuntimeException("Error: enqueue() returned true;"
                        + " expected false");
            }

            if (queue.poll() == null) {
                // poll() should return ref enqueued by the GC
                throw new RuntimeException("Error: poll() returned null;"
                        + " expected ref object");
            }
        }
    }

    static class ExplicitEnqueue {
        final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        final List<Reference<Object>> refs = new ArrayList<>();
        final int iterations = 1000;

        ExplicitEnqueue() {
            this.refs.add(new SoftReference<>(new Object(), queue));
            this.refs.add(new WeakReference<>(new Object(), queue));
            // Can't test PhantomReference because get() always returns null.
        }

        void run() throws InterruptedException {
            for (Reference<Object> ref : refs) {
                if (ref.enqueue() == false) {
                    throw new RuntimeException("Error: enqueue failed");
                }
                if (ref.get() != null) {
                    throw new RuntimeException("Error: referent must be cleared");
                }
            }

            System.gc();
            for (int i = 0; refs.size() > 0 && i < iterations; i++) {
                Reference<Object> ref = (Reference<Object>)queue.poll();
                if (ref == null) {
                    System.gc();
                    Thread.sleep(100);
                    continue;
                }

                if (refs.remove(ref) == false) {
                    throw new RuntimeException("Error: unknown reference " + ref);
                }
            }

            if (!refs.isEmpty()) {
                throw new RuntimeException("Error: not all references are removed");
            }
        }
    }
}
