/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4895367
 * @summary List scrolling w/ down arrow keys obscures horizontal scrollbar
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "linux")
 * @run main/manual HorizScrollbarEraseTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.List;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HorizScrollbarEraseTest {

    private static final String INSTRUCTIONS = """
            This is a Unix-only test.
            Do the four mini-tests below.
            If the horizontal scrollbar is ever erased by a rectangle
            of the background color, the test FAILS.
            If the horizontal scrollbars remain painted, test passes.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("HorizScrollbarEraseTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(HorizScrollbarEraseTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame("HorizScrollbarEraseTest");
        Panel borderPanel = new Panel();
        borderPanel.setLayout(new BorderLayout());
        Button focusedButton = new Button("Focus starts here");
        borderPanel.add(focusedButton, BorderLayout.NORTH);

        Panel gridPanel = new Panel();
        gridPanel.setLayout(new GridLayout(0, 4));
        borderPanel.add(gridPanel, BorderLayout.CENTER);

        InstructionList il1 = new InstructionList("Tab to Item 2, then \n" +
                                                   "press the down" +
                                                    "arrow key to scroll down");
        il1.list.select(2);
        il1.list.makeVisible(0);
        gridPanel.add(il1);

        InstructionList il2 = new InstructionList("Tab to the next List,\n" +
                                                  "then press the down\n" +
                                                  "arrow key to select\n" +
                                                  "the last item.");
        il2.list.select(3);
        il2.list.makeVisible(0);
        gridPanel.add(il2);

        InstructionList il3 = new InstructionList("Click the button to\n" +
                                                  "programmatically\n" +
                                                  "select item 3 (not showing)");
        Button selectBtn = new Button("Click Me");
        final List selectList = il3.list;
        selectBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectList.select(3);
            }
        });
        il3.add(selectBtn, BorderLayout.CENTER);
        gridPanel.add(il3);

        InstructionList il4 = new InstructionList("Click the button to\nprogrammatically\ndeselect item 3\n(not showing)");
        Button deselectBtn = new Button("Click Me");
        final List deselectList = il4.list;
        deselectBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                deselectList.deselect(3);
            }
        });
        il4.add(deselectBtn, BorderLayout.CENTER);
        il4.list.select(3);
        il4.list.makeVisible(0);
        gridPanel.add(il4);

        frame.add(borderPanel);
        frame.pack();
        return frame;

    }
}

class InstructionList extends Panel {
    TextArea ta;
    public List list;

    public InstructionList(String instructions) {
        super();
        setLayout(new BorderLayout());
        ta = new TextArea(instructions, 6, 25, TextArea.SCROLLBARS_NONE);
        ta.setFocusable(false);
        list = new List();
        for (int i = 0; i < 5; i++) {
            list.add("Item " + i + ", a long, long, long, long item");
        }
        add(ta, BorderLayout.NORTH);
        add(list, BorderLayout.SOUTH);
     }
}
