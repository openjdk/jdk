/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4525475
 * @summary This testcase tests JDK-4525475 bug fix, checks whether JFileChooser
 *          allows modification to the file-system by way of the "New Folder"
 *          button or not, ideally a read-only JFileChooser shouldn't allow it.
 * @run main JFileChooserReadOnlyTest
 */
public class JFileChooserReadOnlyTest {

    private static volatile boolean result = true;
    private static volatile boolean newFolderFound = false;

    public static void main(String[] args) throws Exception {

        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            if (!setLookAndFeel(laf)) {
                continue;
            }

            // Test1, Read Only JFileChooser
            SwingUtilities.invokeAndWait(
                        () -> createAndTestCustomFileChooser(true));
            System.out.println("It's a read-only JFileChooser " +
                               (newFolderFound ? "but it has" :
                                "and it doesn't have") +
                               " a New Folder Button found" +
                               ", So the Test1 " +
                               (result ? "Passed" : "Failed") + " for " + laf);

            // Test2, Read/Write JFileChooser
            /* Skipping Motif and Aqua L&Fs
               For Motif L&F, the 'New Folder' button is never shown.
               The Aqua L&F behaves similar to the native FileChooser:
                 the 'Open' dialog doesn't show the 'New Folder' button,
                 but it shows the button for the 'Save' dialog.
             */
            if (!(laf.contains("Motif") || laf.contains("Aqua"))) {
                SwingUtilities.invokeAndWait(
                        () -> createAndTestCustomFileChooser(false));
                System.out.println("It's not a read-only JFileChooser " +
                                   (newFolderFound ? "and it has" :
                                    "but it doesn't have") +
                                   " a New Folder Button" + ", So the Test2 " +
                                   (result ? "Passed" : "Failed") + " for " +
                                   laf);
            }

            if (result) {
                System.out.println("Test Passed for " + laf);
            } else {
                throw new RuntimeException(
                        "Test Failed, JFileChooser readOnly flag is not " +
                        "working properly for " + laf);
            }
        }
    }

    private static void createAndTestCustomFileChooser(boolean readOnly) {
        newFolderFound = false;
        UIManager.put("FileChooser.readOnly", Boolean.valueOf(readOnly));
        JFileChooser jfc = new JFileChooser();
        checkNewFolderButton(jfc, readOnly);
        result = result && (readOnly ^ newFolderFound);
    }

    private static void checkNewFolderButton(Container c, boolean readOnly) {
        int n = c.getComponentCount();
        for (int i = 0; i < n && !newFolderFound; i++) {
            Component comp = c.getComponent(i);
            if (comp instanceof JButton) {
                JButton b = (JButton) comp;
                Action action = b.getAction();
                if (action != null
                    && "New Folder".equals(action.getValue(Action.NAME))) {
                    newFolderFound = true;
                    System.out.println(
                            "New Folder Button Found when readOnly = " +
                            readOnly);
                }
            } else if (comp instanceof Container) {
                checkNewFolderButton((Container) comp, readOnly);
            }
        }
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported L&F: " + lafName);
            return false;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

}
