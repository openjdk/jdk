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

/*
  @test
  @bug 4152524 8310054
  @requires os.family=="windows"
  @summary Test that scroll pane doesn't have scroll bars visible when it is
  shown for the first time with SCROLLBARS_AS_NEEDED style
  @key headful
*/

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.ScrollPane;

import java.awt.event.InputEvent;

public class ScrollPaneExtraScrollBar {
    ScrollPane sp;
    Frame f;
    volatile Rectangle r;

    public static void main(String[] args) throws Exception {
        ScrollPaneExtraScrollBar scrollTest = new ScrollPaneExtraScrollBar();
        scrollTest.init();
        scrollTest.start();
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            f = new Frame("ScrollPaneExtraScrollBar");
            sp = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
            sp.add(new Button("TEST"));
            f.add("Center", sp);
            // Frame must not be packed, otherwise the bug isn't reproduced
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    public void start() throws Exception {
        try {
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(100);
            EventQueue.invokeAndWait(() -> {
                r = f.getBounds();
            });
            robot.mouseMove(r.x + r.width - 1, r.y + r.height - 1);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(r.x + r.width + 50, r.y + r.height + 50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                Insets insets = sp.getInsets();
                if (insets.left != insets.right || insets.top != insets.bottom) {
                    throw new RuntimeException("ScrollPane has scroll bars visible" +
                            " when it shouldn't");
                }
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
