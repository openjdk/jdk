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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4835633
 * @requires (os.family == "windows")
 * @summary Test various file selection scenarios
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileSelectionTests
 */

public class FileSelectionTests {
    private static final String INSTRUCTIONS = """
        This test is only for the Windows Look & Feel.
        This is a test of file selection/deselection using the mouse.
        There are quite a few steps. If any step doesn't behave as
        expected, press Fail else press Pass.

        Make sure that you are in a directory with at least a few files.
        Note that if you don't wait long enough between mouse buttons presses
         that the action will be interpreted as a double-click and will dismiss
         the dialog. Just re-show the dialog in this case.

        Press "Show Windows JFileChooser" button to show the JFileChooser.

        TEST 1:
            Click on a filename.  The file should become selected.
        TEST 2:
            Clear any selection. Click to right of a filename,
             in the space between the filename and the file's icon in the next column.
             The file should NOT be selected.  If it becomes selected, press Fail.
        TEST 3:
            Select a filename. As in TEST 2, click in the empty space to the right of
             the filename. The file should be deselected.
        TEST 4:
            Clear any selection. If necessary, resize the file dialog and/or change to
             a directory with only a couple files, so that there is some space between
             the list of files and the bottom of the file pane.
            Click below the file list, in the empty space between the last file and
             bottom of the file pane. The last file in the column above the cursor
             should NOT become selected. If any file becomes selected, press Fail.
        TEST 5:
            Select a file. As in TEST 4, click in the empty space below the file list.
            The selected file should become deselected.
        TEST 6:
            Clear any selection. As in TEST 4, click below the file list.
            Then click on the last filename in the list. It should NOT go into edit mode.
        TEST 7:
            Clear any selection. Double-click below file list. The dialog should not be
             dismissed, and no exception should be thrown.
        TEST 8:
            Clear any selection. As in TEST 2, press the mouse button in the empty space
             to the right of a filename, but this time drag the mouse onto the filename.
            The file should NOT become selected.
        TEST 9:
            Clear any selection. As in TEST 4, press the mouse button in the empty space
             below the file list, but this time drag onto the last filename in the column.
            The file should NOT become selected.
        TEST 10:
            Click on a filename, and then click again to go into rename mode.
            Modify the filename, and then click to the right of the edit box.
            The filename should be the new filename.
        TEST 11:
            As in TEST 10, rename a file, but this time end the editing by clicking below
             the file list.  Again, the file should retain the new name.
        TEST 12:
            Use shift-click to select several files.  Hold "shift down" and click in
             (1) the empty space to the right of a file name and
             (2) in the empty space below the list of files.
            The files should remain selected. If the selection is cleared press Fail.
        TEST 13:
            Switch to Details view. Repeat TESTS 1-11.
        TEST 14:
            Details view. Clear any selection. Click in the Size column.
            No file should become selected.
        TEST 15:
            Details view. Select a file. Click in the Size column.
            The file should be deselected.
        TEST 16:
            Details view. Shift-click to select several files. Shift-click in
             (1) the empty space to the right of a filename
             (2) in the Size column and
             (3) below the list of files.
            The files should remain selected. If the selection is cleared, press Fail. """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        PassFailJFrame.builder()
                .title("JFileChooser Instructions")
                .instructions(INSTRUCTIONS)
                .rows(25)
                .columns(50)
                .testTimeOut(10)
                .splitUI(FileSelectionTests::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    public static JPanel createAndShowUI() {
        JButton button = new JButton("Show Windows JFileChooser");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser jfc = new JFileChooser();
                jfc.setMultiSelectionEnabled(true);
                jfc.showOpenDialog(null);
            }
        });
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.setSize(200, 200);
        p.add(button);
        return p;
    }
}
