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
 * @bug 4060975
 * @summary tests that a component doesn't lose focus on resize
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FocusChangeOnResizeTest
*/

import java.util.List;

import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class FocusChangeOnResizeTest {

    private static final String INSTRUCTIONS = """
          For the Frame and the Dialog:
          Press the LOWER BUTTON to resize the Frame or Dialog programmatically.
          Give the focus to the LOWER BUTTON and resize the Frame or Dialog manually.
          If the LOWER BUTTON always has focus after resize
          (for both frame and dialog) the test passes.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("PopupMenu Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(FocusChangeOnResizeTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static List<Window> createTestUI() {
        Frame frame = new Frame("FocusChangeOnResizeTest frame");
        Dialog dialog = new Dialog(frame, "Test dialog");
        frame.add(new TestPanel(frame));
        dialog.add(new TestPanel(dialog));
        frame.setBounds (150, 200, 200, 200);
        dialog.setBounds (150, 500, 200, 200);
        return List.of(frame, dialog);
    }

    static FocusListener eventLogger = new FocusListener() {
        public void focusGained(FocusEvent e) {
            PassFailJFrame.log(e.toString());
        }
        public void focusLost(FocusEvent e) {
           PassFailJFrame.log(e.toString());
        }
    };
}

class TestPanel extends Panel {

    TextField textField = new TextField("TEXT FIELD");
    Button button1 = new Button("UPPER BUTTON");
    Button button2 = new Button("LOWER BUTTON");

    public TestPanel(Window parent) {
        setLayout(new GridLayout(3, 1));
        add(textField);
        add(button1);
        add(button2);
        textField.setName("TEXT FIELD");
        button1.setName("UPPER BUTTON");
        button2.setName("LOWER BUTTON");
        textField.addFocusListener(FocusChangeOnResizeTest.eventLogger);
        button1.addFocusListener(FocusChangeOnResizeTest.eventLogger);
        button2.addFocusListener(FocusChangeOnResizeTest.eventLogger);

        button2.addActionListener(new Resizer(parent));
    }
}

class Resizer implements ActionListener {
    Window target;

    public Resizer(Window window) {
        target = window;
    }

    public void actionPerformed(ActionEvent e) {
        target.setSize(200, 100);
        target.doLayout();
        target.pack();
    }
}

