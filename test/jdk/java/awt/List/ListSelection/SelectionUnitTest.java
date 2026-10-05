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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

/**
 * @test
 * @bug 8369327
 * @summary Test awt list selection methods
 * @key headful
 * @library /test/lib
 * @build jdk.test.lib.Asserts
 * @run main SelectionUnitTest
 */
public final class SelectionUnitTest {

    public static void main(String[] args) {
        testNonDisplayable(SelectionUnitTest::testSingleMode);
        testNonDisplayable(SelectionUnitTest::testMultipleMode);
        testNonDisplayable(SelectionUnitTest::testEmptySelection);

        testDisplayable(SelectionUnitTest::testSingleMode);
        testDisplayable(SelectionUnitTest::testMultipleMode);
        testDisplayable(SelectionUnitTest::testEmptySelection);
    }

    interface Test {
        void execute(Frame frame);
    }

    private static void testNonDisplayable(Test test) {
        test.execute(null);
    }

    private static void testDisplayable(Test test) {
        Frame frame = new Frame();
        try {
            frame.setSize(300, 200);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            test.execute(frame);
        } finally {
            frame.dispose();
        }
    }

    private static List createList(Frame frame, boolean multi) {
        List list = new List(4, multi);
        if (frame != null) {
            frame.add(list);
        }
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");
        return list;
    }

    private static void testSingleMode(Frame frame) {
        List list = createList(frame, false);

        // Test initial state
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));

        // Test single selection
        list.select(1);
        assertEquals(1, list.getSelectedIndex());
        assertEquals(1, list.getSelectedIndexes().length);
        assertEquals(1, list.getSelectedIndexes()[0]);
        assertFalse(list.isIndexSelected(0));
        assertTrue(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));

        // Test selection replacement in single mode
        list.select(2);
        assertEquals(2, list.getSelectedIndex());
        assertEquals(1, list.getSelectedIndexes().length);
        assertEquals(2, list.getSelectedIndexes()[0]);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));
    }

    private static void testMultipleMode(Frame frame) {
        List list = createList(frame, true);

        // Test multiple selections
        list.select(0);
        list.select(2);
        // Returns -1 for multiple selections
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(2, list.getSelectedIndexes().length);
        assertTrue(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));

        // Test partial deselection
        list.deselect(0);
        // Single selection remaining
        assertEquals(2, list.getSelectedIndex());
        assertEquals(1, list.getSelectedIndexes().length);
        assertEquals(2, list.getSelectedIndexes()[0]);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));
    }

    private static void testEmptySelection(Frame frame) {
        List list = new List();
        if (frame != null) {
            frame.add(list);
        }
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
    }

}
