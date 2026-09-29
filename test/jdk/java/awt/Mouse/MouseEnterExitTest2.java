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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/*
 * @test
 * @bug 4150851
 * @summary Tests enter and exit events when a lightweight component is on a border
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MouseEnterExitTest2
 */

public class MouseEnterExitTest2 {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Verify that white component turns black whenever mouse enters the frame,
                   except when it enters the red rectangle.
                2. When the mouse enters the red part of the frame the component should stay white.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(EntryExitTest.initialize())
                .build()
                .awaitAndCheck();
    }
}

class EntryExitTest extends Component {
    boolean inWin;

    public Dimension getPreferredSize() {
        return new Dimension(200, 150);
    }

    public void paint(Graphics g) {
        Color c1, c2;
        String s;
        if (inWin) {
            c1 = Color.black;
            c2 = Color.white;
            s = "IN";
        } else {
            c2 = Color.black;
            c1 = Color.white;
            s = "OUT";
        }
        g.setColor(c1);
        Rectangle r = getBounds();
        g.fillRect(0, 0, r.width, r.height);
        g.setColor(c2);
        g.drawString(s, r.width / 2, r.height / 2);
    }

    public static Frame initialize() {
        EntryExitTest test = new EntryExitTest();
        MouseListener frameEnterExitListener = new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                test.inWin = true;
                test.repaint();
            }

            public void mouseExited(MouseEvent e) {
                test.inWin = false;
                test.repaint();
            }
        };

        Frame f = new Frame("Mouse Modifier Test");

        f.add(test);
        Component jc = new Component() {
            public Dimension getPreferredSize() {
                return new Dimension(100, 50);
            }

            public void paint(Graphics g) {
                Dimension d = getSize();
                g.setColor(Color.red);
                g.fillRect(0, 0, d.width, d.height);
            }
        };
        final Container cont = new Container() {
            public Dimension getPreferredSize() {
                return new Dimension(100, 100);
            }
        };
        cont.setLayout(new GridLayout(2, 1));
        cont.add(jc);
        jc.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                //System.out.println("Component entered");
            }
            public void mouseExited(MouseEvent e) {
                //System.out.println("Component exited");
            }
        });

        f.add(cont, BorderLayout.NORTH);
        f.addMouseListener(frameEnterExitListener);
        f.pack();
        return f;
    }
}
