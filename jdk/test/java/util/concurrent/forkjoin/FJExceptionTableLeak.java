/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @author Doug Lea
 * @bug 8004138
 * @summary Check if ForkJoinPool table leaks thrown exceptions.
 * @run main/othervm -Xmx2200k FJExceptionTableLeak
 */

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class FJExceptionTableLeak {
    // This test was observed to fail with jdk7 -Xmx2200k,
    // using STEPS = 220 and TASKS_PER_STEP = 100
    static final int STEPS = 500;
    static final int TASKS_PER_STEP = 100;

    static class FailingTaskException extends RuntimeException {}
    static class FailingTask extends RecursiveAction {
        public void compute() {
            throw new FailingTaskException();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(4);
        FailingTask[] tasks = new FailingTask[TASKS_PER_STEP];
        for (int k = 0; k < STEPS; ++k) {
            for (int i = 0; i < tasks.length; ++i)
                tasks[i] = new FailingTask();
            for (int i = 0; i < tasks.length; ++i)
                pool.execute(tasks[i]);
            for (int i = 0; i < tasks.length; ++i) {
                try {
                    tasks[i].join();
                    throw new AssertionError("should throw");
                } catch (FailingTaskException success) {}
            }
        }
    }
}
