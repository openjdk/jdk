/*
 * Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.ListIterator;

/*
 * @test
 * @requires vm.gc.ConcMarkSweep & !vm.graal.enabled
 * @key cte_test
 * @bug 4950157
 * @summary Stress the behavior of ergonomics when the heap is nearly full and
 *          stays nearly full.
 * @run main/othervm
 *  -XX:+UseConcMarkSweepGC -XX:-CMSYield -XX:-CMSPrecleanRefLists1
 *  -XX:CMSInitiatingOccupancyFraction=0 -Xmx80m TestBubbleUpRef 16000 50 10000
 */

/**
 * Test program to stress the behavior of ergonomics when the
 * heap is nearly full and stays nearly full.
 * This is a test to catch references that have been discovered
 * during concurrent marking and whose referents have been
 * cleared by the mutator.
 * Allocate objects with weak references until the heap is full
 * Free the objects.
 * Do work so that concurrent marking has a chance to work
 * Clear the referents out of the weak references
 * System.gc() in the hopes that it will acquire the collection
 * Free the weak references
 * Do it again.
 *
 * Use the following VM options
 *     -Xmx80m -XX:-CMSYield [-XX:+UseConcMarkSweepGC] -XX:-CMSPrecleanRefLists1
 *      -XX:CMSInitiatingOccupancyFraction=0
 *
 * Use parameter:
 *     args[0] - array size  (16000)
 *     args[1] - iterations  (50)
 *     args[2] - work        (10000)
 */
class MyList extends LinkedList {

    int[] a;

    MyList(int size) {
        a = new int[size];
    }
}

class MyRefList extends LinkedList {

    WeakReference ref;

    MyRefList(Object o, ReferenceQueue rq) {
        ref = new WeakReference(o, rq);
    }

    void clearReferent() {
        ref.clear();
    }
}

public class TestBubbleUpRef {

    MyList list;
    MyRefList refList;
    ReferenceQueue rq;
    int refListLen;
    int arraySize;
    int iterations;
    int workUnits;

    TestBubbleUpRef(int as, int cnt, int wk) {
        arraySize = as;
        iterations = cnt;
        workUnits = wk;
        list = new MyList(arraySize);
        refList = new MyRefList(list, rq);
    }

    public void fill() {
        System.out.println("fill() " + iterations + " times");
        int count = 0;
        while (true) {
            try {
                // Allocations
                MyList next = new MyList(arraySize);
                list.add(next);
                MyRefList nextRef = new MyRefList(next, rq);
                refList.add(nextRef);
            } catch (OutOfMemoryError e) {
                // When the heap is full
                try {
                    if (count++ > iterations) {
                        return;
                    }
                    System.out.println("Freeing list");
                    while (!list.isEmpty()) {
                        list.removeFirst();
                    }
                    System.out.println("Doing work");
                    int j = 0;
                    for (int i = 1; i < workUnits; i++) {
                        j = j + i;
                    }
                    System.out.println("Clearing refs");
                    ListIterator listIt = refList.listIterator();
                    while (listIt.hasNext()) {
                        MyRefList next = (MyRefList) listIt.next();
                        next.clearReferent();
                    }
                    System.gc();
                    System.out.println("Freeing refs");
                    while (!refList.isEmpty()) {
                        refList.removeFirst();
                    }
                } catch (OutOfMemoryError e2) {
                    System.err.println("Out of Memory - 2 ");
                    continue;
                }
            } catch (Exception e) {
                System.err.println("Unexpected exception: " + e);
                return;
            }
        }
    }

    /**
     * Test entry point.
     *     args[0] - array size  (is the size of the int array in a list item)
     *     args[1] - iterations  (is the number of out-of-memory exceptions before exit)
     *     args[2] - work        (is the work done between allocations)
     * @param args
     */
    public static void main(String[] args) {
        // Get the input parameters.
        if (args.length != 3) {
            throw new IllegalArgumentException("Wrong number of input argumets");
        }

        int as = Integer.parseInt(args[0]);
        int cnt = Integer.parseInt(args[1]);
        int work = Integer.parseInt(args[2]);

        System.out.println("<array size> " + as + "\n"
                + "<OOM's> " + cnt + "\n"
                + "<work units> " + work + "\n");

        // Initialization
        TestBubbleUpRef b = new TestBubbleUpRef(as, cnt, work);

        // Run the test
        try {
            b.fill();
        } catch (OutOfMemoryError e) {
            b = null; // Free memory before trying to print anything
            System.err.println("Out of Memory - exiting ");
        } catch (Exception e) {
            System.err.println("Exiting ");
        }
    }
}

