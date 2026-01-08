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
import java.awt.Robot;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * @test
 * @bug 8371657
 * @key headful
 * @summary Checks that programmatic changes to a List do not fire events
 */
public final class ProgrammaticChange {

    private static Robot robot;
    private static volatile boolean itemEvent;
    private static volatile boolean actionEvent;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.mouseMove(0, 0); // Just in case, the mouse may affect selection

        var creators = Arrays.<Supplier<List>>asList(
                List::new, () -> createList(false), () -> createList(true)
        );
        for (Supplier<List> creator : creators) {
            test(creator, true);  // Test displayable list
            test(creator, false); // Test non-displayable list
        }
    }

    private static void test(Supplier<List> creator, boolean displayable) {
        List list = creator.get();
        list.addItemListener(event -> {
            System.err.println("event = " + event);
            itemEvent = true;
        });
        list.addActionListener(event -> {
            System.err.println("event = " + event);
            actionEvent = true;
        });

        Frame frame = null;
        try {
            if (displayable) {
                frame = new Frame();
                frame.setSize(300, 200);
                frame.setLocationRelativeTo(null);
                frame.add(list);
                frame.setVisible(true);
            }
            tryTriggerEvents(list);
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    private static void tryTriggerEvents(List list) {
        // Only "select" and "deselect" should not fire events per the spec,
        // but we also check other methods to prevent accidental changes
        selectAll(list);
        verify();
        deselectAll(list);
        verify();

        // "add" may change the current selection
        selectAll(list);
        list.add("newItemStart", 0);
        list.add("newItemMid", 1);
        list.add("newItemEnd");
        verify();

        // "remove" may change the current selection
        selectAll(list);
        list.remove(0);
        verify();

        // "makeVisible" may change the current selection
        for (int i = 0; i < 100; i++){
            list.add("newItem_" + i, 0);
        }
        selectAll(list);
        list.makeVisible(1);
        list.makeVisible(99);
        verify();

        // "setMultipleMode" may change the current selection
        selectAll(list);
        list.setMultipleMode(!list.isMultipleMode());
        selectAll(list);
        list.setMultipleSelections(!list.allowsMultipleSelections());
        verify();

        // "removeAll" may change the current selection
        selectAll(list);
        list.removeAll();
        verify();

        // No extra logic; just calling methods to touch all code paths
        list.add("newItem1");
        list.getSelectedIndex();
        list.getSelectedIndexes();
        list.getSelectedItem();
        list.getSelectedItems();
        list.getSelectedObjects();
        list.getVisibleIndex();
        list.isIndexSelected(0);
        list.isSelected(0);

        list.add("newItem2");
        list.delItems(0, 0);
        list.addItem("newItem4");
        list.delItem(0);
        list.addItem("newItem6", 0);
        list.replaceItem("newItem7", 0);
        list.remove("newItem7");
        list.add("newItem8");
        list.clear();
        verify();
    }

    private static void selectAll(List list) {
        for (int index = 0; index < list.getItemCount(); index++) {
            list.select(index);
        }
    }

    private static void deselectAll(List list) {
        for (int index = 0; index < list.countItems(); index++) {
            list.deselect(index);
        }
    }

    private static List createList(boolean multipleMode) {
        List list = new List(4, multipleMode);
        for (String item : new String[]{"item1", "item2", "item3"}) {
            list.add(item);
        }
        return list;
    }

    private static void verify() {
        robot.waitForIdle();
        robot.delay(700); // Large delay, we are waiting for unexpected events
        if (actionEvent || itemEvent) {
            System.err.println("itemEvent: " + itemEvent);
            System.err.println("actionEvent: " + actionEvent);
            throw new RuntimeException("Unexpected event");
        }
    }
}
