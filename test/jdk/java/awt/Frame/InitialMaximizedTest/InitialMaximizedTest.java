/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
 * @test
 * @bug 4464714 6365898
 * @key headful
 * @summary Frames cannot be shown initially maximized
*/

public class InitialMaximizedTest {

    static Frame frame;

    public static void main(String[] args) throws Exception {
        if (!Toolkit.getDefaultToolkit()
                    .isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
            return;
        }

        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(InitialMaximizedTest::createAndShowFrame);
            robot.waitForIdle();
            robot.delay(1000);
            EventQueue.invokeAndWait(InitialMaximizedTest::checkMaximized);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void checkMaximized() {
        Rectangle frameBounds = frame.getBounds();

        GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        Rectangle workArea = gc.getBounds();

        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        workArea.x += screenInsets.left;
        workArea.y += screenInsets.top;
        workArea.width -= screenInsets.left + screenInsets.right;
        workArea.height -= screenInsets.top + screenInsets.bottom;

        System.out.println("Frame bounds " + frameBounds);
        System.out.println("GraphicsConfiguration bounds " + gc.getBounds());
        System.out.println("Screen insets: " + screenInsets);
        System.out.println("Work area: " + workArea);

        //frame bounds can exceed screen size on Windows, see 8231043
        if (!frameBounds.contains(workArea)) {
            throw new RuntimeException("Frame is not maximized");
        }
    }

    private static void createAndShowFrame() {
        frame = new Frame("The frame SHOULD be shown MAXIMIZED");
        frame.setSize(300, 300);
        frame.setLocation(50, 50);
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                frame.dispose();
            }
        });
        frame.setVisible(true);
    }
}
