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

/*
 * @test
 * @bug 8078471
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if filechooser current directory changed to parent
 * directory on BACKSPACE key press except root directory.
 * @run main TestBackSpaceAction
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.awt.Robot;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;


public class TestBackSpaceAction {
    private static JFrame frame;
    private static JFileChooser fileChooser;
    private static Robot robot;
    private static File testDir;
    private static File subDir;
    private static File prevDir;
    private static File crntDir;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            // create test directory
            String tmpDir = System.getProperty("java.io.tmpdir");
            //'java.io.tmpdir' isn't guaranteed to be defined
            if (tmpDir.length() == 0) {
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

        for (UIManager.LookAndFeelInfo laf :
                        UIManager.getInstalledLookAndFeels()) {
            if (!laf.getClassName().contains("MotifLookAndFeel")) {
                System.out.println("Testing LAF: " + laf.getClassName());
                SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
                doTesting(laf);
            }
        }
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void doTesting(UIManager.LookAndFeelInfo laf)
            throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });
            boolean passed_1 = false;
            boolean passed_2 = false;
            robot.waitForIdle();
            robot.delay(100);

            // check backspace key at subDir level
            clickBackSpace();
            if (prevDir != crntDir) {
                passed_1 = true;
            }

            // check if backspace key changes directory at root level
            while (!fileChooser.getFileSystemView().isFileSystemRoot(prevDir)) {
                clickBackSpace();
                if (prevDir == crntDir) {
                    passed_2 = true;
                    break;
                }
            }

            if (passed_1 && passed_2) {
                System.out.println("Passed");
            } else {
                throw new RuntimeException("BackSpace does not lead to " +
                        "parent directory for LAF: " + laf.getClassName());
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test File Chooser Backspace Action");
        frame.getContentPane().setLayout(new BorderLayout());
        fileChooser = new JFileChooser(subDir);
        fileChooser.setControlButtonsAreShown(false);
        fileChooser.addHierarchyListener(new HierarchyListener(){
            public void hierarchyChanged(HierarchyEvent he) {
                grabFocusForComboBox(fileChooser.getComponents());
            }
        });
        frame.getContentPane().add(fileChooser, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void grabFocusForComboBox(Component[] comp)
    {
        for (Component c:comp) {
            if (c instanceof JComboBox) {
                JComboBox cb = (JComboBox)c;
                cb.requestFocusInWindow();
                break;
            } else if (c instanceof JPanel) {
                JPanel jp = (JPanel)c;
                grabFocusForComboBox(jp.getComponents());
            }
        }
    }

    private static void clickBackSpace() {
        prevDir = fileChooser.getCurrentDirectory();
        robot.keyPress(KeyEvent.VK_BACK_SPACE);
        robot.keyRelease(KeyEvent.VK_BACK_SPACE);
        crntDir = fileChooser.getCurrentDirectory();
    }
}
