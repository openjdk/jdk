/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8371740
 * @summary Checks for poll() (or peek) returning null when an element must exist.
 */
import java.util.*;
import java.util.concurrent.*;

public class MissedPoll {
    public static void main(String... args) throws Throwable {
        test(new LinkedTransferQueue<>());
        test(new LinkedBlockingQueue<>());
        test(new LinkedBlockingDeque<>());
        test(new ArrayBlockingQueue<>(10));
    }

    private static void test(BlockingQueue<Integer> q)
        throws ExecutionException, InterruptedException {
        System.out.println(q.getClass());
        try (var pool = Executors.newCachedThreadPool()) {
            var futures = new ArrayList<Future<Integer>>();
            var phaser = new Phaser(4) {
                @Override
                protected boolean onAdvance(int phase, int registeredParties) {
                    q.clear();
                    return super.onAdvance(phase, registeredParties);
                }
            };
            for (var i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    int errors = 0;
                    for (int k = 0; k < 10; k++) {
                        for (int j = 0; j < 1000; j++) {
                            q.offer(j);
                            if (q.peek() == null)
                                ++errors;
                            if (q.poll() == null)
                                ++errors;
                        }
                        phaser.arriveAndAwaitAdvance();
                    }
                    return errors;
                }));
            }
            for (var future : futures) {
                Integer res;
                if ((res = future.get()) != 0)
                    throw new AssertionError("Expected 0 but got " + res);
            }
        }
        if (!q.isEmpty())
            throw new AssertionError("Queue is not empty: " + q);
    }
}
