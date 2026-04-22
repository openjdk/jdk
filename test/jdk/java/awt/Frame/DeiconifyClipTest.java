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

/*
 * DeiconifyClipTest.java
 *
 * summary:
 *
 * What happens is that we call AwtWindow::UpdateInsets when
 * processing WM_NCCALCSIZE delivered on programmatic deiconification.
 * At this point IsIconic returns false (so UpdateInsets proceeds),
 * but the rect sizes still seems to be those weird of the iconic
 * state.  Based on them we compute insets with top = left = 0 (and
 * bottom and right that are completely bogus) and pass them to
 * PaintUpdateRgn which results in incorrect clip origin.  Immediately
 * after that we do UpdateInsets again during WM_SIZE processing and
 * get real values.
 */

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;

/*
 * @test
 * @bug 4792958
 * @summary Incorrect clip region after programmatic restore
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DeiconifyClipTest
*/

public class DeiconifyClipTest {
    private static final String INSTRUCTIONS = """
            This test creates a frame that is automatically iconified/deiconified
            in a cycle.

            The test FAILS if after deiconfication the frame has a greyed-out area
            in the lower-right corner.
            If the frame contents is drawn completely - the test PASSES.

            Press PASS or FAIL button accordingly.
            """;

    static TestFrame testFrame;
    static volatile boolean shouldContinue = true;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("DeiconifyClipTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(DeiconifyClipTest::createAndShowUI)
                .build();
        try {
            runThread();
        } finally {
            passFailJFrame.awaitAndCheck();
            shouldContinue = false;
        }
    }

    private static void runThread() {
        new Thread(() -> {
            for (int i = 0; i < 1000 && shouldContinue; ++i) {
                try {
                    Thread.sleep(3000);
                    SwingUtilities.invokeAndWait(() -> {
                        if ((testFrame.getExtendedState() & Frame.ICONIFIED)
                                != 0) {
                            testFrame.setExtendedState(Frame.NORMAL);
                        } else {
                            testFrame.setState(Frame.ICONIFIED);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    static Frame createAndShowUI() {
        testFrame = new TestFrame();
        testFrame.getContentPane().setLayout(new BoxLayout(testFrame.getContentPane(),
                                                   BoxLayout.Y_AXIS));
        testFrame.getContentPane().setBackground(Color.yellow);
        testFrame.setSize(300, 300);
        return testFrame;
    }

    static class TestFrame extends JFrame {
        public TestFrame() {
            super("DeiconifyClipTest");
        }

        // make it more visible if the clip is wrong.
        public void paint(Graphics g) {
            Insets b = getInsets();
            Dimension d = getSize();

            int x = b.left;
            int y = b.top;
            int w = d.width - x - b.right;
            int h = d.height - y - b.bottom;

            g.setColor(Color.white);
            g.fillRect(0, 0, d.width, d.height);

            g.setColor(Color.green);
            g.drawRect(x, y, w-1, h-1);
            g.drawLine(x, y, x+w, y+h);
            g.drawLine(x, y+h, x+w, y);
        }
    }
}
