/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4676536
 * @summary REGRESSION: makeVisible() method of List Component does not perform
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SelectedItemVisibilityTest
 */

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.List;

public class SelectedItemVisibilityTest {

    static List list1, list2;
    static int visibleItem = 4;
    static int selectedItems[] = {6, 7, 8};
    static String selectedItemsStr = "";

    static {
        for (int i = 0 ; i < selectedItems.length ; i++) {
            selectedItemsStr += ""+selectedItems[i]+" ";
        }
    }

    private static final String INSTRUCTIONS =
            "You should see two lists.\n" +
            "\n" +
            "list1: \n" +
            "\t1. the first visible item should be " + visibleItem +
            "\n\t2. the selected item should be " + selectedItems[0] +
            "\n" +
            "list2:\n" +
            "\t1. the first visible item should be " + visibleItem +
            "\n\t2. the selected items should be " + selectedItemsStr +
            "\n" +
            "\nIf it is so the test passed else failed.";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("SelectedItemVisibilityTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(SelectedItemVisibilityTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {

        Frame frame = new Frame("SelectedItemVisibilityTest Frame");
        frame.setLayout(new FlowLayout());

        // list1
        list1 = new List(4);
        for (int i = 0; i < 20; i++) {
            list1.add(""+i);
        }
        list1.makeVisible(visibleItem);
        list1.select(selectedItems[0]);
        frame.add(new Label("list1:"));
        frame.add(list1);

        // list2
        list2 = new List(4);
        list2.setMultipleMode(true);
        for (int i = 0; i < 20; i++) {
            list2.add(""+i);
        }
        list2.makeVisible(visibleItem);
        for (int i = 0 ; i < selectedItems.length ; i++) {
            list2.select(selectedItems[i]);
        }
        frame.add(new Label("list2:"));
        frame.add(list2);
        frame.setSize(200, 200);

        // common output
        String s;
        int sel[];

        PassFailJFrame.log("list1: ");
        PassFailJFrame.log("\tgetVisibleIndex="+list1.getVisibleIndex());
        sel = list1.getSelectedIndexes();
        s = "\tgetSelectedIndexes=";
        for (int i = 0 ; i < sel.length ; i++) {
            s += "" + sel[i] + " ";
        }
        PassFailJFrame.log(s);

        PassFailJFrame.log("list2: ");
        PassFailJFrame.log("\tgetVisibleIndex="+list2.getVisibleIndex());
        sel = list2.getSelectedIndexes();
        s = "\tgetSelectedIndexes=";
        for (int i = 0 ; i < sel.length ; i++) {
            s += "" + sel[i] + " ";
        }
        PassFailJFrame.log(s);
        return frame;
    }
}
