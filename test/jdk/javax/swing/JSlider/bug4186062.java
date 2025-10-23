/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4382876
 * @summary Tests if JSlider fires ChangeEvents when thumb is clicked and not moved
 * @key headful
 * @run main bug4186062
 */

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

public class bug4186062 {
    private static JFrame f;
    private static JSlider slider;
    private static volatile Point loc;
    private static volatile int labelNum;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("JSlider Click Value Test");
                f.setSize(400, 200);
                f.setLocationRelativeTo(null);
                f.setVisible(true);
                JPanel panel = new JPanel();
                slider = new JSlider();
                final JLabel label = new JLabel("0");
                labelNum = 0;

                ChangeListener listener = e -> {
                    labelNum++;
                    label.setText("" + labelNum);
                };
                slider.addChangeListener(listener);

                panel.add(slider);
                panel.add(label);
                f.add(panel);
            });

            Robot r = new Robot();
            r.setAutoDelay(100);
            r.waitForIdle();
            r.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                loc = slider.getLocationOnScreen();
                loc.setLocation(loc.x + (slider.getWidth() / 2),
                        loc.y + (slider.getHeight() / 2));
            });

            r.mouseMove(loc.x, loc.y);
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (labelNum > 0) {
                throw new RuntimeException(labelNum + " ChangeEvents fired. " +
                        "Test failed");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
