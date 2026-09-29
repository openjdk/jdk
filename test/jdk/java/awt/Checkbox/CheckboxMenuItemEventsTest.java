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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.CheckboxMenuItem;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/*
 * @test
 * @bug 4814163 5005195
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests events fired by CheckboxMenuItem
 * @run main/manual CheckboxMenuItemEventsTest
*/

public class CheckboxMenuItemEventsTest extends Frame implements ActionListener {
    Button trigger;
    PopupMenu popup;
    TextArea ta;

    class Listener implements ItemListener, ActionListener {
        public void itemStateChanged(ItemEvent e) {
            ta.append("CORRECT: ItemEvent fired\n");
        }

        public void actionPerformed(ActionEvent e) {
            ta.append("ERROR: ActionEvent fired\n");
        }
    }

    Listener listener = new Listener();

    private static final String INSTRUCTIONS = """
            Press button to invoke popup menu
            When you press checkbox menu item
            Item state should toggle (on/off).
            ItemEvent should be displayed in log below.
            And ActionEvent should not be displayed
            Press PASS if ItemEvents are generated
            and ActionEvents are not, FAIL Otherwise.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("CheckboxMenuItemEventsTest")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(CheckboxMenuItemEventsTest::new)
                .build()
                .awaitAndCheck();
    }

    public CheckboxMenuItemEventsTest() {
        CheckboxMenuItem i1 = new CheckboxMenuItem("CheckBoxMenuItem 1");
        CheckboxMenuItem i2 = new CheckboxMenuItem("CheckBoxMenuItem 2");
        Panel p1 = new Panel();
        Panel p2 = new Panel();

        setLayout(new BorderLayout());
        ta = new TextArea();
        p2.add(ta);

        trigger = new Button("menu");
        trigger.addActionListener(this);

        popup = new PopupMenu();

        i1.addItemListener(listener);
        i1.addActionListener(listener);
        popup.add(i1);
        i2.addItemListener(listener);
        i2.addActionListener(listener);
        popup.add(i2);

        trigger.add(popup);

        p1.add(trigger);

        add(p1, BorderLayout.NORTH);
        add(p2, BorderLayout.SOUTH);

        pack();
        validate();
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == (Object) trigger) {
            popup.show(trigger, trigger.getSize().width, 0);
        }
    }
}
