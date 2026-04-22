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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/*
 * @test
 * @bug 4078176
 * @summary Test to verify Modal dialogs don't act modal if addNotify()
 *          is called before setModal(true).
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ModalDialogTest
 */

public class ModalDialogTest implements ActionListener {
    public boolean modal = true;
    Button closeBtn = new Button("Close me");
    Button createBtn = new Button("Create Dialog");
    Button createNewBtn = new Button("Create Modal Dialog");
    Button lastBtn = new Button("Show Last Dialog");
    Dialog dialog;
    Dialog newDialog;
    Frame testFrame;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Use 'Modal' checkbox to select which dialog you're
                   going to create - modal or non-modal.
                   (this checkbox affects only new created dialog but
                   not existing one)
                2. Use 'Create Dialog' button to create a dialog.
                   If you have selected 'Modal' checkbox then dialog has to
                   be created modal - you can make sure of that clicking
                   on any other control (i.e. 'Modal' checkbox) - they
                   should not work.
                3. Use 'Show Last Dialog' button to bring up last
                   created dialog - to make sure that if you show/hide
                   modal dialog several times it stays modal.
                4. On the appearing dialog there are two buttons:
                   'Close Me' which closes the dialog,
                   and 'Create Modal Dialog' which creates one more
                   MODAL dialog just to make sure that
                   in situation with two modal dialogs all is fine.
                5. If created modal dialogs are really modal
                   (which means that they blocks the calling app)
                   then test is PASSED, otherwise it's FAILED."
                                 """;
        ModalDialogTest test = new ModalDialogTest();
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(test.initialize())
                .build()
                .awaitAndCheck();
    }

    public Frame initialize() {
        testFrame = new Frame("Parent Frame");
        Frame frame = new Frame("Modal Dialog test");
        Panel panel = new Panel();
        panel.setLayout(new BorderLayout());

        createBtn.addActionListener(this);
        createNewBtn.addActionListener(this);
        closeBtn.addActionListener(this);
        lastBtn.addActionListener(this);
        panel.add("Center", createBtn);
        panel.add("South", lastBtn);
        Checkbox cb = new Checkbox("Modal", modal);
        cb.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                modal = ((Checkbox) e.getSource()).getState();
            }
        });
        panel.add("North", cb);
        panel.setSize(200, 100);

        frame.add(panel);
        frame.pack();
        return frame;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == createBtn) {
            if (dialog != null) {
                dialog.dispose();
            }
            dialog = new Dialog(testFrame, "Modal Dialog");
            dialog.add("North", closeBtn);
            dialog.add("South", createNewBtn);
            createBtn.setEnabled(false);
            dialog.pack();
            dialog.setModal(modal);
            dialog.setVisible(true);
        } else if (e.getSource() == closeBtn && dialog != null) {
            createBtn.setEnabled(true);
            dialog.setVisible(false);
        } else if (e.getSource() == lastBtn && dialog != null) {
            dialog.setVisible(true);
        } else if (e.getSource() == createNewBtn && newDialog == null) {
            newDialog = new Dialog(testFrame, "New Modal Dialog");
            Button clsBtn = new Button("Close Me");
            clsBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    newDialog.dispose();
                    newDialog = null;
                }
            });
            newDialog.add("North", clsBtn);
            newDialog.pack();
            newDialog.setModal(true);
            newDialog.setVisible(true);
        }
    }
}
