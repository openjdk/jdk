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

/* @test
   @bug 4924758
   @summary 1.4 REGRESSION: In Motif L&F JComboBox doesn't react when spacebar is pressed
   @key headful
*/

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;
import java.awt.event.KeyEvent;

public class bug4924758 {

    static volatile boolean passed = false;
    volatile boolean isLafOk = true;

    volatile JFrame mainFrame;
    volatile JComboBox comboBox;

    public static void main(String[] args) throws Exception {
        bug4924758 test = new bug4924758();
        try {
            SwingUtilities.invokeAndWait(test::createUI);
            if (!test.isLafOk) {
                throw new RuntimeException("Could not create Win L&F");
            }
            test.test();
            if (!passed) {
                throw new RuntimeException(
                    "Popup was not closed after VK_SPACE press. Test failed.");
            }
        } finally {
            JFrame f = test.mainFrame;
            if (f != null) {
                SwingUtilities.invokeAndWait(() -> f.dispose());
            }
        }
    }

    void createUI() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        } catch (Exception ex) {
            System.err.println("Can not initialize Motif L&F. Testing skipped.");
            isLafOk = false;
            return;
        }

        mainFrame = new JFrame("Bug4924758");
        String[] items = {"One", "Two", "Three"};
        comboBox = new JComboBox(items);
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                passed = true;
            }

            public void popupMenuCanceled(PopupMenuEvent e) {}
        });
        mainFrame.setLayout(new BorderLayout());
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.add(comboBox, BorderLayout.CENTER);
        mainFrame.pack();
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    void test() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.delay(2000);
        Point p = comboBox.getLocationOnScreen();
        Dimension size = comboBox.getSize();
        p.x += size.width / 2;
        p.y += size.height / 2;
        robot.mouseMove(p.x, p.y);
        robot.keyPress(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);
        robot.delay(2000);
    }
}
