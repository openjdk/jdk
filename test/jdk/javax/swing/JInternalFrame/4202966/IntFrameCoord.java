/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4202966
 * @key headful
 * @summary Wrong coordinates in events retargeted to subcomponents of
 *      JInternalFrame
 * @run main IntFrameCoord
 */

public class IntFrameCoord {
    private static JFrame frame;
    private static JDesktopPane dt;
    private static JButton tf;
    private static volatile JButton b;
    private static JInternalFrame if1;
    private static JInternalFrame if2;
    private static boolean isFail;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        SwingUtilities.invokeAndWait(IntFrameCoord::createGUI);

        robot.delay(1000);

        MouseListener mouseListener = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                double height = b.getSize().getHeight();
                if (e.getY() >= height) {
                    isFail = true;
                } else {
                    isFail = false;
                }
            }
        };

        b.addMouseListener(mouseListener);

        robot.waitForIdle();

        robot.mouseMove(if2.getLocationOnScreen().x + (if2.getWidth() / 2),
                if2.getLocationOnScreen().y + 10);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove(if1.getLocationOnScreen().x + (if1.getWidth() / 2),
                if1.getLocationOnScreen().y + 10);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove(b.getLocationOnScreen().x + (b.getWidth() / 2),
                b.getLocationOnScreen().y + (b.getHeight() / 2));
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        SwingUtilities.invokeAndWait(() -> frame.dispose());

        if(isFail) {
            throw new RuntimeException("Mouse coordinates wrong in " +
                    "retargeted JInternalFrame");
        }
    }

    private static void createGUI() {
        frame = new JFrame();
        dt = new JDesktopPane();
        frame.setLayout(new BorderLayout());
        frame.add(BorderLayout.CENTER, dt);

        if1 = new JInternalFrame("Click here second", true, true, true, true);
        if1.setLayout(new BorderLayout());

        tf = new JButton ("ignore");
        if1.add(tf, BorderLayout.NORTH);

        tf = new JButton ("ignore");
        if1.add(tf, BorderLayout.CENTER);

        if1.setBounds(300,0,300,100);

        dt.add(if1);

        if2 = new JInternalFrame("Click here first", true, true, true, true);
        if2.setLayout(new BorderLayout());

        tf = new JButton ("ignore");
        if2.add(tf, BorderLayout.NORTH);

        b = new JButton ("Click here third");
        if2.add (b, BorderLayout.CENTER);

        if2.setBounds(0,0,300,100);

        dt.add(if2);

        if1.setVisible(true);
        if2.setVisible(true);

        frame.setLocationRelativeTo(null);
        frame.setTitle("test");
        frame.setSize(500, 300);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
