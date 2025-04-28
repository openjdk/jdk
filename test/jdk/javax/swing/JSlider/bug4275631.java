/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4275631
 * @summary Tests if vertical JSlider is properly aligned in large container
 * @key headful
 * @run main bug4275631
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Robot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

public class bug4275631 {
    private static final int OFFSET = 1;
    private static JFrame f;
    private static JSlider slider1;
    private static JSlider slider2;
    private static volatile Point loc1;
    private static volatile Point loc2;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("JSlider Alignment Test");
                f.setSize(400, 200);
                f.setLocationRelativeTo(null);

                // Create two sliders, verify the alignment on the slider to be
                // used in the border layout
                slider1 = new JSlider(JSlider.VERTICAL, 0, 99, 50);
                slider1.setInverted(true);
                slider1.setMajorTickSpacing(10);
                slider1.setMinorTickSpacing(1);
                slider1.setPaintTicks(true);
                slider1.setPaintLabels(true);
                slider2 = new JSlider(JSlider.VERTICAL, 0, 99, 50);
                slider2.setInverted(true);
                slider2.setMajorTickSpacing(10);
                slider2.setMinorTickSpacing(1);
                slider2.setPaintTicks(true);
                slider2.setPaintLabels(true);

                // Try to center the natural way, using a border layout in the "Center"
                JPanel borderPanel = new JPanel();
                borderPanel.setLayout(new BorderLayout());
                borderPanel.setBorder(BorderFactory.createTitledBorder("BorderLayout"));
                borderPanel.add(slider1, BorderLayout.CENTER);
                borderPanel.setPreferredSize(new Dimension(200, 200));

                // Try to center using GridBagLayout, with glue on left
                // and right to squeeze slider into place
                JPanel gridBagPanel = new JPanel(new GridBagLayout());
                gridBagPanel.setBorder(BorderFactory.createTitledBorder("GridBagLayout"));
                GridBagConstraints c = new GridBagConstraints();
                c.gridx = 1;
                c.fill = GridBagConstraints.VERTICAL;
                c.weighty = 1.0;
                gridBagPanel.add(slider2, c);
                c.gridx = 0;
                c.fill = GridBagConstraints.BOTH;
                c.weighty = 0.0;
                gridBagPanel.add(Box.createHorizontalGlue(), c);
                c.gridx = 2;
                c.fill = GridBagConstraints.BOTH;
                gridBagPanel.add(Box.createHorizontalGlue(), c);
                gridBagPanel.setPreferredSize(new Dimension(200, 200));

                f.add(borderPanel, BorderLayout.WEST);
                f.add(gridBagPanel, BorderLayout.EAST);
                f.setVisible(true);
            });

            Robot r = new Robot();
            r.setAutoDelay(100);
            r.waitForIdle();
            r.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                loc1 = slider1.getLocationOnScreen();
                loc1.setLocation(loc1.x + (slider1.getWidth() / 2),
                        loc1.y + (slider1.getHeight() / 2));

                loc2 = slider2.getLocationOnScreen();
                loc2.setLocation(loc2.x + (slider2.getWidth() / 2),
                        loc2.y + (slider2.getHeight() / 2));
            });

           if (loc1.y > loc2.y + OFFSET || loc1.y < loc2.y - OFFSET) {
               throw new RuntimeException("JSlider position is not aligned!");
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
