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
 * @bug 6259434
 * @summary PIT: Choice in FileDialog is not responding to keyboard interactions, XToolkit
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual KeyboardInteractionTest
 */

public class KeyboardInteractionTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("sun.awt.disableGtkFileDialogs", "true");
        String INSTRUCTIONS = """
                1) Click on 'Show File Dialog' button to bring up the FileDialog window.
                   A file dialog will come up.
                2) You will see a text field 'Enter full path or filename'.
                   Right next to it, you will see a button.
                   Transfer the focus on this button using 'TAB'.
                   Make sure that the popup choice is not shown.
                3) Press 'ESC'. If file dialog isn't disposed, then the test failed.
                4) Again, click on 'Show File Dialog' to bring up the file dialog.
                   A file dialog will come up.
                5) You will see a text field 'Enter full path or filename'.
                   Right next to it, you will see a button.
                   Click on this button. The popup choice will appear.
                6) Look at the popup choice. Change the current item in the popup
                   choice by the arrow keys.
                   If the text in the 'Enter full path or filename' text field isn't
                   changed, then the test failed.
                7) The test passed.
                """;

        PassFailJFrame.builder()
                .title("KeyboardInteractionTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(KeyboardInteractionTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("KeyboardInteractionTest Test");
        Button b = new Button("Show File Dialog");
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
