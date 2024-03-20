/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/*
 * @test
 * @bug 7161437
 * @summary We should support "apple.awt.fileDialogForDirectories" property.
 * @requires (os.family == "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogForDirectories
 */

public class FileDialogForDirectories {

    private static JFrame initialize() {
        System.setProperty("apple.awt.fileDialogForDirectories", "true");

        JFrame frame = new JFrame("Directory File Dialog Test Frame");
        frame.setLayout(new BorderLayout());
        JTextArea textOutput = new JTextArea(8, 30);
        textOutput.setLineWrap(true);
        JScrollPane textScrollPane = new JScrollPane(textOutput);
        frame.add(textScrollPane, BorderLayout.CENTER);

        FileDialog fd = new FileDialog(new Frame(), "Open");

        Button showBtn = new Button("Show File Dialog");
        showBtn.addActionListener(e -> {
            fd.setVisible(true);
            String output = fd.getFile();
            if (output != null) {
                textOutput.append(output + " is selected\n");
                textOutput.setCaretPosition(textOutput.getText().length());
            }
        });
        frame.add(showBtn, BorderLayout.NORTH);
        frame.pack();
        return frame;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions = """
                1) Click on 'Show File Dialog' button. A file dialog will come up.
                2) Check that files can't be selected.
                3) Check that directories can be selected.
                4) Repeat steps 1 - 3 a few times for different files and directories.
                5) If it's true then the press Pass, otherwise press Fail.
                """;

        PassFailJFrame.builder()
                .title("Directory File Dialog Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(40)
                .testUI(FileDialogForDirectories::initialize)
                .build()
                .awaitAndCheck();
    }
}
