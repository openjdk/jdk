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

/*
 * @test id=default
 * @bug 8335269
 * @summary Test that Thread.yield loop polls for safepoints
 * @requires vm.continuations
 * @library /test/lib
 * @run junit/othervm ThreadPollOnYield
 */

/*
 * @test id=c2
 * @bug 8335269
 * @summary Test that Thread.yield loop polls for safepoints
 * @requires vm.continuations & vm.compMode != "Xcomp"
 * @library /test/lib
 * @run junit/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=inline,*::yield* -XX:CompileCommand=inline,*::*Yield ThreadPollOnYield
 */

import java.util.concurrent.atomic.AtomicBoolean;

import jdk.test.lib.thread.VThreadPinner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadPollOnYield {
    static void foo(AtomicBoolean done) {
        while (!done.get()) {
            Thread.yield();
        }
    }

    @Test
    void testThreadYieldPolls() throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        var vthread = Thread.ofVirtual().start(() -> {
            VThreadPinner.runPinned(() -> foo(done));
        });
        Thread.sleep(5000);
        done.set(true);
        vthread.join();

        System.out.println("First vthread done");

        AtomicBoolean done2 = new AtomicBoolean();
        vthread = Thread.ofVirtual().start(() -> {
            VThreadPinner.runPinned(() -> foo(done2));
        });
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 5000) {
            Thread.sleep(250);
            System.gc();
        }
        done2.set(true);
        vthread.join();
    }
}
