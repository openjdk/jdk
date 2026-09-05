/*
 * Copyright 2025 Arm Limited and/or its affiliates.
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

/**
 * @test id=vthread-deopt-c1
 * @summary Deoptimization test for virtual threads (C1)
 * @requires vm.continuations
 * @requires vm.compiler1.enabled
 * @requires vm.opt.TieredStopAtLevel != 0
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-BackgroundCompilation
 *                   -XX:TieredStopAtLevel=1
 *                   Deoptimization
 */

/**
 * @test id=vthread-deopt-c2
 * @summary Deoptimization test for virtual threads (C2)
 * @requires vm.continuations
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-BackgroundCompilation
 *                   -XX:-TieredCompilation
 *                   Deoptimization
 */

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.Objects;
import jdk.test.whitebox.WhiteBox;

public class Deoptimization {
    static final WhiteBox white_box = WhiteBox.getWhiteBox();

    static class TestTask implements Runnable {
        CyclicBarrier start_barrier = null;
        AtomicInteger completed_number = new AtomicInteger(0);

        public void reset(int barrier_parties) {
            start_barrier = new CyclicBarrier(barrier_parties);
            completed_number.set(0);
        }

        public int getNumberWaiting() {
            return start_barrier.getNumberWaiting();
        }

        public int getNumberCompleted() {
            return completed_number.get();
        }

        public void await() throws BrokenBarrierException, InterruptedException {
            start_barrier.await();
        }

        public void run() {
            try {
                await();
            } catch(BrokenBarrierException e) {
                return;
            } catch(InterruptedException e) {
                return;
            }

            completed_number.getAndIncrement();
        }
    }

    static void test(TestTask task, Method method, int vthreads_num) throws Exception {
        task.reset(vthreads_num + 1 /* 1 for the main thread */);

        Thread[] vthreads = new Thread[vthreads_num];
        for (int i = 0; i < vthreads_num; i++) {
            vthreads[i] = Thread.startVirtualThread(task);
        }

        while (task.getNumberWaiting() != vthreads_num) {
            Thread.onSpinWait();
        }

        if (method != null) {
            if (!white_box.isMethodCompiled(method, false)) {
                throw new Error("Unexpectedly, it is not compiled.");
            }

            white_box.deoptimizeMethod(method);

            if (white_box.isMethodCompiled(method, false)) {
                throw new Error("Unexpectedly, it is compiled.");
            }
        }

        task.await();

        for (int i = 0; i < vthreads_num; i++) {
            vthreads[i].join();
        }

        if (task.getNumberCompleted() != vthreads_num) {
            throw new Error("Some threads didn't reach completion");
        }
    }

    static int getIntegerOption(String option_name) {
        Object option_object = white_box.getVMFlag(option_name);
        String option_string = Objects.toString(option_object);
        return Integer.parseInt(option_string);
    }

    public static void main(String[] args) throws Exception {
        int tiered_stop_at_level = getIntegerOption("TieredStopAtLevel");

        Method method_run = TestTask.class.getMethod("run");
        white_box.testSetDontInlineMethod(method_run, true);

        Method method_await = TestTask.class.getMethod("await");
        white_box.testSetDontInlineMethod(method_await, true);

        TestTask task = new TestTask();

        // Warm-up
        test(task, null, 2);

        white_box.enqueueMethodForCompilation(method_run, tiered_stop_at_level);

        // Deoptimization test
        test(task, method_run, 10000);
    }
}
