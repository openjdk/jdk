/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
 * @test
 * @bug 4050138
 * @key headful
 * @summary Test to verify Lightweight components don't get
 *          enter/exit during drags
 * @run main MouseEnterExitTest
 */

class LWSquare extends Container {
    int width;
    int height;

    public LWSquare(Color color, int w, int h) {
        setBackground(color);
        setLayout(new FlowLayout());
        width = w;
        height = h;
        addMouseListener(new EnterExitAdapter(this));
        setName("LWSquare-" + color.toString());
    }

    public void paint(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getSize().width, getSize().height);
        super.paint(g);
    }

    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    public Cursor getCursor() {
        return new Cursor(Cursor.CROSSHAIR_CURSOR);
    }
}

class MouseFrame extends Frame {
    public LWSquare lw;

    public MouseFrame() {
        super("MouseEnterExitTest");
        setLayout(new FlowLayout());

        lw = new LWSquare(Color.red, 75, 75);
        add(lw);
        setBounds(50, 50, 300, 200);
        setVisible(true);
        System.out.println(getInsets());

        addMouseListener(new EnterExitAdapter(this));
        addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent ev) {
                        dispose();
                    }
                }
        );
        addKeyListener(
                new KeyAdapter() {
                    public void keyPressed(KeyEvent ev) {
                        MouseEnterExitTest.getFrame().setTitle("MouseEnterExitTest");
                    }
                }
        );
    }
}


public class MouseEnterExitTest {
    static MouseFrame testFrame;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> testFrame = new MouseFrame());
            if (testFrame.lw.getBackground() != Color.red) {
                throw new RuntimeException("Initial Background color not matching");
            }
            robot.waitForIdle();
            robot.delay(100);
            EventQueue.invokeAndWait(() -> robot.mouseMove(
                    testFrame.getLocationOnScreen().x + testFrame.getSize().width / 2,
                    testFrame.getLocationOnScreen().y + testFrame.getSize().height / 2));
            robot.waitForIdle();
            robot.delay(100);

            if (testFrame.lw.getBackground() != Color.green) {
                throw new RuntimeException("Initial Background color not matching");
            }
            EventQueue.invokeAndWait(() -> robot.mouseMove(
                    testFrame.getLocationOnScreen().x + testFrame.getSize().width * 2,
                    testFrame.getLocationOnScreen().y + testFrame.getSize().height / 2));
            robot.waitForIdle();
            robot.delay(100);

            if (testFrame.lw.getBackground() != Color.red) {
                throw new RuntimeException("Initial Background color not matching");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (testFrame != null) {
                    testFrame.dispose();
                }
            });
        }
    }

    public static Frame getFrame() {
        return testFrame;
    }
}

class EnterExitAdapter extends MouseAdapter {
    Component compToColor;
    Color colorNormal;

    EnterExitAdapter(Component comp) {
        compToColor = comp;
        colorNormal = comp.getBackground();
    }

    public void mouseEntered(MouseEvent ev) {
        compToColor.setBackground(Color.green);
        compToColor.repaint();
    }

    public void mouseExited(MouseEvent ev) {
        compToColor.setBackground(colorNormal);
        compToColor.repaint();
    }
}
