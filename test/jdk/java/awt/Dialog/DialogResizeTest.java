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

import java.awt.Checkbox;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.Exception;
import java.lang.String;
import java.lang.System;

/*
 * @test
 * @bug 4115213
 * @summary Test to verify Checks that with resizable set to false,
 *          dialog can not be resized
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogResizeTest
 */

public class DialogResizeTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. When this test is run a dialog will display (setResizable Test)
                   Click on the checkbox to change the dialog resizable state
                2. For both dialog resizable states (resizable, non-resizable) try to
                   change the size of the dialog. When isResizable is true the dialog
                   is resizable. When isResizable is false the dialog is non-resizable
                3. If this is the behavior that you observe, the test has passed, Press
                   the Pass button. Otherwise the test has failed, Press the Fail button
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(initialize())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    public static Dialog initialize() {
        Frame f = new Frame("Owner Frame");
        MyDialog ld = new MyDialog(f);
        ld.setBounds(100, 100, 400, 150);
        ld.setResizable(false);
        System.out.println("isResizable is set to: " + ld.isResizable());
        return ld;
    }
}

class MyDialog extends Dialog implements ItemListener {
    String sText = "Tests java.awt.Dialog.setResizable method";
    TextArea ta = new TextArea(sText, 2, 40, TextArea.SCROLLBARS_NONE);

    public MyDialog(Frame f) {

        super(f, "setResizable test", false);

        Panel cbPanel = new Panel();
        cbPanel.setLayout(new FlowLayout());

        Panel taPanel = new Panel();
        taPanel.setLayout(new FlowLayout());
        taPanel.add(ta);

        Checkbox cb = new Checkbox("Check this box to change the dialog's " +
                "resizable state", null, isResizable());
        cb.setState(false);
        cb.addItemListener(this);
        cbPanel.add(cb);

        add("North", taPanel);
        add("South", cbPanel);
        pack();
    }

    public void itemStateChanged(ItemEvent evt) {
        setResizable(evt.getStateChange() == ItemEvent.SELECTED);

        boolean bResizeState = isResizable();
        PassFailJFrame.log("isResizable is set to: " + bResizeState);

        if (isResizable()) {
            ta.setText("dialog is resizable (isResizable = " + bResizeState + ")");
        } else {
            ta.setText("dialog is NOT resizable (isResizable = " + bResizeState + ")");
        }
    }
}
