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
 * @bug 8058704 6789980
 * @key headful
 * @summary  Verifies if Nimbus honor JTextPane and JEditorPane background color
 * @run main TestNimbusBGColor
 */
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

public class TestNimbusBGColor {

    static JFrame frame;
    static volatile Point pt;
    static volatile Rectangle bounds;
    static Robot robot;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        testTextPane();
        testEditorPane();
    }

    private interface ComponentCreator<T extends JTextComponent> {
        T createComponent();
    }

    private static void testTextPane() throws Exception {
        testComponent(JTextPane::new);
    }

    private static void testEditorPane() throws Exception {
        testComponent(() -> {
            JEditorPane ep = new JEditorPane();
            ep.setContentType("text/plain");
            return ep;
        });
    }

    private static void testComponent(ComponentCreator<? extends JTextComponent> creator)
            throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                } catch (Exception checkedExceptionsPleaseDie) {
                    throw new RuntimeException(checkedExceptionsPleaseDie);
                }
                JTextComponent tc = creator.createComponent();
                tc.setEditable(false);
                tc.setForeground(Color.GREEN);
                tc.setBackground(Color.RED);
                tc.setText("This text should be green on red");

                frame = new JFrame(tc.getClass().getName());
                frame.setDefaultCloseOperation(frame.DISPOSE_ON_CLOSE);
                frame.add(tc);
                frame.setSize(new Dimension(480, 360));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                pt = frame.getLocationOnScreen();
                bounds = frame.getBounds();
            });
            if (!(robot.getPixelColor(pt.x + bounds.width/2,
                                      pt.y + bounds.height/2)
                                .equals(Color.RED))) {
                throw new RuntimeException("bg Color not same as the color being set");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
