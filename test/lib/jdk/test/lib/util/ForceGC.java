/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.util;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.function.BooleanSupplier;

/**
 * Utility class to invoke System.gc()
 */
public class ForceGC {
    /**
     * Causes the current thread to wait until the {@code booleanSupplier}
     * returns true, or a predefined waiting time (10 seconds) elapses.
     *
     * @param booleanSupplier boolean supplier
     * @return true if the {@code booleanSupplier} returns true, or false 
     *     if did not complete after 10 Seconds
     */
    public static boolean wait(BooleanSupplier booleanSupplier) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object obj = new Object();
        PhantomReference<Object> ref = new PhantomReference<>(obj, queue);
        obj = null;
        Reference.reachabilityFence(obj);
        Reference.reachabilityFence(ref);
        System.gc();

        for (int retries = 100; retries > 0; retries--) {
            if (booleanSupplier.getAsBoolean()) {
                return true;
            }

            try {
                // The remove() will always block for the specified milliseconds
                // if the reference has already been removed from the queue.
                // But it is fine.  For most cases, the 1st GC is sufficient
                // to trigger and complete the cleanup.
                queue.remove(100L);
            } catch (InterruptedException ie) {
                // ignore, the loop will try again
            }
            System.gc();
        }

        return false;
    }
}

