/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4232374
 * @summary Tests that dismissing a modal dialog does not enable
 *          disabled components
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual EnabledResetTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EnabledResetTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Press "Create Child" twice to create three windows
                   Verify that the parent windows are disabled
                2. Press "Create Modal Dialog"
                   Verify that the parent windows are disabled
                3. Press "enable"
                   Verify that no windows accept mouse events
                4. Press "ok"
                   Verify that the first window is still disabled
                   If all the verifications are done, then test is
                   PASSED, else test fails.
                   """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(new ChildDialog(1, null))
                .build()
                .awaitAndCheck();
    }
}

class ChildDialog extends Frame implements ActionListener {
    Window parent;
    int id;
    Button b, c, d;

    public ChildDialog(int frameNumber, Window myParent) {
        super();
        id = frameNumber;
        parent = myParent;

        setTitle("Frame Number " + id);

        b = new Button("Dismiss me");
        c = new Button("Create Child");
        d = new Button("Create Modal Dialog");

        setLayout(new BorderLayout());
        add("North", c);
        add("Center", d);
        add("South", b);
        pack();

        b.addActionListener(this);
        c.addActionListener(this);
        d.addActionListener(this);
    }

    public void setVisible(boolean b) {
        if (parent != null) {
            if (b) {
                parent.setEnabled(false);
            } else {
                parent.setEnabled(true);
                parent.requestFocus();
            }
        }

        super.setVisible(b);
    }

    public void dispose() {
        if (parent != null) {
            parent.setEnabled(true);
            parent.requestFocus();
        }
        super.dispose();
    }


    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == c) {
            (new ChildDialog(id + 1, this)).setVisible(true);
        } else if (evt.getSource() == d) {
            Dialog D = new Dialog(this, "Modal Dialog ");
            D.setLayout(new FlowLayout());
            Button b = new Button("ok");
            Button e = new Button("enable");
            D.add(b);
            D.add(e);
            D.setModal(true);
            D.pack();
            b.addActionListener(this);
            e.addActionListener(this);
            D.setVisible(true);
        } else if (evt.getSource() == b) {
            dispose();
        } else if (evt.getSource() instanceof Button) {
            if ("ok".equals(evt.getActionCommand())) {
                Button target = (Button) evt.getSource();
                Window w = (Window) target.getParent();
                w.dispose();
            }
            if ("enable".equals(evt.getActionCommand())) {
                parent.setEnabled(true);
            }
        }
    }
}
