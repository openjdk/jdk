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

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;

/*
 * @test
 * @bug 4065568
 * @key headful
 * @summary Test resizing a frame containing a canvas
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameResizeTest_2
 */

public class FrameResizeTest_2 {
    private static final String INSTRUCTIONS = """
        There is a frame (size 300x300).
        The left half is red and the right half is blue.

        When you resize the frame, it should still have a red left half
        and a blue right half.

        In particular, no green should be visible after a resize.

        Upon test completion, click Pass or Fail appropriately.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FrameResizeTest_2 Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(10)
                .columns(45)
                .testUI(FrameResize_2::new)
                .build()
                .awaitAndCheck();
    }
}

class FrameResize_2 extends Frame {

    FrameResize_2() {
        super("FrameResize_2");

        setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;

        Container dumbContainer = new DumbContainer();
        add(dumbContainer, c);

        Panel dumbPanel = new DumbPanel();
        add(dumbPanel, c);

        setSize(300, 300);
    }
}


class Fake extends Canvas {
    public Fake(String name, Color what) {
        setBackground(what);
        setName(name);
    }

    public void paint(Graphics g) {
        Dimension d = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, d.width, d.height);
    }
}

class DumbContainer extends Container {
    public DumbContainer() {
        setLayout(new BorderLayout());
        add("Center", new Fake("dumbc", Color.red));
    }

    public void paint(Graphics g) {
        Dimension d = getSize();
        g.setColor(Color.green);
        g.fillRect(0, 0, d.width, d.height);
        super.paint(g);
    }
}

class DumbPanel extends Panel {
    public DumbPanel() {
        setLayout(new BorderLayout());
        add("Center", new Fake("dumbp", Color.blue));
    }

    public void paint(Graphics g) {
        Dimension d = getSize();
        g.setColor(Color.green);
        g.fillRect(0, 0, d.width, d.height);
        super.paint(g);
    }
}
