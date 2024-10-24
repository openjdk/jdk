/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FileDialog;
import java.awt.Frame;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 6227750
 * @summary Tests that FileDialog can be closed by clicking the 'close' (X) button
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DoubleActionCloseX
 */

public class DoubleActionCloseX {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                NOTE: On Linux and Mac, there is no 'close'(X) button
                      when file dialog is visible, press Pass.

                Click the 'Open File Dialog' button to open FileDialog.
                A file dialog will appear on the screen.
                Click on the 'close'(X) button.
                The dialog should be closed.
                If not, the test failed, press Fail otherwise press Pass.
                """;

        PassFailJFrame.builder()
                .title("DoubleActionCloseX Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(DoubleActionCloseX::createUI)
                .build()
                .awaitAndCheck();
    }
    public static Frame createUI() {
        Frame f = new Frame("DoubleActionCloseX Test");
        Button b = new Button("Open File Dialog");
        FileDialog fd = new FileDialog(f);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fd.setVisible(true);
            }
        });
        f.add(b);
        f.setSize(300, 200);
        return f;
    }
}
