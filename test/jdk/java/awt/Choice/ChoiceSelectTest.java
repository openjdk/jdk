/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Panel;

/*
 *  @test
 *  @bug 4115139 4128213
 *  @summary Tests that the (rather bizarre) rules for handling selection
 *  in Choice components are implemented as documented in
 *  "The Java Class Libraries 2nd Edition"
 *  @key headful
 */

public class ChoiceSelectTest extends Panel {
    final Choice c;

    public ChoiceSelectTest() {
        setLayout(new FlowLayout());
        c = new Choice();
        add(c);
    }

    private void test() {
        testAddition();
        testInsertion();
        testRemoval();
        testIndices();
    }

    public void testAddition() {
        c.removeAll();

        // check that after first item added selection is zero
        c.addItem("zero");
        if (c.getSelectedIndex() != 0) {
            throw new SelectionException("selection wrong after first add");
        }

        // check that selection doesn't change for subsequent adds
        c.addItem("one");
        c.select(1);
        c.addItem("two");
        if (c.getSelectedIndex() != 1) {
            throw new SelectionException("selection wrong after subsequent add");
        }
    }

    public void testInsertion() {
        c.removeAll();

        // check that after first item inserted selection is zero
        c.insert("zero", 0);
        if (c.getSelectedIndex() != 0) {
            throw new SelectionException("selection wrong after first insert");
        }

        // check that if selected item shifted, selection goes to zero
        c.insert("three", 1);
        c.select(1);
        c.insert("one", 1);
        if (c.getSelectedIndex() != 0) {
            throw new SelectionException("selection wrong after selected item shifted");
        }

        // check that if selected item not shifted, selection stays the same
        c.select(1);
        c.insert("two", 2);
        if (c.getSelectedIndex() != 1) {
            throw new SelectionException("selection wrong after item inserted after selection");
        }
    }

    public void testRemoval() {
        c.removeAll();

        // check that if removing selected item, selection goes to 0
        c.add("zero");
        c.add("one");
        c.add("two");
        c.select(2);
        c.remove(2);
        if (c.getSelectedIndex() != 0) {
            throw new SelectionException("selection wrong after removing selected item");
        }

        // check that if removing item before the selection
        // the selected index is updated
        c.add("two");
        c.add("three");
        c.select(3);
        c.remove(1);
        if (c.getSelectedIndex() != 2) {
            throw new SelectionException("selection wrong after removing item before it");
        }
    }

    public void testIndices() {
        c.removeAll();

        c.addItem("zero");
        c.addItem("one");
        c.addItem("two");
        c.addItem("three");
        c.addItem("four");
        c.addItem("five");

        // Test selection of negative index
        try {
            c.select(-1);
            throw new SelectionException("Negative Index Test FAILED");
        } catch (IllegalArgumentException expected) {}

        // Test selection of zero index
        try {
            c.select(0);
        } catch (IllegalArgumentException iae) {
            throw new SelectionException("Zero Index Test FAILED", iae);
        }

        // Test selection of maximum index
        try {
            c.select(5);
        } catch (IllegalArgumentException iae) {
            throw new SelectionException("Maximum Index Test FAILED", iae);
        }

        // Test selection of index that is too large
        try {
            c.select(6);
            throw new SelectionException("Greater than Maximum Index Test FAILED");
        } catch (IllegalArgumentException expected) {}
    }

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> new ChoiceSelectTest().test());
    }

    class SelectionException extends RuntimeException {
        SelectionException(String msg, Throwable cause) {
            super(msg, cause);
            System.out.println(
                    "Selection item is '" + c.getSelectedItem() +
                            "' at index " + c.getSelectedIndex());
        }

        SelectionException(String msg) {
            this(msg, null);
        }
    }
}
