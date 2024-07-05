/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/*
 * @test
 * @summary Test of diagnostic command Thread.print with virtual threads
 * @requires vm.continuations
 * @library /test/lib
 * @run junit PrintMountedVirtualThread
 */
public class PrintMountedVirtualThread {

    public void run(CommandExecutor executor) throws InterruptedException {
        var shouldFinish = new AtomicBoolean(false);
        var started = new AtomicBoolean();
        final Runnable runnable = new DummyRunnable(shouldFinish, started);
        try {
            Thread vthread = Thread.ofVirtual().name("Dummy Vthread").start(runnable);
            while (!started.get()) {
                Thread.sleep(10);
            }
            /* Execute */
            OutputAnalyzer output = executor.execute("Thread.print");
            output.shouldMatch(".*at " + Pattern.quote(DummyRunnable.class.getName()) + "\\.run.*");
            output.shouldMatch(".*at " + Pattern.quote(DummyRunnable.class.getName()) + "\\.compute.*");
            output.shouldMatch("Mounted virtual thread " + "#" + vthread.threadId());

        } finally {
            shouldFinish.set(true);
        }
    }

    @Test
    public void jmx() throws InterruptedException {
        run(new JMXExecutor());
    }

    static class DummyRunnable implements Runnable {
        private final AtomicBoolean shouldFinish;
        private final AtomicBoolean started;

        public DummyRunnable(AtomicBoolean shouldFinish, AtomicBoolean started) {
           this.shouldFinish = shouldFinish;
           this.started = started;
        }

        public void run() {
            compute();
        }

        void compute() {
            started.set(true);
            while (!shouldFinish.get()) {
                Thread.onSpinWait();
            }
        }
    }

}
