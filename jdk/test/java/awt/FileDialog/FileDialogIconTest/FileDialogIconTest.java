/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8157163
 * @summary AWT FileDialog does not inherit icon image from parent Frame
 * @requires os.family=="windows"
 * @run main FileDialogIconTest
 */
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

public class FileDialogIconTest {
    private static Frame frame;
    private static Dialog dialog;

    public static void main(final String[] args) throws InterruptedException, InvocationTargetException, AWTException {
        Robot robot;
        Point p;
        try {
            frame = new Frame();
            frame.setIconImage(createImage());
            frame.setVisible(true);
            robot = new Robot();
            robot.waitForIdle();
            robot.delay(200);

            dialog = new FileDialog(frame, "Dialog");
            dialog.setModal(false);
            dialog.setVisible(true);
            robot.waitForIdle();
            robot.delay(200);

            p = new Point(10, 10);
            SwingUtilities.convertPointToScreen(p, dialog);
            Color color = robot.getPixelColor(p.x, p.y);
            if (!Color.RED.equals(color)) {
                throw new RuntimeException("Dialog icon was not inherited from " +
                        "owning window. Wrong color: " + color);
            }
        } finally {
            dialog.dispose();
            frame.dispose();
        }
    }

    private static Image createImage() {
        BufferedImage image = new BufferedImage(64, 64,
                                                  BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        return image;
    }

}
