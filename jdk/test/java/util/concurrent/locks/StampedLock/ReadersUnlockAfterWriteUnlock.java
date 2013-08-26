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
 * @test
 * @run main/othervm/timeout=60 ReadersUnlockAfterWriteUnlock
 * @bug 8023234
 * @summary StampedLock serializes readers on writer unlock
 * @author Dmitry Chyuko
 * @author Aleksey Shipilev
 */

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.StampedLock;

public class ReadersUnlockAfterWriteUnlock {
    static final int RNUM = 2;
    static final StampedLock sl = new StampedLock();
    static volatile boolean isDone;

    static CyclicBarrier iterationStart = new CyclicBarrier(RNUM + 1);
    static CyclicBarrier readersHaveLocks = new CyclicBarrier(RNUM);
    static CyclicBarrier writerHasLock = new CyclicBarrier(RNUM + 1);

    static class Reader extends Thread {
        final String name;
        Reader(String name) {
            super();
            this.name = name;
        }
        public void run() {
            while (!isDone && !isInterrupted()) {
                try {
                    iterationStart.await();
                    writerHasLock.await();
                    long rs = sl.readLock();

                    // single reader blocks here indefinitely if readers
                    // are serialized
                    readersHaveLocks.await();

                    sl.unlockRead(rs);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        for (int r = 0 ; r < RNUM; ++r) {
            new Reader("r" + r).start();
        }
        int i;
        for (i = 0; i < 1024; ++i) {
            try {
                iterationStart.await();
                long ws = sl.writeLock();
                writerHasLock.await();
                Thread.sleep(10);
                sl.unlockWrite(ws);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        isDone = true;
    }

}
