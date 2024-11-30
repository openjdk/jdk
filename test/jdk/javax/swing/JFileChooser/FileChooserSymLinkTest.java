/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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
    private static final String INSTRUCTIONS = """
            <html><body>
            Instructions to Test:
            <ol>
            <li>Open an elevated <i>Command Prompt</i>.
            <li>Paste the following commands:
            <pre><code>cd /d C:\\
            mkdir FileChooserTest
            cd FileChooserTest
            mkdir target
            mklink /d link target</code></pre>

            <li>Navigate to <code>C:\\FileChooserTest</code> in
                the <code>JFileChooser</code>.
            <li>Perform testing in single- and multi-selection modes:
                <ul style="margin-bottom: 0px">
                <li><strong>Single-selection:</strong>
                    <ol>
                    <li>Ensure <b>Enable multi-selection</b> is cleared
                        (the default state).
                    <li>Click <code>link</code> directory,
                        the absolute path of the symbolic
                        link should be displayed.<br>
                        If it's <code>null</code>, click <b>Fail</b>.
                    <li>Click <code>target</code> directory,
                        its absolute path should be displayed.
                    </ol>
                <li><strong>Multi-selection:</strong>
                    <ol>
                    <li>Select <b>Enable multi-selection</b>.
                    <li>Click <code>link</code>,
                    <li>Press <kbd>Ctrl</kbd> and
                        then click <code>target</code>.
                    <li>Both should be selected and
                        their absolute paths should be displayed.
                    <li>If <code>link</code> can't be selected or
                        if its absolute path is <code>null</code>,
                        click <b>Fail</b>.
                    </ol>
                </ul>
                <p>If <code>link</code> can be selected in both
                single- and multi-selection modes, click <b>Pass</b>.</p>
            <li>When done with testing, paste the following commands to
                remove the <code>FileChooserTest</code> directory:
            <pre><code>cd \\
            rmdir /s /q C:\\FileChooserTest</code></pre>

            or use File Explorer to clean it up.
            </ol>
            """;

    static JFrame frame;
    static JFileChooser jfc;
    static JPanel panel;
    static JTextArea pathList;
    static JCheckBox multiSelection;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(35)
                      .columns(50)
                      .testUI(FileChooserSymLinkTest::createTestUI)
                      .build()
                      .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        frame = new JFrame("JFileChooser Symbolic Link test");
        panel = new JPanel(new BorderLayout());
        multiSelection = new JCheckBox("Enable Multi-Selection");
        pathList = new JTextArea(10, 50);
        jfc = new JFileChooser(new File("C:\\"));

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
        return frame;
    }
}
