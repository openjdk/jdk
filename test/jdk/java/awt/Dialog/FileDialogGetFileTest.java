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

/*
 * @test
 * @bug 4414105
 * @summary Tests that FileDialog returns null when cancelled
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogGetFileTest
 */

import java.awt.Button;
import java.awt.FileDialog;
import java.awt.Frame;

public class FileDialogGetFileTest {
    static FileDialog fd;
    static Frame frame;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Open FileDialog from "Show File Dialog" button.
                2. Click cancel button without selecting any file/folder.
                3. If FileDialog.getFile return null then test PASSES,
                   else test FAILS automatically.
                   """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .logArea(4)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        frame = new Frame("FileDialog GetFile test");
        fd = new FileDialog(frame);
        fd.setFile("FileDialogGetFileTest.html");
        fd.setBounds(100, 100, 400, 400);
        Button showBtn = new Button("Show File Dialog");
        frame.add(showBtn);
        frame.pack();
        showBtn.addActionListener(e -> {
            fd.setVisible(true);
            if (fd.getFile() != null) {
                PassFailJFrame.forceFail("Test failed: FileDialog returned non-null value");
            } else {
                PassFailJFrame.log("Test Passed!");
            }
        });
        return frame;
    }
}
