/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Container;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4105025 4153487 4177107 4146229 4119383 4181310 4152317
 * @summary Test: FileDialogTest
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogTest
 */

public class FileDialogTest extends Panel implements ActionListener {
    Button buttonShow, buttonNullShow, buttonShowHide, buttonShowDispose;
    TextField fieldFile;
    TextField fieldDir;
    TextField fieldTitle;
    private static final String INSTRUCTIONS = """
             1. Set file, directory, and title fields to some real values
                Title will not show on macos dialog
             2. Click the "Get File..." button.
             3. Verify that dialog is set to proper file and directory, and that
                title is also set.
             4. Select a file and OK the dialog
                (or whatever the selection button is).
             5. Verify that the file and directory fields reflect the file chosen.
             6. Now, click the "Get null File with null Directory..." button.
             7. Verify that the file list matches the listed directory.
             8. Cancel or OK the dialog.
             9. Verify that no NullPointerException is thrown.
            10. Now, click the "Show FileDialog, then hide() in 5 s..." button.
            11. Wait for 5 seconds. The FileDialog should then
                disappear automatically.
            12. 12-14 are Windows specific. Set file to some invalid value,
                like "/<>++".
            13. Click the "Get File..." button.
            14. Verify that FileDialog is shown with empty "file" field.
            15. Run the test on different locales. Verify that filter string
                "All Files" is localized.
            """;

    public static void main(String args[]) throws Exception {
        Frame frame = new Frame("FileDialogTest");
        frame.setLayout(new GridLayout());
        frame.add(new FileDialogTest());
        frame.pack();

        PassFailJFrame.builder()
                .title("FileDialogTest")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(frame)
                .build()
                .awaitAndCheck();
    }

    public FileDialogTest() {
        setLayout(new GridLayout(6, 2));

        buttonShow = new Button("Get File...");
        add(buttonShow);
        buttonNullShow = new Button("Get null File with null Directory...");
        add(buttonNullShow);
        buttonShowHide = new Button("Show FileDialog, then hide() in 5 s...");
        add(buttonShowHide);
        buttonShowDispose =
                new Button("Show FileDialog, then dispose() in 5 s...");
        add(buttonShowDispose);

        add(new Label(""));
        add(new Label(""));

        add(new Label("File:"));
        fieldFile = new TextField(20);
        add(fieldFile);

        add(new Label("Directory:"));
        fieldDir = new TextField(20);
        add(fieldDir);

        add(new Label("Title:"));
        fieldTitle = new TextField(20);
        fieldTitle.setText("TestTitle");
        add(fieldTitle);

        buttonShow.addActionListener(this);
        buttonNullShow.addActionListener(this);
        buttonShowHide.addActionListener(this);
        buttonShowDispose.addActionListener(this);
    }

    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == buttonShow) {
            FileDialog fd = new FileDialog(getFrame(), fieldTitle.getText());
            fd.setFile(fieldFile.getText());
            fd.setDirectory(fieldDir.getText());
            fd.show();
            System.out.println("back from show");
            fieldFile.setText(fd.getFile());
            fieldDir.setText(fd.getDirectory());
            fd.dispose();
        } else if (evt.getSource() == buttonNullShow) {
            FileDialog fd = new FileDialog(getFrame(), fieldTitle.getText());
            fd.setFile(null);
            fd.setDirectory(null);
            fd.show();
            System.out.println("back from show");
            fieldFile.setText(fd.getFile());
            fieldDir.setText(fd.getDirectory());
            fd.setFile(null);
            fd.setDirectory(null);
            fd.dispose();
        } else if (evt.getSource() == buttonShowHide) {
            final FileDialog fd = new FileDialog(getFrame(),
                    fieldTitle.getText());
            fd.setFile(fieldFile.getText());
            fd.setDirectory(fieldDir.getText());
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.currentThread().sleep(5000);
                    } catch (InterruptedException ex) {
                    }
                    fd.hide();
                }
            }).start();
            fd.show();
            System.out.println("back from show");
            fd.dispose();
        } else if (evt.getSource() == buttonShowDispose) {
            final FileDialog fd = new FileDialog(getFrame(),
                    fieldTitle.getText());
            fd.setFile(fieldFile.getText());
            fd.setDirectory(fieldDir.getText());
            new Thread(() -> {
                try {
                    Thread.currentThread().sleep(5000);
                } catch (InterruptedException ex) {
                }
                fd.dispose();
            }).start();
            fd.show();
            System.out.println("back from show");
            fd.dispose();
        }
    }

    private Frame getFrame() {
        Container cont = getParent();
        while (cont != null) {
            if (cont instanceof Frame) {
                return (Frame) cont;
            }
            cont = cont.getParent();
        }
        return null;
    }
}
