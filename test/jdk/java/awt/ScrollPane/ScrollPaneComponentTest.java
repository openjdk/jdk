/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4100671
 * @summary removing and adding back ScrollPane component does not work
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollPaneComponentTest
 */

import java.awt.Adjustable;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ScrollPaneComponentTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Notice the scrollbars (horizontal and vertical)
                   in the Frame titled 'ScrollPane Component Test'
                2. Click the button labeled 'Remove and add back
                   ScrollPane Contents'
                3. If the Scrollbars (horizontal or vertical or both)
                   disappears in the Frame, then press FAIL, else press PASS.
                                   """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ScrollPaneComponentTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame fr = new Frame("ScrollPane Component Test");
        fr.setLayout(new BorderLayout());
        ScrollTester test = new ScrollTester();

        fr.add(test);
        fr.pack();
        fr.setSize(200, 200);

        Adjustable vadj = test.pane.getVAdjustable();
        Adjustable hadj = test.pane.getHAdjustable();
        vadj.setUnitIncrement(5);
        hadj.setUnitIncrement(5);
        return fr;
    }
}

class Box extends Component {
    public Dimension getPreferredSize() {
        System.out.println("asked for size");
        return new Dimension(300, 300);
    }

    public void paint(Graphics gr) {
        super.paint(gr);
        gr.setColor(Color.red);
        gr.drawLine(5, 5, 295, 5);
        gr.drawLine(295, 5, 295, 295);
        gr.drawLine(295, 295, 5, 295);
        gr.drawLine(5, 295, 5, 5);
        System.out.println("Painted!!");
    }
}

class ScrollTester extends Panel {
    public ScrollPane pane;
    private final Box child;

    class Handler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            System.out.println("Removing scrollable component");
            pane.remove(child);
            System.out.println("Adding back scrollable component");
            pane.add(child);
            System.out.println("Done Adding back scrollable component");
        }
    }

    public ScrollTester() {
        pane = new ScrollPane();
        pane.setSize(200, 200);
        child = new Box();
        pane.add(child);
        setLayout(new BorderLayout());
        Button changeScrollContents = new Button("Remove and add back ScrollPane Contents");
        changeScrollContents.setBackground(Color.red);
        changeScrollContents.addActionListener(new Handler());
        add("North", changeScrollContents);
        add("Center", pane);
    }
}
