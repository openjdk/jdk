/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.TextField;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import jtreg.SkippedException;

/*
 * @test
 * @bug 6260676
 * @summary FileDialog.setDirectory() does not work properly, XToolkit
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual FileDialogReturnTest
 */

public class FileDialogReturnTest {

    private static JFrame initialize() {
        JFrame frame = new JFrame("File Dialog Return Test Frame");
        GridBagConstraints gbc = new GridBagConstraints();
        GridBagLayout grid = new GridBagLayout();
        frame.setLayout(grid);
        JTextArea textOutput = new JTextArea(8, 30);
        textOutput.setLineWrap(true);
        JScrollPane textScrollPane = new JScrollPane(textOutput);
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.WEST;
        frame.add(new Label("File:"), gbc);

        TextField fileField = new TextField("", 20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        frame.add(fileField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.WEST;
        frame.add(new Label("Dir:"), gbc);

        TextField dirField = new TextField("", 20);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        frame.add(dirField, gbc);

        Button button = new Button("Show");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.CENTER;
        frame.add(button, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        frame.add(textScrollPane, gbc);

        button.addActionListener(e -> {
            FileDialog fd = new FileDialog(frame);
            fd.setFile(fileField.getText());
            fd.setDirectory(dirField.getText());
            fd.setVisible(true);

            textOutput.append("[file=" + fd.getFile()+"]\n");
            textOutput.append("[dir=" + fd.getDirectory()+"]\n");
            textOutput.setCaretPosition(textOutput.getText().length());

        });
        frame.pack();
        return frame;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions = """
                1) The test shows the 'File Dialog Return Test Frame' frame
                   that contains two text fields, one button and an output area.
                2) Input something into the 'File:' text field or just keep the field empty.
                3) Input something into the 'Dir:' text field or just keep the field empty.
                4) Press the 'Show' button and a file dialog will appear.
                5-1) Cancel the file dialog, e.g. by selecting the 'close' menu item.
                     If the output window shows that 'file'/'dir' values are null
                     then the test passes, otherwise the test fails.
                5-2) Select any file by double clicking on it.
                     If the output window shows that 'file'/'dir' values are not-null
                     then the test passes, otherwise the test fails.
                """;

        String toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        if (!toolkit.equals("sun.awt.X11.XToolkit")) {
            throw new SkippedException("Test is not designed for toolkit " + toolkit);
        }

        PassFailJFrame.builder()
                .title("File Dialog Return Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(50)
                .testUI(FileDialogReturnTest::initialize)
                .build()
                .awaitAndCheck();
    }
}
