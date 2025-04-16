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
 * @test
 * @bug 4117404
 * @summary Tests that child component is always at least large as scrollpane
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollPaneSize
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.util.List;

public class ScrollPaneSize {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Three frames representing the three different ScrollPane scrollbar
                   policies will appear.
                2. Verify that when you resize the windows, the child component in the
                   scrollpane always expands to fill the scrollpane. The scrollpane
                   background is colored red to show any improper bleed through.
                   """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(ScrollPaneSize::initialize)
                .positionTestUIRightColumn()
                .build()
                .awaitAndCheck();
    }

    static List<Frame> initialize() {
        return List.of(new ScrollFrame("SCROLLBARS_AS_NEEDED", ScrollPane.SCROLLBARS_AS_NEEDED),
                new ScrollFrame("SCROLLBARS_ALWAYS", ScrollPane.SCROLLBARS_ALWAYS),
                new ScrollFrame("SCROLLBARS_NEVER", ScrollPane.SCROLLBARS_NEVER));
    }
}

class ScrollFrame extends Frame {
    ScrollFrame(String title, int policy) {
        super(title);
        setLayout(new GridLayout(1, 1));
        ScrollPane c = new ScrollPane(policy);
        c.setBackground(Color.red);
        Panel panel = new TestPanel();
        c.add(panel);
        add(c);
        pack();
        Dimension size = panel.getPreferredSize();
        Insets insets = getInsets();
        setSize(size.width + 45 + insets.right + insets.left,
                size.height + 20 + insets.top + insets.bottom);
    }
}

class TestPanel extends Panel {
    TestPanel() {
        setLayout(new FlowLayout());
        setBackground(Color.white);

        Button b1, b2, b3;
        add(b1 = new Button("Button1"));
        add(b2 = new Button("Button2"));
        add(b3 = new Button("Button3"));
    }
}
