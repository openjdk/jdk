/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6267833
 * @summary Tests for invokeAny, invokeAll
 * @author  Martin Buchholz
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Invoke {
    static volatile int passed = 0, failed = 0;

    static void fail(String msg) {
        failed++;
        new AssertionError(msg).printStackTrace();
    }

    static void pass() {
        passed++;
    }

    static void unexpected(Throwable t) {
        failed++;
        t.printStackTrace();
    }

    static void check(boolean condition, String msg) {
        if (condition) pass(); else fail(msg);
    }

    static void check(boolean condition) {
        check(condition, "Assertion failure");
    }

    public static void main(String[] args) {
        try {
            final AtomicLong count = new AtomicLong(0);
            ExecutorService fixed = Executors.newFixedThreadPool(5);
            class Inc implements Callable<Long> {
                public Long call() throws Exception {
                    Thread.sleep(200); // Catch IE from possible cancel
                    return count.incrementAndGet();
                }
            }
            List<Inc> tasks = Arrays.asList(new Inc(), new Inc(), new Inc());
            List<Future<Long>> futures = fixed.invokeAll(tasks);
            check(futures.size() == tasks.size());
            check(count.get() == tasks.size());

            long gauss = 0;
            for (Future<Long> future : futures) gauss += future.get();
            check(gauss == ((tasks.size()+1)*tasks.size())/2);

            ExecutorService single = Executors.newSingleThreadExecutor();
            long save = count.get();
            check(single.invokeAny(tasks) == save + 1);
            check(count.get() == save + 1);

            fixed.shutdown();
            single.shutdown();

        } catch (Throwable t) { unexpected(t); }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new Error("Some tests failed");
    }
}
