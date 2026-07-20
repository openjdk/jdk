/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GridLayout;

/*
 * @test
 * @bug 4172302
 * @summary Test to make sure non-resizable Dialogs can be resized with the
 *          setSize() method.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogResizeTest2
 */

public class DialogResizeTest2 {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This tests the programmatic resizability of non-resizable Dialogs
                Even when a Dialog is set to be non-resizable, it should be
                programmatically resizable using the setSize() method.

                1. Initially the Dialog will be resizable.  Try using the \\"Smaller\\"
                   and \\"Larger\\" buttons to verify that the Dialog resizes correctly
                2. Then, click the \\"Toggle\\" button to make the Dialog non-resizable
                3. Again, verify that clicking the \\"Larger\\" and \\"Smaller\\" buttons
                    causes the Dialog to get larger and smaller.  If the Dialog does
                    not change size, or does not re-layout correctly, the test FAILS
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("Parent Frame");
        frame.add(new Button("Button"));
        frame.setSize(100, 100);
        new dlg(frame).setVisible(true);
        return frame;
    }

    static class dlg extends Dialog {
        public dlg(Frame f_) {
            super(f_, "Dialog", false);
            setSize(200, 200);
            Button bLarger = new Button("Larger");
            bLarger.addActionListener(e -> setSize(400, 400));
            Button bSmaller = new Button("Smaller");
            bSmaller.addActionListener(e -> setSize(200, 100));
            Button bCheck = new Button("Resizable?");
            bCheck.addActionListener(e -> {
                if (isResizable()) {
                    PassFailJFrame.log("Dialog is resizable");
                } else {
                    PassFailJFrame.log("Dialog is not resizable");
                }
            });
            Button bToggle = new Button("Toggle");
            bToggle.addActionListener(e -> {
                if (isResizable()) {
                    setResizable(false);
                    PassFailJFrame.log("Dialog is now not resizable");
                } else {
                    setResizable(true);
                    PassFailJFrame.log("Dialog is now resizable");
                }
            });
            setLayout(new GridLayout(1, 4));
            add(bSmaller);
            add(bLarger);
            add(bCheck);
            add(bToggle);
        }
    }
}
