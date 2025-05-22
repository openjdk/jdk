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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/*
 * @test
 * @bug 4835479
 * @requires (os.family == "windows")
 * @summary JFileChooser should respect native setting for showing hidden files
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ShowHiddenFiles
 */

public class ShowHiddenFiles
{
    private static final String INSTRUCTIONS = """
        This tests JFileChooser's ability to track the native setting for
        displaying of hidden files.
        This test has four parts. If any portion of any of the tests don't
        behave as specified, press FAIL else press PASS.
        Before beginning the tests, you'll want to find the Folder Options
        dialog on your Windows platform. Open an Explorer window for c:/
        and select Tools->Folder Options. Under the View tab, locate
        the option to show hidden files. You will need this for the tests.

        TEST 1:
            This tests that JFileChooser tracks the native platform setting.
            Configure windows to Show Hidden Files, and in an Explorer window
            locate a hidden file that is now shown (there are usually hidden
            files in c:/).
            Click on the TEST 1 button to display a JFileChooser.
            Confirm that the hidden files are shown in the JFileChooser.
            On Windows 98, TEST 1 is now complete.
            On Windows 2000 and later, configure Folder Options to hide hidden
            files. Confirm that
                (1) the files are hidden in the JFileChooser and
                (2) "PropertyChangeEvent for FILE_HIDING_CHANGED_PROPERTY"
            appears in the accessory text field.
            Re-enable showing of hidden files and confirm that
                (1) the hidden files are again shown and
                (2) you get another PropertyChangeEvent.
            Press "Cancel" button to close JFileChooser window.

        TEST 2:
            This tests that JFileChooser.setFileHidingEnabled(true) overrides the
            native platform setting.
            Make sure Windows is configured to Show Hidden Files.
            Click on the TEST 2 button.
            Confirm that hidden files are NOT displayed in the JFileChooser.
            Press "Cancel" button to close JFileChooser window.

        TEST 3:
            This tests that JFileChooser.setFileHidingEnabled(false) overrides the
            Make sure Windows is configured to NOT show hidden files.
            Click on the TEST 3 button.
            Confirm that hidden files ARE displayed in the JFileChooser.
            Press "Cancel" button to close JFileChooser window.

        TEST 4:
            This tests that calling setFileHidingEnabled() on a showing
            JFileChooser will cause it to ignore further changes in the
            native platform setting.
            Click on the TEST 4 button. As in TEST 1, confirm that the
            JFileChooser tracks the native setting.
            Click on the "Show Hidden Files" button.
            Confirm that hidden files remain visible, even when you change
            the native setting.
            Repeat the test for the "Hide Hidden Files" button.
            Press "Cancel" button to close JFileChooser window.
        """;
    private static JButton test1, test2, test3, test4;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("JFileChooser Instructions")
            .instructions(INSTRUCTIONS)
            .rows(25)
            .columns(50)
            .splitUI(ShowHiddenFiles::createAndShowUI)
            .build()
            .awaitAndCheck();
    }

    public static JPanel createAndShowUI() {
        test1 = new JButton("TEST 1: Track native setting");
        test2 = new JButton("TEST 2: setFileHidingEnabled(true)");
        test3 = new JButton("TEST 3: setFileHidingEnabled(false)");
        test4 = new JButton("TEST 4: setFileHidingEnabled() on showing JFC");

        ButtonListener bl = new ButtonListener();
        test1.addActionListener(bl);
        test2.addActionListener(bl);
        test3.addActionListener(bl);
        test4.addActionListener(bl);

        JPanel p = new JPanel();
        p.setLayout(new GridLayout(4,1));
        p.setSize(200, 200);
        p.add(test1);
        p.add(test2);
        p.add(test3);
        p.add(test4);
        return p;
    }

    private static class ButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JFileChooser jfc = new JFileChooser("c:/");
            if (e.getSource() == test1) {
                jfc.setAccessory(createTest1Acc(jfc));
            }
            else if (e.getSource() == test2) {
                jfc.setAccessory(null);
                jfc.setFileHidingEnabled(true);
            }
            else if (e.getSource() == test3) {
                jfc.setAccessory(null);
                jfc.setFileHidingEnabled(false);
            }
            else if (e.getSource() == test4) {
                jfc.setAccessory(createTest4Acc(jfc));
            }
            else {
                return;
            }
            jfc.showOpenDialog(new JFrame());
        }
    }

    private static class JFCHideButton extends JButton implements ActionListener {
        JFileChooser jfc;
        boolean setVal;

        public JFCHideButton(String label, JFileChooser jfc, boolean setVal) {
            super(label);
            this.jfc = jfc;
            this.setVal = setVal;
            addActionListener(this);
        }
        public void actionPerformed(ActionEvent e) {
            jfc.setFileHidingEnabled(setVal);
        }
    }

    private static JPanel createTest1Acc(JFileChooser jfc) {
        JPanel jpl = new JPanel();
        jpl.add(createTAListener(jfc));
        return jpl;
    }

    private static JPanel createTest4Acc(JFileChooser jfc) {
        JPanel jpl = new JPanel();
        jpl.setLayout(new BorderLayout());

        JPanel north = new JPanel();
        north.setLayout(new GridLayout(2,1));
        north.add(new JFCHideButton("Show Hidden Files", jfc, false));
        north.add(new JFCHideButton("Hide Hidden Files", jfc, true));
        jpl.add(BorderLayout.NORTH, north);
        jpl.add(BorderLayout.CENTER, createTAListener(jfc));
        return jpl;
    }

    private static JComponent createTAListener(JFileChooser jfc) {
        final JTextArea jta = new JTextArea(10,20);
        PropertyChangeListener pcl = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                jta.append("PropertyChangeEvent for FILE_HIDING_CHANGED_PROPERTY\n");
            }
        };
        jfc.addPropertyChangeListener(JFileChooser.FILE_HIDING_CHANGED_PROPERTY, pcl);
        JScrollPane jsp = new JScrollPane(jta);
        return jsp;
    }
}
