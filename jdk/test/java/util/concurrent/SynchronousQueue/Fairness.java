/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4992438
 * @compile -source 1.5 Fairness.java
 * @run main Fairness
 * @summary Checks that fairness setting is respected.
 */

import java.util.concurrent.*;

public class Fairness {
    private static void testFairness(boolean fair,
                                     final BlockingQueue<Integer> q)
        throws Exception
    {
        for (int i = 0; i < 3; i++) {
            final Integer I = new Integer(i);
            new Thread() { public void run() {
                try { q.put(I); } catch (Exception e) {}
            }}.start();
            Thread.currentThread().sleep(100);
        }
        for (int i = 0; i < 3; i++) {
            int j = q.take().intValue();
            System.err.printf("%d%n",j);
            // Non-fair queues are lifo in our implementation
            if (fair ? j != i : j != 2-i)
                throw new Exception("No fair!");
        }
    }

    public static void main(String[] args) throws Exception {
        testFairness(false, new SynchronousQueue<Integer>());
        testFairness(false, new SynchronousQueue<Integer>(false));
        testFairness(true,  new SynchronousQueue<Integer>(true));
    }
}
