/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8308235
 * @summary Unreference ExecutorService objects returned by the Executors without shutdown
 *    and termination, this should not leak memory
 * @run main/othervm -Xmx32m UnreferencedExecutor
 */

import java.time.Duration;
import java.util.concurrent.Executors;

public class UnreferencedExecutor {

    private static final int DURATION_IN_SECONDS = 5;

    public static void main(String[] args) throws Exception {
        int ncores = Runtime.getRuntime().availableProcessors();
        long durationNanos = Duration.ofSeconds(DURATION_IN_SECONDS).toNanos();
        long start = System.nanoTime();
        while (System.nanoTime() - start < durationNanos) {
            Executors.newFixedThreadPool(ncores);
            Executors.newCachedThreadPool();
            Executors.newVirtualThreadPerTaskExecutor();
            Executors.newWorkStealingPool(ncores);
        }
    }
}
