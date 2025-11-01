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

/**
 * @test
 * @key headful
 * @bug 8369327
 * @summary Test awt list deselection methods
 */
public final class DeselectionUnitTest {

    public static void main(String[] args) {
        // non-displayable list
        testSingleMode(null);
        testMultipleMode(null);
        testInvalidDeselection(null);
        testEmptyListDeselection(null);

        // displayable list
        testDisplayable(DeselectionUnitTest::testSingleMode);
        testDisplayable(DeselectionUnitTest::testMultipleMode);
        testDisplayable(DeselectionUnitTest::testInvalidDeselection);
        testDisplayable(DeselectionUnitTest::testEmptyListDeselection);
    }

    interface Test {
        void execute(Frame frame);
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

    private static void testSingleMode(Frame frame) {
        List list = new List(4, false);
        if (frame != null) {
            frame.add(list);
        }
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");

        // Select and deselect single item
        list.select(1);
        assertTrue(list.isIndexSelected(1));
        list.deselect(1);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));

        // Deselect non-selected item (should be no-op)
        list.select(0);
        list.deselect(2);
        assertEquals(0, list.getSelectedIndex());
        assertTrue(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));
    }

    private static void testMultipleMode(Frame frame) {
        List list = new List(4, true);
        if (frame != null) {
            frame.add(list);
        }
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");

        // Select multiple items and deselect one
        list.select(0);
        list.select(1);
        list.select(2);
        assertEquals(3, list.getSelectedIndexes().length);

        list.deselect(1);
        assertEquals(2, list.getSelectedIndexes().length);
        assertTrue(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertTrue(list.isIndexSelected(2));

        // Deselect all remaining
        list.deselect(0);
        list.deselect(2);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));
    }

    private static void testInvalidDeselection(Frame frame) {
        List list = new List(4, false);
        if (frame != null) {
            frame.add(list);
        }
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");

        // Deselect invalid indices (should be no-op)
        list.select(0);
        list.deselect(-1);
        list.deselect(5);
        assertEquals(0, list.getSelectedIndex());
        assertTrue(list.isIndexSelected(0));
        assertFalse(list.isIndexSelected(1));
        assertFalse(list.isIndexSelected(2));
    }

    private static void testEmptyListDeselection(Frame frame) {
        List list = new List();
        if (frame != null) {
            frame.add(list);
        }

        // Deselect on empty list (should be no-op)
        list.deselect(0);
        list.deselect(-1);
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(0, list.getSelectedIndexes().length);
        assertFalse(list.isIndexSelected(0));
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            System.err.println("Expected: " + expected);
            System.err.println("Actual: " + actual);
            throw new RuntimeException("Values are not equal");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new RuntimeException("Expected true but got false");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new RuntimeException("Expected false but got true");
        }
    }
}
