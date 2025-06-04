/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4515031
 * @key headful
 * @summary The JFileChooser Dialog itself has no accessible description.
 * @run main JFileChooserAccessibleDescriptionTest
 */
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class JFileChooserAccessibleDescriptionTest {
    private static JFrame jFrame;
    private static JFileChooser jFileChooser;
    private static JButton jButton;

    private static Robot robot;
    private static volatile String description;
    private static volatile int xLocn;
    private static volatile int yLocn;
    private static volatile int width;
    private static volatile int height;

    public static void createGUI() {
        jFrame = new JFrame("bug4515031 Frame");
        jFileChooser = new JFileChooser();

        jButton = new JButton("Show FileChooser");
        jButton.addActionListener(e -> jFileChooser.showDialog(jFrame, null));
        jFrame.getContentPane().add(jButton);
        jFrame.setSize(200, 100);
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }

    public static void doTest() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> createGUI());
            robot = new Robot();
            robot.setAutoDelay(200);
            robot.setAutoWaitForIdle(true);

            SwingUtilities.invokeAndWait(() -> {
                xLocn = jButton.getLocationOnScreen().x;
                yLocn = jButton.getLocationOnScreen().y;
                width = jButton.getSize().width;
                height = jButton.getSize().height;
            });

            robot.mouseMove(xLocn + width / 2, yLocn + height / 2);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            SwingUtilities.invokeAndWait(() -> description =
                jFileChooser.getAccessibleContext().getAccessibleDescription());

            if (description != null) {
                System.out.println(
                    "Accessibility Description " + "for JFileChooser is Set");
            } else {
                throw new RuntimeException("Accessibility Description for"
                    + "JFileChooser is not Set");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> jFrame.dispose());
        }
    }

    public static void main(String args[]) throws Exception {
        doTest();
    }
}

