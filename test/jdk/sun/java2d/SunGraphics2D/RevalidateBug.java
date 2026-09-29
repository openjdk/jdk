/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4652373
 * @summary verify that SunGraphics2D survives surface revalidation
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RevalidateBug
 * @requires (os.family == "windows")
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JFrame;

public class RevalidateBug {

    private static final String INSTRUCTIONS = """
        This bug only reproduces on Windows systems with a task manager that can lock the computer.

        This test draws a grayscale gradient in a window.

        After the gradient becomes visible above, use ctrl-alt-del to bring up
        the task manager and lock the computer.
        Then unlock the computer and the gradient should be repainted to pass.

        If the gradient does not appear after unlocking (or if the test gets
        an error on its own after unlocking the computer) then it fails.
        """;

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .title("RevalidateBug")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(12)
                .columns(50)
                .testUI(RevalidateBug::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {

        JComponent comp = new JComponent() {

            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                System.out.println("paintComponent");
                Graphics2D g2d = (Graphics2D) g;

                Insets insets = getInsets();
                Rectangle rect =
                    new Rectangle(insets.left, insets.top,
                                  getWidth() - insets.right - insets.left,
                                  getHeight() - insets.top - insets.bottom);
                g2d.setPaint(new GradientPaint(rect.x, rect.y, Color.white,
                                               rect.x + rect.width, rect.y, Color.black));

                System.out.println(rect + " w:" + getWidth() + " h:"+getHeight());

                g2d.fillRect(0, 0, getWidth(), getHeight());
            }

            public Dimension getPreferredSize() {
                return new Dimension(500, 500);
            }
        };

        JFrame f = new JFrame("RevalidateTest");
        f.add(comp);
        f.pack();
        return f;
    }

}
