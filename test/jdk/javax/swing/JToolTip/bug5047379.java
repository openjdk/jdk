/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
/* @test
   @key headful
   @bug 5047379
   @summary Checks that Tooltips are rendered properly for Metal Look and Feel
   @library ../regtesthelpers
   @build Util
   @run main bug5047379
*/

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.plaf.metal.MetalToolTipUI;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;

public class bug5047379 {
    static JFrame frame;
    static JButton a;
    static JButton b;
    static JButton c;
    static JButton d;
    static Robot testRobot;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        testRobot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                runTest();
            }
        });
        testRobot.delay(1000);
        testRobot.waitForIdle();
        Point movePoint = getButtonPoint(b);
        testRobot.mouseMove(movePoint.x, movePoint.y);
        testRobot.delay(2000);
        testRobot.waitForIdle();
        handleToolTip();
    }


    static void handleToolTip() throws Exception {
        SwingUtilities.updateComponentTreeUI(frame);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    JToolTip tooltip = (JToolTip) Util.findSubComponent(
                            JFrame.getFrames()[0], "JToolTip");

                    MetalToolTipUI toolTipObj = (MetalToolTipUI) MetalToolTipUI.createUI(tooltip);

                    if (tooltip == null) {
                        throw new RuntimeException("Metal Tooltip not been found");
                    }
                    checkAcclString(toolTipObj, tooltip);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    frame.dispose();
                }
            }
        });
    }

    static void checkAcclString(MetalToolTipUI toolTipObj, JToolTip tooltip) {
        toolTipObj.installUI(tooltip);

        if (toolTipObj.getAcceleratorString() == null) {
            throw new RuntimeException("Acceleration String for ToolTip of Metal L&F is null");
        }
    }

    static Point getButtonPoint(JButton button) throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                Point p = button.getLocationOnScreen();
                Dimension size = button.getSize();
                result[0] = new Point(p.x + size.width / 2, p.y + size.height / 2);
            }
        });
        return result[0];
    }

    static void runTest() {
        frame = new JFrame();
        JTextArea area = new JTextArea();
        JPanel p = new JPanel();

        String text = "The mouse will hover over button B, so the\t\t\n";
        text += "ToolTip will appear. Here is what should show\t\t\n";
        text += "The word \\\"TEXT\\\" and then \\\"CTRL-B\\\"\\n\"\t\t";
        text += "\n";

        area.setText(text);
        area.setEditable(false);
        area.setFocusable(false);

        frame.add(area, BorderLayout.CENTER);

        p.setLayout(new GridLayout(1, 5));

        a = new JButton("A");
        a.setMnemonic(KeyEvent.VK_A);
        a.setToolTipText("TEXT");
        p.add(a);

        b = new JButton("B");
        b.setMnemonic(KeyEvent.VK_B);
        b.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl B"), "foo");
        b.setToolTipText("TEXT");
        p.add(b);

        c = new JButton("C");
        c.setMnemonic(KeyEvent.VK_C);
        c.setToolTipText("<html>TEXT");
        p.add(c);

        d = new JButton("D");
        d.setMnemonic(KeyEvent.VK_D);
        d.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl D"), "foo");
        d.setToolTipText("<html>TEXT");
        p.add(d);
        frame.add(p, BorderLayout.NORTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
