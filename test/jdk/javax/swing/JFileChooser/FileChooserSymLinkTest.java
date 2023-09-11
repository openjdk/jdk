/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/*
 * @test
 * @bug 8281966
 * @key headful
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if the absolute path of Symbolic Link folder
 *          is valid on ValueChanged property listener.
 * @run main/manual FileChooserSymLinkTest
 */
public class FileChooserSymLinkTest {
    static JFrame frame;
    static JFileChooser jfc;
    static JPanel panel;
    static JTextArea pathList;
    static JCheckBox multiSelection;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    initialize();
                } catch (InterruptedException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    static void initialize() throws InterruptedException, InvocationTargetException {
        //Initialize the components
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Open an elevated Command Prompt.
                2. Paste the following commands:
                cd /d C:\\
                mkdir FileChooserTest
                cd FileChooserTest
                mkdir target
                mklink /d link target

                3. Navigate to C:\\FileChooserTest in the JFileChooser.
                4. Use "Enable Multi-Selection" checkbox to enable/disable
                   MultiSelection Mode
                5. Single-selection:
                   Click "link" directory, the absolute path of the symbolic
                   link should be displayed. If it's null, click FAIL.
                   Click "target" directory, its absolute path should be
                   displayed.

                   Enable multiple selection by clicking the checkbox.
                   Multi-selection:
                   Click "link", press Ctrl and then click "target".
                   Both should be selected and their absolute paths should be
                   displayed.

                   If "link" can't be selected or if its absolute path is null,
                   click FAIL.

                   If "link" can be selected in both single- and multi-selection modes,
                   click PASS.
                6. When done with testing, paste the following commands to
                   remove the 'FileChooserTest' directory:
                cd \\
                rmdir /s /q C:\\FileChooserTest

                or use File Explorer to clean it up.
                """;
        frame = new JFrame("JFileChooser Symbolic Link test");
        panel = new JPanel(new BorderLayout());
        multiSelection = new JCheckBox("Enable Multi-Selection");
        pathList = new JTextArea(10, 50);
        jfc = new JFileChooser(new File("C:\\"));
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS, 5L, 35, 40);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        panel.add(multiSelection, BorderLayout.EAST);
        panel.add(new JScrollPane(pathList), BorderLayout.WEST);
        jfc.setDialogType(JFileChooser.CUSTOM_DIALOG);
        jfc.setControlButtonsAreShown(false);
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jfc.setMultiSelectionEnabled(multiSelection.isSelected());
        pathList.append("Path List\n");

        multiSelection.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object source = e.getSource();
                jfc.setMultiSelectionEnabled(((JCheckBox)source).isSelected());
            }
        });
        jfc.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String msg = null;
                if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                    msg = "Absolute Path : " + evt.getNewValue();
                } else if (JFileChooser.SELECTED_FILES_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                    msg = "Selected Files : " + Arrays.toString((File[]) evt.getNewValue());
                }

                if (msg != null) {
                    System.out.println(msg);
                    pathList.append(msg + "\n");
                }
            }
        });
        frame.add(panel, BorderLayout.NORTH);
        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
}
