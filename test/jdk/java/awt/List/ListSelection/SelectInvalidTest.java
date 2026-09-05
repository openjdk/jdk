/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.Frame;
import java.awt.List;

import jdk.test.lib.Platform;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

/**
 * @test
 * @bug 8369327 8369455
 * @summary Test awt list selection of invalid indexes
 * @key headful
 * @library /test/lib
 * @build jdk.test.lib.Asserts jdk.test.lib.Platform
 * @run main SelectInvalidTest
 */
public final class SelectInvalidTest {

    /**
     * A special index on windows, selects or deselects all elements.
     */
    private static final int WINDOWS_INVALID = -1;

    /**
     * The list of invalid indexes, their usages should be noop.
     */
    private static final int[] INVALID = {
            WINDOWS_INVALID, Integer.MIN_VALUE, -100, 3, 100, Integer.MAX_VALUE
    };

    public static void main(String[] args) {
        for (int i : INVALID) {
            if (Platform.isWindows() && i == WINDOWS_INVALID) {
                testDisplayable(SelectInvalidTest::testWinDeselectAllSingleMode, i);
                testDisplayable(SelectInvalidTest::testWinSelectAllMultipleMode, i);
            } else {
                testDisplayable(SelectInvalidTest::testSingleMode, i);
                testDisplayable(SelectInvalidTest::testMultipleMode, i);
                testDisplayable(SelectInvalidTest::testEmptySelection, i);
            }
        }
    }

    interface Test {
        void execute(Frame frame, int invalid);
    }

    private static void testDisplayable(Test test, int invalid) {
        Frame frame = new Frame();
        try {
            frame.setSize(300, 200);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            test.execute(frame, invalid);
        } finally {
            frame.dispose();
        }
    }

    private static void testSingleMode(Frame frame, int invalid) {
        List list = new List(4, false);
        frame.add(list);
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");

        // Test initial state
        list.select(invalid);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));

        // Test single selection
        list.select(1);
        list.select(invalid);
        assertEquals(1, list.getSelectedIndex());
        assertEquals(1, list.getSelectedIndexes().length);
        assertEquals(1, list.getSelectedIndexes()[0]);
        assertFalse(list.isIndexSelected(0));
        assertTrue(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));

        // Test selection replacement in single mode
        list.select(2);
        list.select(invalid);
        assertEquals(2, list.getSelectedIndex());
        assertEquals(1, list.getSelectedIndexes().length);
        assertEquals(2, list.getSelectedIndexes()[0]);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));
    }

    private static void testMultipleMode(Frame frame, int invalid) {
        List list = new List(4, true);
        frame.add(list);
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");

        // Test multiple selections
        list.select(0);
        list.select(2);
        list.select(invalid);
        // Returns -1 for multiple selections
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(2, list.getSelectedIndexes().length);
        assertTrue(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));

        // Test partial deselection
        list.deselect(0);
        list.select(invalid);
        // Single selection remaining
        assertEquals(2, list.getSelectedIndex());
        assertEquals(1, list.getSelectedIndexes().length);
        assertEquals(2, list.getSelectedIndexes()[0]);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));
    }

    private static void testEmptySelection(Frame frame, int invalid) {
        List list = new List();
        frame.add(list);
        list.select(invalid);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
    }

    private static void testWinDeselectAllSingleMode(Frame frame, int invalid) {
        List list = new List(4, false);
        frame.add(list);
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");

        list.select(invalid);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));

        list.select(1);
        list.select(invalid);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));

        list.select(2);
        list.select(invalid);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));
    }

    private static void testWinSelectAllMultipleMode(Frame frame, int invalid) {
        List list = new List(4, true);
        frame.add(list);
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");

        list.select(0);
        list.select(2);
        list.select(invalid);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(3, list.getSelectedIndexes().length);
        assertTrue(list.isIndexSelected(0));
        assertTrue(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));

        list.deselect(0);
        list.select(invalid);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(3, list.getSelectedIndexes().length);
        assertTrue(list.isIndexSelected(0));
        assertTrue(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));
    }

}
