/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
    // The jtreg testing timeout factor.
    private static final double TIMEOUT_FACTOR = Double.valueOf(
            System.getProperty("test.timeout.factor", "1.0"));

    /**
     * Causes the current thread to wait until the {@code booleanSupplier}
     * returns true, or a specific waiting time elapses.  The waiting time
     * is 1 second scaled with the jtreg testing timeout factor.
     *
     * @param booleanSupplier boolean supplier
     * @return true if the {@code booleanSupplier} returns true, or false
     *     if did not complete after the specific waiting time.
     */
    public static boolean wait(BooleanSupplier booleanSupplier) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object obj = new Object();
        PhantomReference<Object> ref = new PhantomReference<>(obj, queue);
        obj = null;
        Reference.reachabilityFence(obj);
        Reference.reachabilityFence(ref);

        int retries = (int)(Math.round(1000L * TIMEOUT_FACTOR) / 200);
        for (; retries >= 0; retries--) {
            if (booleanSupplier.getAsBoolean()) {
                return true;
            }

            System.gc();

            try {
                // The remove() will always block for the specified milliseconds
                // if the reference has already been removed from the queue.
                // But it is fine.  For most cases, the 1st GC is sufficient
                // to trigger and complete the cleanup.
                queue.remove(200L);
            } catch (InterruptedException ie) {
                // ignore, the loop will try again
            }
        }

        return booleanSupplier.getAsBoolean();
    }
}

