/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.ScrollPane;

/*
 * @test
 * @bug 8311689
 * @key headful
 * @requires os.family=="windows"
 * @summary Verifies ScrollPane allows viewing the whole contents of its child
 * @run main ScrollPaneScrollEnd
 */
public final class ScrollPaneScrollEnd {
    private static final Color CANVAS_BACKGROUND = new Color(255, 200, 200);
    private static final Color CANVAS_FOREGROUND = new Color(255, 255, 200);
    private static final int OFFSET = 12;

    private static final Dimension CANVAS_SIZE = new Dimension(900, 600);
    private static final Dimension SCROLL_PANE_SIZE =
            new Dimension(CANVAS_SIZE.width / 3, CANVAS_SIZE.height / 3);
    private static final int SCROLL_OFFSET = 100;

    private static final int DELAY = 200;

    public static void main(String[] args) throws Exception {
        Canvas canvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                g.setColor(CANVAS_BACKGROUND);
                g.fillRect(0, 0, getWidth(), getHeight());

                g.setColor(CANVAS_FOREGROUND);
                g.fillRect(OFFSET, OFFSET,
                           getWidth() - OFFSET * 2, getHeight() - OFFSET * 2);
            }
        };
        canvas.setSize(CANVAS_SIZE);

        ScrollPane scrollPane = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        scrollPane.add(canvas);
        scrollPane.setSize(SCROLL_PANE_SIZE);

        Frame frame = new Frame("ScrollPaneScrollEnd");
        frame.add(scrollPane, "Center");
        frame.setLocation(100, 100);
        frame.pack();
        frame.setVisible(true);

        final Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(DELAY);

        final Dimension vp = scrollPane.getViewportSize();
        final Point expected = new Point(CANVAS_SIZE.width - vp.width,
                                         CANVAS_SIZE.height - vp.height);

        scrollPane.setScrollPosition(CANVAS_SIZE.width + SCROLL_OFFSET,
                                     CANVAS_SIZE.height + SCROLL_OFFSET);
        try {
            if (!expected.equals(scrollPane.getScrollPosition())) {
                throw new Error("Can't scroll to the end of the child component");
            }
        } finally {
            frame.dispose();
        }
    }
}
