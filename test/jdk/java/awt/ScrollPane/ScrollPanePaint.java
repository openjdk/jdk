/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Licensed Materials - Property of IBM
 *
 * (C) Copyright IBM Corporation 1998  All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or disclosure
 * restricted by GSA ADP Schedule Contract with IBM Corp.
 */

/*
 * @test
 * @bug 4160721
 * @summary AWT ScrollPane painting problem
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollPanePaint
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class ScrollPanePaint {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Press the button marked "Toggle" a few times.
                2. The contents of the frame should alternate between
                    a red panel and a scroll pane containing a green panel.
                    If this does not happen (specifically, if the scroll
                    pane does not consistently contain a green panel),
                    then the test has FAILED.
                """;
        ScrollPaintTest scrollPaintTest = new ScrollPaintTest();
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(scrollPaintTest::initialize)
                .positionTestUI(WindowLayouts::rightOneColumn)
                .build()
                .awaitAndCheck();
    }

    private static class ScrollPaintTest implements ActionListener {
        static Frame f;
        static boolean showScroll;

        public List<Frame> initialize() {
            Frame frame = new Frame("Scrollpane paint test");
            frame.setLayout(new BorderLayout());
            f = new Frame("Scrollpane paint test");
            f.setLayout(new GridLayout(0, 1));

            Button b = new Button("Toggle");
            b.addActionListener(this);

            frame.add(b, BorderLayout.CENTER);
            frame.pack();

            showScroll = false;
            actionPerformed(null);
            return List.of(frame, f);
        }

        public void actionPerformed(ActionEvent e) {
            Container c;
            if (!showScroll) {
                c = (Container) new TestPanel(new Dimension(100, 100));
                c.setBackground(Color.red);
            } else {
                c = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
                Panel p = new TestPanel(new Dimension(20, 20));
                p.setBackground(Color.green);
                c.add(p);
            }

            f.removeAll();
            f.add("Center", c);
            f.pack();
            showScroll = !showScroll;
        }
    }

    private static class TestPanel extends Panel {
        Dimension dim;

        TestPanel(Dimension d) {
            dim = d;
        }

        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        public Dimension getPreferredSize() {
            return dim;
        }
    }

}
