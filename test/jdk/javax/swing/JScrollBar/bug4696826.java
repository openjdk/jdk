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

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/*
 * @test
 * @bug 4696826
 * @summary BasicScrollBarUI should check if it needs to paint the thumb
 * @run main bug4696826
 */

public class bug4696826 {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JScrollBar sb = new JScrollBar();
            sb.setBounds(new Rectangle(0, 0, 20, 20));

            TestScrollBarUI ui = new TestScrollBarUI();
            sb.setUI(ui);
            ui.setThumbBounds(0, 0, 20, 20);

            BufferedImage image = new BufferedImage(100, 100,
                    BufferedImage.TYPE_3BYTE_BGR);
            Graphics g = image.getGraphics();
            g.setClip(200, 200, 100, 100);
            sb.paint(g);
        });
        System.out.println("Test Passed!");
    }

    static class TestScrollBarUI extends BasicScrollBarUI {
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            throw new RuntimeException("Thumb shouldn't be painted");
        }
        public void setThumbBounds(int x, int y, int width, int height) {
            super.setThumbBounds(x, y, width, height);
        }
    }
}
