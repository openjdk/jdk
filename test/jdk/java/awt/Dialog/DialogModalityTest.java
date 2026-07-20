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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Event;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Window;
import java.util.List;

/*
 * @test
 * @bug 4058370
 * @summary Test to verify Modality of Dialog
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogModalityTest
 */

public class DialogModalityTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. When the test is running, there will be a Frame, a Modal Dialog
                   and a Window that is Modal Dialog's parent.
                2. Verify that it is impossible to bring up the menu in Frame before
                   closing the Modal Dialog.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static List<Window> initialize() {
        Frame f = new Frame("Parent Frame");
        DialogTest dlg = new DialogTest(f, "Modal Dialog");
        f.add(new Button("push me"));
        f.setSize(200, 200);
        f.setLocation(210, 1);
        dlg.setBounds(210, 203, 200, 200);
        return List.of(f, dlg);
    }
}

class DialogTest extends Dialog {
    Button closeButton;
    Frame parent;

    public DialogTest(Frame parent, String title) {
        this(parent, title, true);
    }

    public DialogTest(Frame parent, String title, boolean modal) {
        super(parent, title, modal);
        this.parent = parent;
        setLayout(new BorderLayout());
        Panel buttonPanel = new Panel();
        closeButton = new Button("Close");
        buttonPanel.add(closeButton);
        add("Center", buttonPanel);
        pack();
    }

    public boolean action(Event e, Object arg) {
        if (e.target == closeButton) {
            Dialog dialog = null;
            Component c = (Component) e.target;

            while (c != null && !(c instanceof Dialog)) {
                c = c.getParent();
            }

            if (c != null) {
                dialog = (Dialog) c;
            }

            if (dialog == null) {
                return false;
            }

            dialog.setVisible(false);
            dialog.dispose();
            return true;
        }
        return false;
    }
}
