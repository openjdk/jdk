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
 * @bug 4476002
 * @summary  Verifies JEditorPane: <ol> list numbers do not pick up color of the list text
 * @key headful
 * @run main bug4476002
*/

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class bug4476002 {

    private static boolean passed = true;
    private static JLabel htmlComponent;

    private static Robot robot;
    private static JFrame mainFrame;
    private static volatile Point p;
    private static volatile Dimension d;

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        try {
            SwingUtilities.invokeAndWait(() -> {
                String htmlText =
                    "<html><head><style>" +
                    "OL { list-style-type: disc; color: red }" +
                    "</style></head>" +
                    "<body><ol><li>wwwww</li></ol></body></html>";

                mainFrame = new JFrame("bug4476002");

                htmlComponent = new JLabel(htmlText);
                mainFrame.getContentPane().add(htmlComponent);

                mainFrame.pack();
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                p = htmlComponent.getLocationOnScreen();
                d = htmlComponent.getSize();
            });
            int x0 = p.x;
            int y = p.y + d.height/2;

            for (int x = x0; x < x0 + d.width; x++) {
                if (robot.getPixelColor(x, y).equals(Color.black)) {
                    passed = false;
                    break;
                }
            }
            if (!passed) {
                throw new RuntimeException("Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (mainFrame != null) {
                    mainFrame.dispose();
                }
            });
        }
    }

}
