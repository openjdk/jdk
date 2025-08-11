/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4094581
 * @summary ScrollPane does not refresh properly when child is just smaller than viewport
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollPanechildViewportTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ScrollPanechildViewportTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Click "Slightly Large" and ensure scrollbars are VISIBLE
                2. Click "Slightly Small" and ensure there are NO scrollbars
                3. Click "Smaller" and ensure there are NO scrollbars
                4. If everything is ok, click PASS, else click FAIL.
                                          """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ScrollPanechildViewportTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        return new Test();
    }
}

class Test extends Frame implements ActionListener {
    Button b1, b2, b3;
    MyPanel p;
    int state; // 0 = slightly large, 1 = slightly smaller, 2 = smaller

    public Test() {
        ScrollPane sp = new ScrollPane();
        p = new MyPanel();
        p.setBackground(Color.yellow);
        state = 1;
        sp.add(p);
        add(sp, "Center");

        Panel p1 = new Panel();
        b1 = new Button("Slightly Large");
        b1.addActionListener(this);
        p1.add(b1);
        b2 = new Button("Slightly Small");
        b2.addActionListener(this);
        p1.add(b2);
        b3 = new Button("Smaller");
        b3.addActionListener(this);
        p1.add(b3);

        add(p1, "South");

        setSize(400, 200);
        //added to test to move test frame away from instructions
        setLocation(0, 350);
    }

    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        // set size to small and re-validate the panel to get correct size of
        // scrollpane viewport without scrollbars

        state = 2;
        p.invalidate();
        validate();

        Dimension pd = ((ScrollPane) p.getParent()).getViewportSize();

        if (source.equals(b1)) {
            p.setBackground(Color.green);
            state = 0;
        } else if (source.equals(b2)) {
            p.setBackground(Color.yellow);
            state = 1;
        } else if (source.equals(b3)) {
            p.setBackground(Color.red);
            state = 2;
        }

        p.invalidate();
        validate();
        System.out.println("Panel Size = " + p.getSize());
        System.out.println("ScrollPane Viewport Size = " + pd);
        System.out.println(" ");
    }

    class MyPanel extends Panel {
        public Dimension getPreferredSize() {
            Dimension d = null;
            Dimension pd = ((ScrollPane) getParent()).getViewportSize();
            switch (state) {
                case 0 -> {
                    d = new Dimension(pd.width + 2, pd.height + 2);
                    System.out.println("Preferred size: " + d);
                }
                case 1 -> {
                    d = new Dimension(pd.width - 2, pd.height - 2);
                    System.out.println("Preferred size: " + d);
                }
                case 2 -> {
                    d = new Dimension(50, 50);
                    System.out.println("Preferred size: " + d);
                }
            }
            return d;
        }
    }
}
