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
import java.awt.Checkbox;
import java.awt.Component;
import java.awt.Container;
import java.awt.Event;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.io.File;
import java.io.FilenameFilter;

/*
 * @test
 * @bug 4293697 4416433 4417139 4409600
 * @summary Test to verify that user filter always gets called on changing the
 *          directory in FileDialog
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogUserFilterTest
 */

public class FileDialogUserFilterTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                        1. Enter a mask into the <filter> field, a directory into
                           the <directory> field (or leave the default values).
                        2. Then click the <Load> button, file dialog will appear.
                           Output of the user filter will be shown in the output
                           area. Enter several different directories to the file dialog
                           via double-clicking on the directory list. The output
                           area should show some filtering output on each directory
                           change. If any output was only given on dialog startup,
                           the test is FAILED.
                        3. Look at the list of files accepted by the filter.
                           If some files do not match the filter,
                           the test is FAILED.
                        4. Open dialog with an empty filter.
                           Enter some directories with a lot of files (like /usr/bin).
                           If dialog crashes the test is FAILED.
                           Enter the directory that contain files and other directories.
                           If the directories are shown in the files box along with files
                           then the test is FAILED.
                        5. Click in checkbox 'do not use filter', make it checked.
                           Open dialog, enter the directory with some files.
                           If no files is shown in the File list box (while you are sure
                           there are some files there) the test is FAILED
                           Otherwise it is PASSED."
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(new DialogFilterTest())
                .build()
                .awaitAndCheck();
    }
}

class DialogFilterTest extends Frame implements FilenameFilter {
    FileDialog fd;
    static TextField tfDirectory = new TextField();
    static TextField tfFile = new TextField();
    static TextField tfFilter = new TextField();
    static Checkbox useFilterCheck = new Checkbox("do not use filter");

    public DialogFilterTest() {
        setTitle("File Dialog User Filter test");
        add("North", new Button("Load"));
        Panel p = new Panel();
        p.setLayout(new GridBagLayout());
        addRow(p, new Label("directory:", Label.RIGHT), tfDirectory);
        addRow(p, new Label("file:", Label.RIGHT), tfFile);
        addRow(p, new Label("filter:", Label.RIGHT), tfFilter);
        addRow(p, new Label(""), useFilterCheck);
        tfFilter.setText(".java");
        tfDirectory.setText(".");
        add("Center", p);
        setSize(300, 200);
    }

    static void addRow(Container cont, Component c1, Component c2) {
        GridBagLayout gbl = (GridBagLayout) cont.getLayout();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        cont.add(c1);
        gbl.setConstraints(c1, c);

        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1.0;
        cont.add(c2);
        gbl.setConstraints(c2, c);
    }

    public boolean accept(File dir, String name) {
        System.out.println("File " + dir + " String " + name);
        if (fd.getMode() == FileDialog.LOAD) {
            return name.lastIndexOf(tfFilter.getText()) > 0;
        }
        return true;
    }

    public boolean action(Event evt, Object what) {
        boolean load = "Load".equals(what);

        if (load || "Save".equals(what)) {
            fd = new FileDialog(new Frame(), null,
                    load ? FileDialog.LOAD : FileDialog.SAVE);
            fd.setDirectory(tfDirectory.getText());
            fd.setFile(tfFile.getText());
            if (!useFilterCheck.getState()) {
                fd.setFilenameFilter(this);
            }
            fd.setVisible(true);
            tfDirectory.setText(fd.getDirectory());
            tfFile.setText(fd.getFile());

            return true;
        }
        return false;
    }
}
