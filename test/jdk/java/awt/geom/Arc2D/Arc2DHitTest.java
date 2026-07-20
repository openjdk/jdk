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
 * @bug 4178123
 * @summary Verifies that the Arc2D.contains(point) methods work correctly.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Arc2DHitTest
 */

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;

public class Arc2DHitTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            This test displays an arc figure and lets the user click on it.
            The arc will initially be drawn in red and only when the user clicks
            within the arc in the window it will be redrawn into green otherwise
            it should stay red.

            For convenience, the point being tested is drawn in black.  Note
            that rounding in the arc rendering routines may cause points near
            the boundary of the arc to render incorrectly.  Allow for a pixel
            or two of leeway near the boundary.
            """;

        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(initialize())
            .build()
            .awaitAndCheck();
    }
    private static Frame initialize() {
        Frame f = new Frame("Arc2DHitTest");
        ArcHitPanel panel = new ArcHitPanel();
        f.add(panel);
        f.setSize(300, 250);
        return f;
    }
}

class ArcHitPanel extends Panel {
    private Arc2D arc;
    private Point hit;
    public ArcHitPanel() {
        arc = new Arc2D.Float(10, 10, 100, 100, 0, 120, Arc2D.PIE);
        this.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                hit = e.getPoint();
                repaint();
            }
        });
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.white);
        g2.fill(g2.getClipBounds());
        g2.setColor((hit != null && arc.contains(hit))
            ? Color.green : Color.red);
        g2.fill(arc);
        if (hit != null) {
            g2.setColor(Color.black);
            g2.fillRect(hit.x, hit.y, 1, 1);
        }
    }
}
