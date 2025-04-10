/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4232716
 * @key headful
 * @summary Tests if <input type="text" size="5"> creates a maximized JTextField
 * @run main bug4232716
 */

import java.awt.Component;
import java.awt.Container;
import java.awt.Robot;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class bug4232716 {

    private static JFrame frame;
    private static JEditorPane e;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4232716");
                String html ="<html> <head> <title> Test </title> </head> " +
                             "<body> <form> <input type=\"text\" size=\"5\"> " +
                             "</form> </body> </html>";
                e = new JEditorPane("text/html", html);
                e.setEditable(false);
                frame.add(e);
                frame.setSize(400, 300);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            Container c = (Container)e.getComponent(0);
            Component swingComponent = c.getComponent(0);
            System.out.println(swingComponent);
            if (swingComponent instanceof JTextField tf) {
                System.out.println(tf.getWidth());
                System.out.println(frame.getWidth());
                if (swingComponent.getWidth() > (frame.getWidth() * 0.75)) {
                    throw new RuntimeException("textfield width almost same as frame width");
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
