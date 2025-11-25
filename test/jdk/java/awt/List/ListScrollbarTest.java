/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4096445
 * @summary Test to verify List Scollbar appears/disappears automatically
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ListScrollbarTest
 */

import java.awt.Button;
import java.awt.Component;
import java.awt.Event;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.List;

public class ListScrollbarTest extends Frame {
    static final int ITEMS = 10;
    List ltList;
    List rtList;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. There are two lists added to the Frame separated by
                   a column of buttons
                2. Double click on any item(s) on the left list, you would see
                   a '*' added at the end of the item
                3. Keep double clicking on the same item till the length of the
                   item exceeds the width of the list
                4. Now, if you don't get the horizontal scrollbar on
                   the left list click FAIL.
                5. If you get horizontal scrollbar, select the item
                   (that you double clicked) and press the '>' button
                   to move the item to the right list.
                6. If horizontal scroll bar appears on the right list
                   as well as disappears from the left list [only if both
                   happen] proceed with step 8 else click FAIL
                7. Now move the same item to the left list, by pressing
                     '<' button
                8. If the horizontal scrollbar appears on the left list
                   and disappears from the right list[only if both happen]
                   click PASS else click FAIL.
                """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ListScrollbarTest::new)
                .build()
                .awaitAndCheck();
    }

    public ListScrollbarTest() {
        super("List scroll bar test");
        GridBagLayout gbl = new GridBagLayout();
        ltList = new List(ITEMS, true);
        rtList = new List(0, true);
        setLayout(gbl);
        add(ltList, 0, 0, 1, 5, 1.0, 1.0);
        add(rtList, 2, 0, 1, 5, 1.0, 1.0);
        add(new Button(">"), 1, 0, 1, 1, 0, 1.0);
        add(new Button(">>"), 1, 1, 1, 1, 0, 1.0);
        add(new Button("<"), 1, 2, 1, 1, 0, 1.0);
        add(new Button("<<"), 1, 3, 1, 1, 0, 1.0);
        add(new Button("!"), 1, 4, 1, 1, 0, 1.0);

        for (int i = 0; i < ITEMS; i++) {
            ltList.addItem("item " + i);
        }
        setSize(220, 250);
    }

    void add(Component comp, int x, int y, int w, int h, double weightx, double weighty) {
        GridBagLayout gbl = (GridBagLayout) getLayout();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = w;
        c.gridheight = h;
        c.weightx = weightx;
        c.weighty = weighty;
        add(comp);
        gbl.setConstraints(comp, c);
    }

    void reverseSelections(List l) {
        for (int i = 0; i < l.countItems(); i++) {
            if (l.isSelected(i)) {
                l.deselect(i);
            } else {
                l.select(i);
            }
        }
    }

    void deselectAll(List l) {
        for (int i = 0; i < l.countItems(); i++) {
            l.deselect(i);
        }
    }

    void replaceItem(List l, String item) {
        for (int i = 0; i < l.countItems(); i++) {
            if (l.getItem(i).equals(item)) {
                l.replaceItem(item + "*", i);
            }
        }
    }

    void move(List l1, List l2, boolean all) {

        // if all the items are to be moved
        if (all) {
            for (int i = 0; i < l1.countItems(); i++) {
                l2.addItem(l1.getItem(i));
            }
            l1.delItems(0, l1.countItems() - 1);
        } else { // else move the selected items
            String[] items = l1.getSelectedItems();
            int[] itemIndexes = l1.getSelectedIndexes();

            deselectAll(l2);
            for (int i = 0; i < items.length; i++) {
                l2.addItem(items[i]);
                l2.select(l2.countItems() - 1);
                if (i == 0) {
                    l2.makeVisible(l2.countItems() - 1);
                }
            }
            for (int i = itemIndexes.length - 1; i >= 0; i--) {
                l1.delItem(itemIndexes[i]);
            }
        }
    }

    @Override
    public boolean action(Event evt, Object arg) {
        if (">".equals(arg)) {
            move(ltList, rtList, false);
        } else if (">>".equals(arg)) {
            move(ltList, rtList, true);
        } else if ("<".equals(arg)) {
            move(rtList, ltList, false);
        } else if ("<<".equals(arg)) {
            move(rtList, ltList, true);
        } else if ("!".equals(arg)) {
            if (ltList.getSelectedItems().length > 0) {
                reverseSelections(ltList);
            } else if (rtList.getSelectedItems().length > 0) {
                reverseSelections(rtList);
            }
        } else if (evt.target == rtList || evt.target == ltList) {
            replaceItem((List) evt.target, (String) arg);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean handleEvent(Event evt) {
        if (evt.id == Event.LIST_SELECT
                || evt.id == Event.LIST_DESELECT) {
            if (evt.target == ltList) {
                deselectAll(rtList);
            } else if (evt.target == rtList) {
                deselectAll(ltList);
            }
            return true;
        }
        return super.handleEvent(evt);
    }
}
