/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/* @test
 * @bug 8078471
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "linux")
 * @summary Verifies if filechooser current directory changed to parent directory on
 * BACKSPACE key press from keyboard.
 * @run main/manual TestBackSpaceAction
 */

public class TestBackSpaceAction {
    static TestFrame testObj;
    static final String instructions
            = """
            Default look and feel set to Metal.


            INSTRUCTIONS:
               1. Double click on 'subDir' to move into 'subDir' folder.
               2. Press BACKSPACE key.
               3. Verify the file chooser directory changed to 'testDir'.
               4. Press Nimbus button to change look and feel to nimbus.
               5. Repeat Steps 1 to 3.
               6. Press GTK+ button to change look and feel to GTK.
               7. Repeat Steps 1 to 3.
               8. Press Pass if execution is as per instructions else Press Fail.
            """;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                passFailJFrame = new PassFailJFrame("JFileChooser Test Instructions",
                        instructions, 5, 15, 50);
                testObj = new TestFrame();
                PassFailJFrame.addTestWindow(testObj);
                PassFailJFrame.positionTestWindow(testObj,
                        PassFailJFrame.Position.HORIZONTAL);
                testObj.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        passFailJFrame.awaitAndCheck();
    }
}

class TestFrame extends JFrame implements ActionListener {
    static File testDir;
    static File subDir;

    public TestFrame() {
        try {
            // create test directory
            String tmpDir = System.getProperty("java.io.tmpdir");
            if (tmpDir.length() == 0) { //'java.io.tmpdir' isn't guaranteed to be defined
                tmpDir = System.getProperty("user.home");
            }
            testDir = new File(tmpDir, "testDir");
            testDir.mkdir();
            testDir.deleteOnExit();

            // create sub directory inside test directory
            subDir = new File(testDir, "subDir");
            subDir.mkdir();
            subDir.deleteOnExit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        initComponents();
    }

    public void initComponents() {
        JPanel p = new JPanel();
        JFileChooser fileChooser = new JFileChooser(testDir);
        fileChooser.setControlButtonsAreShown(false);
        getContentPane().add(fileChooser);

        UIManager.LookAndFeelInfo[] lookAndFeel = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo laf : lookAndFeel) {
            if(!laf.getClassName().contains("MotifLookAndFeel")) {
                JButton btn = new JButton(laf.getName());
                btn.setActionCommand(laf.getClassName());
                btn.addActionListener(this);
                p.add(btn);
            }
        }

        getContentPane().add(p,BorderLayout.SOUTH);
        setSize(500, 500);
    }

    private static void setLookAndFeel(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf);
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    //Change the Look and Feel on user selection
    public void actionPerformed(ActionEvent e) {
        setLookAndFeel(e.getActionCommand());
        SwingUtilities.updateComponentTreeUI(this);
    }
}
