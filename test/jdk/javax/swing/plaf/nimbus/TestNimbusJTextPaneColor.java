/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8058704
 * @key headful
 * @summary  Verifies if Nimbus honor JTextPane background color
 * @run main TestNimbusJTextPaneColor
 */
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestNimbusJTextPaneColor {

    static JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                } catch (Exception checkedExceptionsPleaseDie) {
                    throw new RuntimeException(checkedExceptionsPleaseDie);
                }
                JTextPane tp = new JTextPane();
                tp.setForeground(Color.WHITE);
                tp.setBackground(Color.BLACK);
                tp.setText("This text should be white on black");

                frame = new JFrame();
                frame.setDefaultCloseOperation(frame.DISPOSE_ON_CLOSE);
                frame.add(tp);
                frame.setSize(new Dimension(480, 360));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            Thread.sleep(1000);
            Robot robot = new Robot();
            Point pt = frame.getLocationOnScreen();
            if (!(robot.getPixelColor(pt.x + frame.getBounds().width/2,
                                  pt.y + frame.getBounds().height/2)
                                .equals(Color.BLACK))) {
                throw new RuntimeException("JTextPane Color not same as the color being set");
            }
        } finally {
            if (frame != null) {
                SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }
}
