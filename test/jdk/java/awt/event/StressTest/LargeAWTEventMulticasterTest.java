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

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.AWTEventMulticaster;

/*
 * @test
 * @bug 8342782
 * @summary Tests large AWTEventMulticasters for StackOverflowErrors
 * @run main LargeAWTEventMulticasterTest
 */
public class LargeAWTEventMulticasterTest {

    /**
     * This is an empty ActionListener that also has a numeric index.
     */
    static class IndexedActionListener implements ActionListener {
        private final int index;

        public IndexedActionListener(int index) {
            this.index = index;
        }

        @Override
        public void actionPerformed(ActionEvent e) {

        }

        public int getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return Integer.toString(index);
        }
    }

    public static void main(String[] args) {
        int maxA = 0;
        try {
            for (int a = 1; a < 200_000; a *= 2) {
                maxA = a;
                testAddingActionListener(a);
            }
        } finally {
            System.out.println("maximum a = " + maxA);
        }
    }

    private static void testAddingActionListener(int numberOfListeners) {
        // step 1: create the large AWTEventMulticaster
        ActionListener l = null;
        for (int a = 0; a < numberOfListeners; a++) {
            l = AWTEventMulticaster.add(l, new IndexedActionListener(a));
        }

        // Prior to 8342782 we could CREATE a large AWTEventMulticaster, but we couldn't
        // always interact with it.

        // step 2: dispatch an event
        // Here we're making sure we don't get a StackOverflowError when we traverse the tree:
        l.actionPerformed(null);

        // step 3: make sure getListeners() returns elements in the correct order
        // The resolution for 8342782 introduced a `rebalance` method; we want to
        // double-check that the rebalanced tree preserves the appropriate order.
        IndexedActionListener[] array = AWTEventMulticaster.getListeners(l, IndexedActionListener.class);
        for (int b = 0; b < array.length; b++) {
            if (b != array[b].getIndex())
                throw new Error("the listeners are in the wrong order. " + b + " != " + array[b].getIndex());
        }
    }
}
