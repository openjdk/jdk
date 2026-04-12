/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5100806
 * @summary TextArea.select(0,0) does not de-select the selected text properly
 * @run main CorrectTextComponentSelectionTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.TextComponent;
import java.awt.TextField;

public class CorrectTextComponentSelectionTest {
    static TextField tf = new TextField("TextField");
    static TextArea ta = new TextArea("TextArea");
    static Robot r;
    static Frame frame;
    static volatile Color color_center;

    public static void main(String[] args) throws Exception {
        try {
            r = new Robot();
            EventQueue.invokeAndWait(() -> initialize());
            r.waitForIdle();
            r.delay(1000);

            test(tf);
            test(ta);
            System.out.println("Test Passed!");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void initialize() {
        frame = new Frame("TextComponent Selection Test");
        frame.setLayout(new BorderLayout());

        // We should place to the text components the long strings in order to
        // cover the components by the selection completely
        tf.setText(" ".repeat(50));
        // We check the color of the text component in order to find out the
        // bug reproducible situation
        tf.setForeground(Color.WHITE);
        tf.setBackground(Color.WHITE);

        ta.setText((" ".repeat(50) + "\n").repeat(50));
        ta.setForeground(Color.WHITE);
        ta.setBackground(Color.WHITE);

        frame.add(tf, "North");
        frame.add(ta, "South");
        frame.setSize(200, 200);
        frame.setVisible(true);
    }

    private static void test(TextComponent tc) throws Exception {
        if (tc instanceof TextField) {
            System.out.println("TextField testing ...");
        } else if (tc instanceof TextArea) {
            System.out.println("TextArea testing ...");
        }

        r.waitForIdle();
        r.delay(100);

        EventQueue.invokeAndWait(() -> {
            tc.requestFocus();
            tc.selectAll();
            tc.select(0, 0);
        });

        r.waitForIdle();
        r.delay(100);

        EventQueue.invokeAndWait(() -> {
            Point p = tc.getLocationOnScreen();
            p.translate(tc.getWidth() / 2, tc.getHeight() / 2);
            color_center = r.getPixelColor(p.x, p.y);
        });

        System.out.println("Color of the text component (CENTER) = " + color_center);
        System.out.println("White color = " + Color.WHITE);

        if (color_center.getRGB() != Color.WHITE.getRGB()) {
            throw new RuntimeException("Test Failed");
        }
    }
}
