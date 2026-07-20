/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test virtual thread entering (and reentering) a lot of monitors with no contention
 * @library /test/lib
 * @run main LotsOfUncontendedMonitorEnter
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import jdk.test.lib.thread.VThreadRunner;

public class LotsOfUncontendedMonitorEnter {

    public static void main(String[] args) throws Exception {
        int depth;
        if (args.length > 0) {
            depth = Integer.parseInt(args[0]);
        } else {
            depth = 24; // 33554430 enters
        }
        VThreadRunner.run(() -> {
            testEnter(List.of(), depth);
        });
    }

    /**
     * Enter the monitor for a new object, reenter a monitor that is already held, and
     * repeat to the given depth.
     */
    private static void testEnter(List<Object> ownedMonitors, int depthRemaining) {
        if (depthRemaining > 0) {
            var lock = new Object();
            synchronized (lock) {
                // new list of owned monitors
                var monitors = concat(ownedMonitors, lock);
                testEnter(monitors, depthRemaining - 1);

                // reenter a monitor that is already owned
                int index = ThreadLocalRandom.current().nextInt(monitors.size());
                var otherLock = monitors.get(index);

                synchronized (otherLock) {
                    testEnter(monitors, depthRemaining - 1);
                }
            }
        }
    }

    /**
     * Adds an element to a list, returning a new list.
     */
    private static <T> List<T> concat(List<T> list, T object) {
        var newList = new ArrayList<>(list);
        newList.add(object);
        return newList;
    }
}
