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
import java.awt.Toolkit;
import java.util.Arrays;

/**
 * @test
 * @bug 8369327
 * @summary Test awt list setMultipleMode selection
 * @key headful
 */
public final class SetMultipleModeTest {

    public static void main(String[] args) {
        // Non-displayable list
        // test(null); Does not work per the spec

        // Displayable list
        Frame frame = new Frame();
        try {
            test(frame);
        } finally {
            frame.dispose();
        }
    }

    private static void test(Frame frame) {
        List list = new List();
        list.add("Item1");
        list.add("Item2");
        list.add("Item3");
        if (frame != null) {
            frame.add(list);
            frame.pack();
        }

        // Empty: mode switch preserves empty
        list.setMultipleMode(true);
        check(list);
        list.deselect(0);
        check(list);
        list.setMultipleMode(false);
        check(list);

        // Single to multi preserves selection
        list.select(1);
        list.setMultipleMode(true);
        check(list, 1);

        // Multi to multi is no-op
        list.select(0);
        list.select(2);
        check(list, 0, 1, 2);
        list.setMultipleMode(true);
        check(list, 0, 1, 2);

        // Multi to single keeps lead
        list.setMultipleMode(false);
        check(list, 2);

        // Single to single is no-op
        list.setMultipleMode(false);
        check(list, 2);

        // Round-trip
        list.setMultipleMode(true);
        check(list, 2);
        list.setMultipleMode(false);
        check(list, 2);

        // XAWT does not move the focus cursor on deselect(),
        // so multi->single keeps the focused item, skip on XAWT
        boolean isXToolkit = "sun.awt.X11.XToolkit".equals(
                Toolkit.getDefaultToolkit().getClass().getName());
        if (!isXToolkit) {
            // Deselect lead in multi, switch to single
            list.setMultipleMode(true);
            list.select(0);
            list.select(1);
            check(list, 0, 1, 2);
            list.deselect(2);
            check(list, 0, 1);
            list.setMultipleMode(false);
            check(list);

            // Deselect non-selected in multi, no-op
            list.setMultipleMode(true);
            list.select(0);
            check(list, 0);
            list.deselect(2);
            check(list, 0);
            list.setMultipleMode(false);
            check(list);
        }

        if (frame != null) {
            frame.remove(list);
        }
    }

    private static void check(List list, int... expected) {
        int[] actual = list.getSelectedIndexes();
        Arrays.sort(actual);
        if (!Arrays.equals(expected, actual)) {
            throw new RuntimeException(
                    "Expected %s, got %s".formatted(Arrays.toString(expected),
                                                    Arrays.toString(actual)));
        }
    }
}
