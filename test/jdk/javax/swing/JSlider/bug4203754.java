/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.Robot;
import java.util.Dictionary;
import java.util.Hashtable;

/*
 * @test
 * @bug 4203754
 * @key headful
 * @summary Labels in a JSlider don't disable or enable with the slider
 * @run main bug4203754
 */

public class bug4203754 {
    private static JFrame frame;
    private static Robot robot;
    private static JLabel label;

    public static void main(String[] argv) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Test");
                frame.getContentPane().setLayout(new FlowLayout());
                JSlider slider = new JSlider(0, 100, 25);
                frame.getContentPane().add(slider);

                label = new JLabel("0", JLabel.CENTER) {
                    public void setEnabled(boolean b) {
                        super.setEnabled(b);
                    }
                };

                Dictionary labels = new Hashtable();
                labels.put(Integer.valueOf(0), label);
                slider.setLabelTable(labels);
                slider.setPaintLabels(true);
                slider.setEnabled(false);
                frame.setSize(250, 150);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            if (label.isEnabled()) {
                throw new RuntimeException("Label should be disabled");
            }
            System.out.println("Test Passed!");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
