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
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/*
 *@test
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
    static JFrame frame = null;
    static Boolean bMultiSel_Enabled = false;
    static JFileChooser jfc = null;
    static JPanel panel = null;
    static JTextArea textArea = null;
    static JCheckBox checkMSelection = null;
    static PassFailJFrame passFailJFrame = null;

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
                cd /d C:\
                mkdir FileChooserTest
                cd FileChooserTest
                mkdir target
                mklink /d link FileChooserTest

                3. Navigate to C:\\FileChooserTest in the JFileChooser.
                4. Use "Enable Multi-Selection" checkbox to enable/disable
                   MultiSelection Mode
                5. MultiSelection Mode disabled - On click of the "link"
                   directory, if the Absolute path of Symbolic Link is
                   valid then Click PASS, else if it is null then Click FAIL.
                   MultiSelection Mode Enabled - If Multi selection of
                   directories including Symbolic Link is possible along
                   with valid Absolute path then Click PASS, else either
                   of them failed click FAIL.
                6. When done with testing, paste the following commands to remove the 'filechooser' directory
                cd \
                rmdir /s /q filechooser

                or use File Explorer to clean it up.
                """;
        frame = new JFrame("JFileChooser Symbolic Link test");
        panel = new JPanel(new BorderLayout());
        checkMSelection = new JCheckBox("Enable Multi-Selection");
        textArea = new JTextArea();
        jfc = new JFileChooser();
        passFailJFrame = new PassFailJFrame(INSTRUCTIONS);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        panel.add(checkMSelection,BorderLayout.EAST);
        panel.add(textArea,BorderLayout.WEST);
        jfc.setDialogType(JFileChooser.CUSTOM_DIALOG);
        jfc.setControlButtonsAreShown(false);
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jfc.setMultiSelectionEnabled(bMultiSel_Enabled);
        textArea.setPreferredSize(new Dimension(600,300));
        textArea.append("Path List");

        checkMSelection.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object source = e.getSource();
                if (((JCheckBox)source).isSelected()) {
                    bMultiSel_Enabled = true;
                } else {
                    bMultiSel_Enabled = false;
                }
                jfc.setMultiSelectionEnabled(bMultiSel_Enabled);
            }
        });
        jfc.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                    System.out.println("Absolute Path : "+evt.getNewValue());
                    textArea.append("\nAbsolute Path : "+evt.getNewValue());
                }
            }
        });
        frame.add(panel,BorderLayout.NORTH);
        frame.add(jfc,BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
}
