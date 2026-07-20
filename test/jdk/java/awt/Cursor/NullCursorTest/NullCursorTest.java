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
 * @bug 4111379
 * @summary Test for setting cursor to null for lightweight components
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NullCursorTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class NullCursorTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                 1. Hover over each colored area as described:
                        Green area shows a CrossCursor.
                        Red area shows a TextCursor.
                        Yellow area shows a HandCursor.
                 2. Click once in red area, then:
                        Green area shows a HandCursor.
                        Red area shows a BusyCursor.
                        Yellow area shows a HandCursor.
                 3. Click again in red area, then:
                        Green area shows a CrossCursor.
                        Red area shows a HandCursor.
                        Yellow area shows a HandCursor.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(NullCursorTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("Null Cursor Test Frame");
        f.setSize(200, 200);
        final Container p = f;
        p.setName("parent");
        p.setLayout(null);

        final Component green = p.add(new Component() {
            public void paint(Graphics g) {
                Rectangle r = getBounds();
                g.setColor(Color.green);
                g.fillRect(0, 0, r.width, r.height);
            }
        });
        green.setName("green");
        green.setBackground(Color.red);
        green.setBounds(50, 50, 75, 75);
        green.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        Container h = new Container() {
            public void paint(Graphics g) {
                Rectangle r = getBounds();
                g.setColor(Color.yellow);
                g.fillRect(0, 0, r.width, r.height);
                super.paint(g);
            }
        };
        h.setBounds(15, 25, 150, 150);
        h.setName("container");
        h.setBackground(Color.yellow);
        h.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        final Component red = new Component() {
            public void paint(Graphics g) {
                Rectangle r = getBounds();
                Color c = getBackground();
                g.setColor(c);
                g.fillRect(0, 0, r.width, r.height);
                super.paint(g);
            }
        };
        red.setName("red");
        red.setBackground(Color.red);
        red.setBounds(10, 10, 120, 120);
        red.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        final Button b = (Button)h.add(new Button("Test"));
        b.setBounds(10, 10, 40, 20);
        h.add(red);
        p.add(h);

        b.addActionListener(new ActionListener() {
            boolean f = false;
            public void actionPerformed(ActionEvent e) {
                if (f) {
                    b.setCursor(null);
                } else {
                    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                f = !f;
            }
        });
        red.addMouseListener(new MouseAdapter() {
            boolean f = true;

            public void mouseClicked(MouseEvent e) {
                Component c = (Component)e.getSource();
                if (f) {
                    c.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    p.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                    green.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    f = false;
                } else {
                    c.setCursor(null);
                    p.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    green.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    f = true;
                }
            }
        });
        return f;
    }
}
