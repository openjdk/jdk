/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 5013739
  @summary MNEMONIC CONFLICTS IN DISABLED/HIDDEN MENU ITEMS
  @library ../regtesthelpers
  @build JRobot
  @key headful
  @run main bug5013739
*/

import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

public class bug5013739 {

    static boolean passed = true;
    static JFrame mainFrame;
    static JMenu file;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            mainFrame = new JFrame("Bug5013739");
            JMenuBar mb = new JMenuBar();
            mainFrame.setJMenuBar(mb);
            file = new JMenu("File");
            JMenuItem about = new JMenuItem("About");
            about.setMnemonic('A');
            about.addActionListener(new AbstractAction() {
                public void actionPerformed(ActionEvent evt) {
                    passed = false;
                }
            });
            file.add(about);
            about.setVisible(false);
            file.add("Open");
            file.add("Close");
            file.setMnemonic('F');
            mb.add(file);
            mainFrame.pack();
            mainFrame.setVisible(true);
            Util.blockTillDisplayed(mainFrame);
        });

        try {
            JRobot robo = JRobot.getRobot();
            robo.delay(500);
            robo.clickMouseOn(file);
            robo.hitKey(KeyEvent.VK_A);
            robo.delay(1000);
        } finally {
            if (mainFrame != null) {
                SwingUtilities.invokeAndWait(() -> mainFrame.dispose());
            }
        }
        if (!passed) {
            throw new RuntimeException("Hidden menu item is selectable "+
                    "via mnemonic. Test failed.");
        }
    }
}

class Util {
    public static Point blockTillDisplayed(Component comp) {
        Point p = null;
        while (p == null) {
            try {
                p = comp.getLocationOnScreen();
            } catch (IllegalStateException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                }
            }
        }
        return p;
    }
}
