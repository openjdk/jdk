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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4168481
 * @summary Test to verify Dialog getLocation() regression on Solaris
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual GetLocationTest_1
 */

public class GetLocationTest_1 {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Click in in the blue square and the yellow window should come
                   up with the top left by the cursor
                2. If you see this correct behavior press PASS. If you see that
                   the yellow window location is offset by some inset, press FAIL
                   """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    public static Dialog initialize() {
        Frame f = new Frame("Owner Frame");
        ColorComponent blue = new ColorComponent();
        blue.setBackground(Color.blue);
        blue.setSize(50, 50);

        final Dialog dialog = new Dialog(f, "GetLocation test");
        dialog.setLocation(300, 300);
        System.out.println("Dialog location = " + dialog.getLocation());
        blue.setLocation(50, 50);
        dialog.setLayout(null);
        dialog.add(blue);
        dialog.setSize(200, 200);

        final ColorWindow w = new ColorWindow(f);
        w.setSize(50, 50);
        w.setBackground(Color.yellow);

        blue.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                PassFailJFrame.log("Dialog location = " + dialog.getLocation());
                Point p = e.getPoint();
                Component c = e.getComponent();
                PassFailJFrame.log("Position = " + p);
                convertPointToScreen(p, c);
                PassFailJFrame.log("Converted to = " + p);
                w.setLocation(p.x, p.y);
                w.setVisible(true);
            }
        });
        return dialog;
    }

    static class ColorComponent extends Component {
        public void paint(Graphics g) {
            g.setColor(getBackground());
            Rectangle bounds = getBounds();
            g.fillRect(0, 0, bounds.width, bounds.height);
        }
    }

    static class ColorWindow extends Window {
        ColorWindow(Frame f) {
            super(f);
        }

        public void paint(Graphics g) {
            g.setColor(getBackground());
            Rectangle bounds = getBounds();
            g.fillRect(0, 0, bounds.width, bounds.height);
        }
    }

    public static void convertPointToScreen(Point p, Component c) {
        do {
            Point b = c.getLocation();
            PassFailJFrame.log("Adding " + b + " for " + c);
            p.x += b.x;
            p.y += b.y;

            if (c instanceof java.awt.Window) {
                break;
            }
            c = c.getParent();
        } while (c != null);
    }
}
