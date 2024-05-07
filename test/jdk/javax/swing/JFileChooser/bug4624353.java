/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4624353
 * @summary Tests that Motif FileChooser is not able to show control buttons
 * @key headful
 * @run main bug4624353
 */

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug4624353 {
    static volatile boolean passed = true;
    static JFrame fr;
    static JFileChooser fc;

    public static void main(String args[]) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");

        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4624353");
                fc = new JFileChooser();
                fc.setControlButtonsAreShown(false);
                fr.getContentPane().add(fc);
                fr.pack();
                fr.setVisible(true);

                passAround(fc);
            });
            if (!passed) {
                throw new RuntimeException("Test failed");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    public static void passAround(Container c) {
        Component[] list = c.getComponents();
        if (list.length == 0) {
            return;
        }
        for (int i = 0; i < list.length; i++) {
            if (list[i] != null) {
                if ((list[i] instanceof JButton) &&
                        "OK".equals(((JButton)list[i]).getText())) {
                    passed = false;
                    return;
                }
                passAround((Container)list[i]);
            }
        }
    }
}
