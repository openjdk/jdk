/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TestUI {
    public static class DialogBuilder {
        private JDialog dialog;
        private JTextArea instructionsText;
        private JTextArea messageText;
        private JButton pass;
        private JButton fail;

        public DialogBuilder() {
            dialog = new JDialog(new JFrame());
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            instructionsText = new JTextArea("", 5, 100);

            dialog.add("North", new JScrollPane(instructionsText,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS));

            messageText = new JTextArea("", 20, 100);
            dialog.add("Center", new JScrollPane(messageText,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS));

            JPanel buttons = new JPanel();
            pass = new JButton("pass");
            pass.setActionCommand("pass");
            buttons.add("East", pass);

            fail = new JButton("fail");
            fail.setActionCommand("fail");
            buttons.add("West", fail);

            dialog.add("South", buttons);
        }

        public DialogBuilder setTitle(String title) {
            dialog.setTitle(title);
            return this;
        }

        public DialogBuilder setInstruction(String instruction) {
            instructionsText.setText("Test instructions:\n" + instruction);
            return this;
        }

        public DialogBuilder setMessage(String message) {
            messageText.setText(message);
            return this;
        }

        public DialogBuilder setPassAction(ActionListener action) {
            pass.addActionListener(action);
            return this;
        }

        public DialogBuilder setFailAction(ActionListener action) {
            fail.addActionListener(action);
            return this;
        }

        public DialogBuilder setCloseAction(Runnable action) {
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    super.windowClosing(e);
                    action.run();
                }
            });
            return this;
        }

        public JDialog build() {
            dialog.pack();
            return dialog;
        }
    }
}
