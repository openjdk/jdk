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
 * @bug 4171820
 * @summary Checks that GradientPaint responds to shearing transforms correctly
 *          The gradients drawn should be parallel to the sides of the
 *          indicated anchor rectangle.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ShearTest
 */

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

public class ShearTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test displays 2 rows each containing 4 gradient fills. Each
                gradient fill is labeled depending on whether the line or lines
                of the gradient should be truly vertical, truly horizontal, or
                some slanted diagonal direction. The test passes if the direction
                of each gradient matches its label.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ShearTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("Shear Gradient Test");
        f.setLayout(new GridLayout(0, 1));
        f.add(getPanelSet(false), "North");
        f.add(getPanelSet(true), "Center");
        f.setSize(500, 300);
        return f;
    }

    public static Panel getPanelSet(boolean horizontal) {
        String direven = horizontal ? "Slanted" : "Vertical";
        String dirodd = horizontal ? "Horizontal" : "Slanted";

        Panel p = new Panel();
        p.setLayout(new GridLayout(0, 4));
        p.add(new ShearCanvas(direven, false, horizontal, false, true));
        p.add(new ShearCanvas(dirodd,  false, horizontal, true,  false));
        p.add(new ShearCanvas(direven, true,  horizontal, false, true));
        p.add(new ShearCanvas(dirodd,  true,  horizontal, true,  false));

        return p;
    }

    public static class ShearCanvas extends Canvas {
        public static final int GRADW = 30;

        public static final Rectangle anchor =
            new Rectangle(-GRADW / 2, -GRADW / 2, GRADW, GRADW);

        public static final Color faintblue = new Color(0f, 0f, 1.0f, 0.35f);

        private AffineTransform txform;
        private GradientPaint grad;
        private String label;

        public ShearCanvas(String label,
                           boolean cyclic, boolean horizontal,
                           boolean shearx, boolean sheary) {
            txform = new AffineTransform();
            if (shearx) {
                txform.shear(-.5, 0);
            }
            if (sheary) {
                txform.shear(0, -.5);
            }
            int relx = horizontal ? 0 : GRADW / 2;
            int rely = horizontal ? GRADW / 2 : 0;
            grad = new GradientPaint(-relx, -rely, Color.green,
                                     relx, rely, Color.yellow, cyclic);
            this.label = label;
        }

        public void paint(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;

            AffineTransform at = g2d.getTransform();
            g2d.translate(75, 75);
            g2d.transform(txform);
            g2d.setPaint(grad);
            g2d.fill(g.getClip());
            g2d.setColor(faintblue);
            g2d.fill(anchor);
            g2d.setTransform(at);

            Dimension d = getSize();
            g2d.setColor(Color.black);
            g2d.drawRect(0, 0, d.width - 1, d.height - 1);
            g2d.drawString(label, 5, d.height - 5);
            g2d.dispose();
        }
    }
}
