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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Panel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4820080 7175397
 * @summary RFE: Cannot Change the JSplitPane Divider Color while dragging
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4820080
 */

public class bug4820080 {
    private static final String INSTRUCTIONS = """
            Drag the dividers of the splitpanes (both top and bottom). If the divider
            color is green while dragging then test passes, otherwise test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4820080::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4820080");
        UIManager.put("SplitPaneDivider.draggingColor", Color.GREEN);

        Box box = new Box(BoxLayout.Y_AXIS);
        frame.add(box);

        JPanel jleft = new JPanel();
        jleft.setBackground(Color.DARK_GRAY);
        jleft.setPreferredSize(new Dimension(100, 100));
        JPanel jright = new JPanel();
        jright.setBackground(Color.DARK_GRAY);
        jright.setPreferredSize(new Dimension(100, 100));

        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jleft, jright);
        jsp.setContinuousLayout(false);
        box.add(jsp);

        box.add(Box.createVerticalStrut(5));
        box.add(new JSeparator());
        box.add(Box.createVerticalStrut(5));

        Panel left = new Panel();
        left.setBackground(Color.DARK_GRAY);
        left.setPreferredSize(new Dimension(100, 100));
        Panel right = new Panel();
        right.setBackground(Color.DARK_GRAY);
        right.setPreferredSize(new Dimension(100, 100));

        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        sp.setContinuousLayout(false);
        box.add(sp);
        frame.pack();
        return frame;
    }
}
