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
 * @bug 8335362
 * @summary Test virtual thread usage with big stackChunks
 * @requires vm.continuations
 * @run junit/othervm BigStackChunk
 */

import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BigStackChunk {

    void recurse(int cnt, ReentrantLock rlock) {
        int i1 = cnt;
        int i2 = i1 + 1;
        int i3 = i2 + 1;
        int i4 = i3 + 1;
        int i5 = i4 + 1;
        int i6 = i5 + 1;
        int i7 = i6 + 1;
        long ll = 2 * (long)i1;
        float ff = ll + 1.2f;
        double dd = ff + 1.3D;

        if (cnt > 0) {
            recurse(cnt - 1, rlock);
        } else {
            rlock.lock();
            rlock.unlock();
        }
    }

    @Test
    void bigStackChunkTest() throws Exception {
        int VTHREAD_CNT = Runtime.getRuntime().availableProcessors();
        ReentrantLock rlock = new ReentrantLock();
        Thread[] vthreads = new Thread[VTHREAD_CNT];

        rlock.lock();
        for (int i = 0; i < VTHREAD_CNT; i++) {
            vthreads[i] = Thread.ofVirtual().start(() -> {
                // Set up things so that half of the carriers will commit lots of
                // pages in the stack while running the mounted vthread and half
                // will just commit very few ones.
                if (Math.random() < 0.5) {
                    recurse(300, rlock);
                } else {
                    recurse(1, rlock);
                }
            });
        }
        await(vthreads[0], Thread.State.WAITING);
        // Now we expect that some vthread that recursed a lot is mounted on
        // a carrier that previously run a vthread that didn't recurse at all.
        rlock.unlock();

        for (int i = 0; i < VTHREAD_CNT; i++) {
            vthreads[i].join();
        }
    }

    private void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assertTrue(state != Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}