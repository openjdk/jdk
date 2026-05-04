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
 * @bug 6260650
 * @summary FileDialog.getDirectory() does not return null when file dialog is cancelled
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SavedDirInitTest
 */

public class SavedDirInitTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Click on 'Show File Dialog' button to bring up the FileDialog window.
                1) A file dialog will come up.
                2) Press 'Cancel' button to cancel the file dialog.
                3) The result (passed or failed) will be shown in the message window below.
                """;

        PassFailJFrame.builder()
                .title("SavedDirInitTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(SavedDirInitTest::createUI)
                .logArea(2)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("SavedDirInitTest Test");
        Button b = new Button("Show File Dialog");
        FileDialog fd = new FileDialog(f);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fd.setVisible(true);
                if (fd.getDirectory() == null && fd.getFile() == null) {
                    PassFailJFrame.log("TEST PASSED");
                } else {
                    PassFailJFrame.log("TEST FAILED. dir = " + fd.getDirectory()
                            + " , file = " + fd.getFile());
                }
            }
        });
        f.add(b);
        f.setSize(300, 200);
        return f;
    }
}
