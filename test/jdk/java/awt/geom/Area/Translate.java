/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4183373
 * @summary Verifies that the translated Area objects display correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Translate
 */

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

public class Translate {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            This test displays two sets of rectangular figures. The two sets
            should be displayed one on top of the other and should be lined
            up vertically with each other. If the two sets of figures are
            not directly above and below each other then the test fails
            """;

        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(35)
            .testUI(initialize())
            .build()
            .awaitAndCheck();
    }
    private static Frame initialize() {
        Frame f = new Frame("Translate");
        TranslatePanel panel = new TranslatePanel();
        f.add(panel);
        f.setSize(300, 250);
        return f;
    }
}

class TranslatePanel extends Panel {
    private static Image bufferedImage;
    private static Area a1, a2, a3;

    public TranslatePanel() {
        a1 = new Area(new Rectangle2D.Double(20.0, 20.0, 60.0, 60.0));

        a2 = new Area((Area) a1.clone());
        a2.subtract(new Area(new Rectangle2D.Double(30.0, 30.0, 40.0, 40.0)));

        a3 = new Area((Area) a2.clone());
        a3.add(new Area(new Rectangle2D.Double(40.0, 40.0, 20.0, 20.0)));

        AffineTransform at2 = new AffineTransform();
        at2.translate(100.0, 0.0);
        a2.transform(at2);

        AffineTransform at3 = new AffineTransform();
        at3.translate(200.0, 0.0);
        a3.transform(at3);
    }
    private void paintRects(Graphics2D g2) {
        Rectangle clip = g2.getClipBounds();
        g2.setColor(Color.white);
        g2.fillRect(clip.x, clip.y, clip.width, clip.height);
        g2.setPaint(Color.red);
        g2.fill(a1);
        g2.setPaint(Color.yellow);
        g2.fill(a2);
        g2.setPaint(Color.blue);
        g2.fill(a3);
    }

    @Override
    public void paint(Graphics g) {
        if (bufferedImage == null) {
            bufferedImage = createImage(300, 100);
            Graphics big = bufferedImage.getGraphics();
            // Notice that if you remove the translate() call, it works fine.
            big.translate(-1, -1);
            big.setClip(1, 1, 300, 100);
            paintRects((Graphics2D)big);
            big.translate(1, 1);
        }
        paintRects((Graphics2D)g);
        g.drawImage(bufferedImage, 1, 100, this);
        g.setColor(Color.black);
        g.drawString("From offscreen image (with translate):", 10, 95);
        g.drawString(" (should line up with rectangles above)", 10, 110);
    }
}
