/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4859390
 * @requires (os.family == "windows")
 * @summary Verify that FileDialog matches the look
    of the native windows FileDialog
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogUIUpdate
 */

public class FileDialogUIUpdate extends Frame {
    static String instructions = """
            Click the button to show the FileDialog. Then open the Paint
            application (usually found in Program Files->Accessories).
            Select File->Open from Paint to display a native Open dialog.
            Compare the native dialog to the AWT FileDialog.
            Specifically, confirm that the Places Bar icons are along the left side (or
            not, if the native dialog doesn't have them), and that the
            dialogs are both resizable (or not).
            If the file dialogs both look the same press Pass.  If not,
            press Fail.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FileDialogUIUpdate")
                .instructions(instructions)
                .testTimeOut(5)
                .rows(12)
                .columns(35)
                .testUI(FileDialogUIUpdate::new)
                .build()
                .awaitAndCheck();
    }

    public FileDialogUIUpdate() {
        final FileDialog fd = new FileDialog(new Frame("FileDialogUIUpdate frame"),
                "Open FileDialog");
        Button showButton = new Button("Show FileDialog");
        setLayout(new BorderLayout());

        fd.setDirectory("c:/");
        showButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fd.setVisible(true);
            }
        });

        add(showButton);
        setSize(200, 200);
    }
}
