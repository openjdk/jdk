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
   @bug 4996503
   @summary REGRESSION: NotSerializableException: javax.swing.plaf.basic.BasicComboPopup+1
   @key headful
*/

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4996503 {

    static volatile JFrame frame = null;
    static volatile JComboBox<String> comboBox = null;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4996503");
                String[] items = { "item0", "item1", "item2" };
                comboBox = new JComboBox<String>(items);
                frame.add(comboBox);
                frame.pack();
                frame.validate();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.delay(1000);
            Point p = comboBox.getLocationOnScreen();
            Dimension size = comboBox.getSize();
            p.x += size.width / 2;
            p.y += size.height / 2;
            robot.mouseMove(p.x, p.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

            ObjectOutputStream out = null;

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try {
                out = new ObjectOutputStream(byteStream);
            } catch (IOException e) {}
            if (out != null) {
                try {
                    out.writeObject(comboBox);
                } catch (Exception e) {
                    System.out.println(e);
                    throw new Error("Serialization exception. Test failed.");
                }
            }
        } finally {
            if (frame != null) {
                 SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }
}
