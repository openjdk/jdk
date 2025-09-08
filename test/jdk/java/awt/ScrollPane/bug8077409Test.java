/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 8077409
 * @summary Drawing deviates when validate() is invoked on java.awt.ScrollPane
 * @run main bug8077409Test
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.ScrollPane;
import java.awt.event.KeyEvent;

public class bug8077409Test extends Frame {
    ScrollPane pane;
    MyCanvas myCanvas;

    public bug8077409Test() {
        super();
        setLayout(new BorderLayout());
        pane = new ScrollPane();
        myCanvas = new MyCanvas();
        pane.add(myCanvas);
        add(pane, BorderLayout.CENTER);
        setSize(320, 480);
    }

    public static void main(String[] args) throws AWTException, InterruptedException {
        final bug8077409Test obj = new bug8077409Test();
        try {
            obj.setLocationRelativeTo(null);
            obj.setVisible(true);
            Point scrollPosition = obj.pane.getScrollPosition();
            scrollPosition.translate(0, 1);
            obj.pane.setScrollPosition(scrollPosition);
            int y = obj.pane.getComponent(0).getLocation().y;
            obj.pane.validate();
            if (y != obj.pane.getComponent(0).getLocation().y) {
                throw new RuntimeException("Wrong position of component in ScrollPane");
            }
        } finally {
            obj.dispose();
        }
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
    }

    class MyCanvas extends Canvas {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, 800);
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(Color.BLACK);
            g.drawLine(0, 0, 399, 0);
            g.setColor(Color.RED);
            g.drawLine(0, 1, 399, 1);
            g.setColor(Color.BLUE);
            g.drawLine(0, 2, 399, 2);
            g.setColor(Color.GREEN);
            g.drawLine(0, 3, 399, 3);
        }

    }

}
