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
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.Test;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class SynchronousQueue20Test extends JSR166TestCase {

    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return newTestSuite(SynchronousQueue20Test.class);
    }

    public void testFairDoesntLeak() throws InterruptedException {
        assertDoesntLeak(new SynchronousQueue<>(true));
    }

    public void testUnfairDoesntLeak() throws InterruptedException {
        assertDoesntLeak(new SynchronousQueue<>(false));
    }

    private void assertDoesntLeak(SynchronousQueue<Object> queue) throws InterruptedException {
        final int NUMBER_OF_ITEMS = 250;
        final int ROUND_WAIT_MILLIS = 50;

        class Item {}
        final Map<Item, Void> survivors =
                Collections.synchronizedMap(WeakHashMap.newWeakHashMap(NUMBER_OF_ITEMS));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for(int i = 0;i < NUMBER_OF_ITEMS;++i) {
                executor.submit(() -> {
                    var item = new Item();
                    survivors.put(item, null);
                    queue.put(item);
                    return null;
                });

                executor.submit(() -> {
                    queue.take();
                    return null;
                });
            }
        } // Close waits until all tasks are done

        while(!survivors.isEmpty()) {
            System.gc();
            Thread.sleep(ROUND_WAIT_MILLIS); // We don't expect interruptions
        }

        assertTrue(queue.isEmpty()); // Make sure that the queue survives until the end
    }

}
