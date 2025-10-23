/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6287895
 * @requires (os.family == "linux")
 * @summary Test cursor and selected text incorrectly colored in TextField
 * @key headful
 * @run main SelectionAndCaretColor
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.TextComponent;
import java.awt.TextField;
import java.awt.image.BufferedImage;

public class SelectionAndCaretColor {
    static TextField tf = new TextField(20);
    static TextArea ta = new TextArea("", 1, 20, TextArea.SCROLLBARS_NONE);
    static Robot r;
    static Frame frame;
    static volatile int flips;

    public static void main(String[] args) throws Exception {
        try {
            frame = new Frame("Selection and Caret color test");
            r = new Robot();

            EventQueue.invokeAndWait(() -> {
                frame.setLayout(new BorderLayout());
                tf.setFont(new Font("Monospaced", Font.PLAIN, 15));
                ta.setFont(new Font("Monospaced", Font.PLAIN, 15));

                frame.add(tf, BorderLayout.NORTH);
                frame.add(ta, BorderLayout.SOUTH);
                frame.setSize(200, 200);
                frame.setVisible(true);
            });
            r.waitForIdle();
            r.delay(1000);
            test(tf);
            test(ta);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static int countFlips(TextComponent tc) {
        int y = tc.getLocationOnScreen().y + tc.getHeight() / 2;
        int x1 = tc.getLocationOnScreen().x + 5;
        int x2 = tc.getLocationOnScreen().x + tc.getWidth() - 5;

        int[] fb = {tc.getBackground().getRGB(), tc.getForeground().getRGB()};
        int i = 0;
        int flips = 0;

        BufferedImage img = r.createScreenCapture(new Rectangle(x1, y, x2 - x1, 1));
        for (int x = 0; x < x2 - x1; x++) {
            int c = img.getRGB(x, 0);
            if (c == fb[i]) {
                ;
            } else if (c == fb[1 - i]) {
                flips++;
                i = 1 - i;
            } else {
                throw new RuntimeException("Invalid color detected: " +
                        Integer.toString(c, 16) + " instead of " +
                        Integer.toString(fb[i], 16));
            }
        }
        return flips;
    }

    private static void test(TextComponent tc) throws Exception {
        if (tc instanceof TextField) {
            System.out.println("TextField testing ...");
        } else if (tc instanceof TextArea) {
            System.out.println("TextArea testing ...");
        }

        // now passing along the component's vertical center,
        // skipping 5px from both sides,
        // we should see bg - textcolor - bg - selcolor -
        // seltextcolor - selcolor - bg
        // that is bg-fg-bg-fg-bg-fg-bg, 6 flips

        EventQueue.invokeAndWait(() -> {
            tc.setForeground(Color.green);
            tc.setBackground(Color.magenta);

            tc.setText("  I    I    ");
            tc.select(5, 10);
            tc.requestFocus();
        });
        r.waitForIdle();
        r.delay(200);
        EventQueue.invokeAndWait(() -> {
            flips = countFlips(tc);
        });
        if (flips != 6) {
            throw new RuntimeException("Invalid number of flips: "
                    + flips + " instead of 6");
        }
        EventQueue.invokeAndWait(() -> {
            // same for caret: spaces in the tc, caret in the middle
            // bg-fg-bg - 2 flips

            tc.select(0, 0);
            tc.setText("            ");
            tc.setCaretPosition(5);
        });
        r.waitForIdle();
        r.delay(200);

        for (int i = 0; i < 10; i++) {
            EventQueue.invokeAndWait(() -> {
                flips = countFlips(tc);
            });

            if (flips == 2) {
                break;
            }
            if (flips == 0) {
                continue;
            }
            throw new RuntimeException("Invalid number of flips: "
                    + flips + " instead of 2");
        }
    }
}
