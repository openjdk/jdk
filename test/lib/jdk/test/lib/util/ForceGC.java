/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
     * returns true, or the waiting time elapses.  The waiting time
     * is 1 second scaled with the jtreg testing timeout factor. This method
     * is equivalent to calling {@link #waitFor(BooleanSupplier, long)
     * waitFor(booleanSupplier, Math.round(1000L * JTREG_TIMEOUT_FACTOR)}
     * where {@code JTREG_TIMEOUT_FACTOR} is the value of
     * "test.timeout.factor" system property.
     *
     * @apiNote If the given {@code booleanSupplier} is expected to never
     * return true, for example to check if an object that is expected
     * to be strongly reachable is still alive,
     * {@link #waitFor(BooleanSupplier, long)} can be used to specify
     * the timeout for the wait method to return.
     *
     * @param booleanSupplier boolean supplier
     * @return true if the {@code booleanSupplier} returns true, or false
     *     if did not complete after the waiting time.

     */
    public static boolean wait(BooleanSupplier booleanSupplier) {
        return waitFor(booleanSupplier, Math.round(1000L * TIMEOUT_FACTOR));
    }

    /**
     * Causes the current thread to wait until the {@code booleanSupplier}
     * returns true, or the specified waiting time elapses.
     *
     * @apiNote If the given {@code booleanSupplier} is expected to never
     * return true, for example to check if an object that is expected
     * to be strongly reachable is still alive, this method can be used
     * to specify the timeout independent of the jtreg timeout factor.
     *
     * @param booleanSupplier boolean supplier
     * @param timeout the maximum time to wait, in milliseconds
     * @return true if the {@code booleanSupplier} returns true, or false
     *     if did not complete after the specified waiting time.
     */
    public static boolean waitFor(BooleanSupplier booleanSupplier, long timeout) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object obj = new Object();
        PhantomReference<Object> ref = new PhantomReference<>(obj, queue);
        obj = null;
        Reference.reachabilityFence(obj);
        Reference.reachabilityFence(ref);

        int retries = (int)(timeout / 200);
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

