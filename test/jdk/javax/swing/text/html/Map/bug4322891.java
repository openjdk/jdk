/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4322891
 * @summary  Tests if image map receives correct coordinates.
 * @key headful
 * @run main bug4322891
*/

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JFrame;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;

public class bug4322891 {

    private boolean finished = false;
    private static boolean passed = false;
    private static Robot robot;
    private static JFrame f;
    private static JEditorPane jep;
    private static volatile Point p;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            bug4322891 test = new bug4322891();
            SwingUtilities.invokeAndWait(test::init);
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                p = jep.getLocationOnScreen();
            });
            robot.mouseMove(p.x, p.y);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            for (int i = 1; i < 30; i++) {
                robot.mouseMove(p.x + i, p.y + i);
                robot.waitForIdle();
            }
            if (!passed) {
                throw new RuntimeException("Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    public void init() {
        String text = "<img src=\"aaa\" height=100 width=100 usemap=\"#mymap\">" +
                      "<map name=\"mymap\">" +
                      "<area href=\"aaa\" shape=rect coords=\"0,0,100,100\">" +
                      "</map>";

        f = new JFrame();
        jep = new JEditorPane();
        jep.setEditorKit(new HTMLEditorKit());
        jep.setEditable(false);

        jep.setText(text);

        jep.addHyperlinkListener(new HyperlinkListener() {
                                    public void hyperlinkUpdate(HyperlinkEvent e) {
                                        passed = true;
                                    }
                                });
        f.getContentPane().add(jep);
        f.setSize(500,500);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

}
