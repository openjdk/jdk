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

    private static boolean result = true;
    private static boolean newFolderFound = false;

    public static void main(String[] args) {

        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            if (!setLookAndFeel(laf)) {
                continue;
            }

            // Test1, Read Only JFileChooser
            boolean readOnly = true;
            createAndTestCustomFileChooser(readOnly);
            System.out.println("Its a Read Only JFileChooser " +
                               (newFolderFound ? "but it has" :
                                "and it doesn't have") +
                               " a New Folder Button found" +
                               ", So the Test1 " +
                               (result ? "Passed" : "Failed") + " for " + laf);

            // Test2, Read/Write JFileChooser
            if (!(laf.contains("Motif") || laf.contains("Aqua"))) {
                createAndTestCustomFileChooser(readOnly = false);
                System.out.println("Its a not a Read Only JFileChooser " +
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
        result = readOnly ^ newFolderFound;
    }

    private static void checkNewFolderButton(Container c, boolean readOnly) {
        int n = c.getComponentCount();
        for (int i = 0; i < n; i++) {
            if (newFolderFound) {
                break;
            }
            Component comp = c.getComponent(i);
            if (comp instanceof JButton) {
                JButton b = (JButton) comp;
                Action action = b.getAction();
                if (action != null) {
                    String name = (String) action.getValue(Action.NAME);
                    if (name != null && name.equals("New Folder")) {
                        newFolderFound = true;
                        System.out.println(
                                "New Folder Button Found when readOnly = " +
                                readOnly);
                    }
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
