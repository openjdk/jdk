/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4024943
 * @summary  Test for position of List scrollbar when it is added
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollbarPositionTest
 */

import java.awt.Button;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.List;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ScrollbarPositionTest {
    static int item = 0;
    static List list;
    static Button addButton, delButton;

    private static final String INSTRUCTIONS = """
        Click on the "Add List Item" button many times
        until the vertical scrollbar appears.
        Verify that the displayed vertical scrollbar does not take the space
        that was occupied by buttons before the scrollbar is shown.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ScrollbarPositionTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(ScrollbarPositionTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Panel pan;

        Frame frame = new Frame("ScrollbarPositionTest Frame");
        frame.setLayout(new GridLayout(1, 2));
        list = new List();
        frame.add(list);
        frame.add(pan = new Panel());
        pan.setLayout(new GridLayout(4, 1));

        MyListener listener = new MyListener();
        addButton = new Button("Add List Item");
        addButton.addActionListener(listener);
        pan.add(addButton);

        delButton = new Button("Delete List Item");
        delButton.addActionListener(listener);
        pan.add(delButton);

        frame.pack();
        return frame;
    }

    static class MyListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            if (evt.getSource() == addButton) {
                String s = "item";
                for (int i = 0; i <= item; i++) {
                    s = s +" "+Integer.toString(i);
                }
                item++;
                list.addItem(s);
            } else if (evt.getSource() == delButton) {
                int i;
                if ((i = list.countItems()) > 0) {
                    list.delItem(i - 1);
                    --item;
                }
            }
        }
    }
}
