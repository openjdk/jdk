/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.ComponentOrientation;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @bug 6442919
 * @library ../regtesthelpers
 * @build Util
 * @summary Test to check the orientation of Popup menu is set
 * as per FileChooser orientation.
 * @run main FCPopupMenuOrientationTest
 */

public class FCPopupMenuOrientationTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                for (UIManager.LookAndFeelInfo laf :
                        UIManager.getInstalledLookAndFeels()) {
                    String className = laf.getName().toLowerCase();
                    if (className.contains("motif")
                            || className.contains("mac")
                            || className.contains("gtk")) {
                        continue;
                    }
                    setLookAndFeel(laf);
                    JFileChooser fc = new JFileChooser();
                    fc.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

                    JComponent comp = (JComponent) Util.findSubComponent(
                            fc, "FilePane");
                    JPopupMenu popupMenu = comp.getComponentPopupMenu();
                    if (popupMenu.getComponentOrientation() !=
                            fc.getComponentOrientation()) {
                        throw new RuntimeException("File Chooser component " +
                                "orientation doesn't match with PopupMenu");
                    }
                }
            }
        });
        System.out.println("Test Pass");
    }
    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            System.out.println("Set L&F : " + laf.getName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LookAndFeel: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

