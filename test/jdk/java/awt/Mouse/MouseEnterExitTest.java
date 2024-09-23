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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/*
 * @test
 * @bug 4050138
 * @summary Test to verify Lightweight components don't get
 *          enter/exit during drags
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MouseEnterExitTest
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

    public Dimension preferredSize() {
        return new Dimension(width, height);
    }

    public Cursor getCursor() {
        return new Cursor(Cursor.CROSSHAIR_CURSOR);
    }
}

class HWSquare extends Panel {
    int width;
    int height;

    public HWSquare(Color color, int w, int h) {
        setBackground(color);
        width = w;
        height = h;
        addMouseListener(new EnterExitAdapter(this));
        setName("HWSquare-" + color.toString());

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent ev) {
                MouseEnterExitTest.getFrame().setTitle("MouseEnterExitTest");
                MouseEnterExitTest.TestFailed = false;
            }
        });
    }

    public void paint(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getSize().width, getSize().height);
        super.paint(g);
    }

    public Dimension preferredSize() {
        return new Dimension(width, height);
    }
}

class MouseFrame extends Frame {
    public MouseFrame() {
        super("MouseEnterExitTest");
        setLayout(new FlowLayout());

        LWSquare lw = new LWSquare(Color.red, 75, 75);
        lw.add(new HWSquare(Color.blue, 32, 32));
        add(lw);

        LWSquare lw2 = new LWSquare(Color.red, 75, 75);
        lw2.add(new LWSquare(Color.yellow, 32, 32));
        add(lw2);

        add(new HWSquare(Color.blue, 75, 75));

        HWSquare hw = new HWSquare(Color.blue, 75, 75);
        hw.add(new LWSquare(Color.red, 30, 30));

        add(hw);
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
                        MouseEnterExitTest.TestFailed = false;
                    }
                }
        );
    }
}


public class MouseEnterExitTest {
    static boolean TestFailed = false;
    static Frame TestFrame;

    public MouseEnterExitTest() {
        runTest();
    }

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            1. Move the mouse into any Frame,
            2. Verify that the frame background color changes to Green on enter
               and back to set color on exit events.",
            3. If the color doesn't change on either enter/exit
               then test fails.
                            """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(runTest())
                .build()
                .awaitAndCheck();
    }

    public static Frame getFrame() {
        return TestFrame;
    }

    public static List<Frame> runTest() {
        TestFrame = new MouseFrame();
        Frame otherFrame = new Frame("Other Frame");
        otherFrame.setBounds(350, 50, 150, 200);
        otherFrame.setVisible(true);
        otherFrame.addMouseListener(new EnterExitAdapter(otherFrame));
        return List.of(TestFrame, otherFrame);
    }
}

class EnterExitAdapter extends MouseAdapter {
    Component   compToColor;
    Color       colorNormal;

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
