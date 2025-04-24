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

/*
 * @test
 * @bug 4058400
 * @summary Tests that calling addNotify on a lightweight component more than
 *          once does not break event dispatching for that component.
 * @key headful
 * @run main MultipleAddNotifyTest
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MultipleAddNotifyTest {
    static volatile boolean passFlag;
    static volatile int posX;
    static volatile int posY;
    static Frame f;
    static LightComponent l;

    public static void main(String[] args) throws Exception {
        Robot r;
        try {
            r = new Robot();
            r.setAutoWaitForIdle(true);
            passFlag = false;

            EventQueue.invokeAndWait(() -> {
                f = new Frame("Multiple addNotify Test");
                l = new LightComponent();
                f.setLayout(new FlowLayout());
                l.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        System.out.println("Mouse Clicked");
                        passFlag = true;
                    }
                });
                f.add(l);
                f.addNotify();
                f.addNotify();

                if (!l.isVisible()) {
                    throw new RuntimeException("Test failed. LW Component " +
                            "not visible.");
                }
                f.setSize(200, 200);
                f.setLocationRelativeTo(null);
                f.setVisible(true);
            });
            r.waitForIdle();
            r.delay(1000);

            EventQueue.invokeAndWait(() -> {
                posX = f.getX() + l.getWidth() + (l.getWidth() / 2);
                posY = f.getY() + l.getHeight();
            });

            r.mouseMove(posX, posY);
            r.delay(500);

            r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            r.delay(500);

            if (!passFlag) {
                throw new RuntimeException("Test failed. MouseClicked event " +
                        "not working properly.");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}

class LightComponent extends Component {
    public void paint(Graphics g) {
        setSize(100, 100);
        Dimension d = getSize();
        g.setColor(Color.red);
        g.fillRect(0, 0, d.width, d.height);
    }
}
