/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025082
 * @summary The behaviour of the highlight will be lost after clicking the set
 * button.
 * @run main bug8025082
 */
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import javax.swing.*;

public class bug8025082 {

    private static JButton button;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        Robot robo = new Robot();
        robo.delay(500);

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                createUI();
            }
        });

        robo.waitForIdle();
        Point point = getButtonLocationOnScreen();
        robo.mouseMove(point.x, point.y);
        robo.mousePress(InputEvent.BUTTON1_MASK);
        robo.mouseRelease(InputEvent.BUTTON1_MASK);
        robo.waitForIdle();

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                frame.dispose();
            }
        });
    }

    private static void createUI() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 500);
        JTextPane textpane = new JTextPane();
        textpane.setText("Select Me");
        textpane.selectAll();

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(textpane, BorderLayout.CENTER);
        button = new JButton("Press Me");
        panel.add(button, BorderLayout.SOUTH);

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!textpane.getCaret().isSelectionVisible()) {
                    throw new RuntimeException("Highlight removed after "
                            + "button click");
                }
            }
        });

        frame.getContentPane().add(panel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static Point getButtonLocationOnScreen() throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                Point point = button.getLocationOnScreen();
                point.x += button.getWidth() / 2;
                point.y += button.getHeight() / 2;
                result[0] = point;
            }
        });
        return result[0];
    }
}
