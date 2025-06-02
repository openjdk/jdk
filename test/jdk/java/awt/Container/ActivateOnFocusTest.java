/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/*
 * @test
 * @bug 4111098
 * @key headful
 * @summary Test for no window activation on control requestFocus()
 * @run main/timeout=30 ActivateOnFocusTest
 */

public class ActivateOnFocusTest {
    static MyFrame mf1;
    static Point p;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                mf1 = new MyFrame();
                mf1.setBounds(100, 100, 300, 300);
                mf1.mc1.requestFocusInWindow();
            });

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                p = mf1.mb.getLocationOnScreen();
            });

            robot.waitForIdle();

            robot.mouseMove(p.x + 5, p.y + 5);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(250);

        } finally {
            if (mf1 != null) {
                EventQueue.invokeAndWait(mf1::dispose);
            }
        }
    }
}

class MyFrame extends Frame implements ActionListener {
    public Button mb;
    public MyComponent mc1;
    public MyComponent mc2;

    public MyFrame() {
        super();
        setTitle("ActivateOnFocusTest");
        setLayout(new FlowLayout());
        mb = new Button("Pull");
        mb.addActionListener(this);
        add(mb);
        mc1 = new MyComponent(Color.red);
        add(mc1);
        mc2 = new MyComponent(Color.blue);
        add(mc2);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                mc1.requestFocusInWindow();
            }
            @Override
            public void windowDeactivated(WindowEvent e) {
                mc2.requestFocusInWindow();
            }
        });
        setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        MyFrame mf2 = new MyFrame();
        mf2.setBounds(200, 200, 300, 300);
        mf2.setVisible(true);
        mf2.mc1.requestFocusInWindow();
    }
}

class MyComponent extends Component {
    public MyComponent(Color c) {
        super();
        setBackground(c);
    }

    public void paint(Graphics g) {
        Dimension d = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, d.width, d.height);
    }

    public boolean isFocusTraversable() {
        return true;
    }

    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }
}
