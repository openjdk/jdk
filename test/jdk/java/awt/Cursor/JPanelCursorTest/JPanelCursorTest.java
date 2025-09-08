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
 * @bug 4114073
 * @summary Test for setCursor in a JPanel when added to a JFrame's contentPane
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual JPanelCursorTest
 */

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.border.BevelBorder;

public class JPanelCursorTest {
    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                This test checks for setCursor in a JPanel when added to a
                JFrame's contentPane.

                1. Verify that the cursor in the left side of the test window
                    is a text cursor.
                2. Verify that the cursor changes to the crosshair cursor when
                    pointing over the button.
                3. Verify that the cursor changes to the hand cursor when in
                    the right side of the splitpane (and not on the button).
                4. Verify that the cursor changes to the wait cursor when in
                    the empty bottom section of the test window.

                If true, then pass the test. Otherwise, fail this test.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(37)
                .testUI(JPanelCursorTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createUI() {
        JFrame frame = new JFrame("Cursor Test Frame");

        JSplitPane j = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        ExtJComponent pane = new ExtJComponent();

        CursorBugPanel panel = new CursorBugPanel();

        j.setLeftComponent(pane);
        j.setRightComponent(panel);
        j.setContinuousLayout(true);
        j.setSize(200, 200);

        frame.getContentPane().add("North", j);
        pane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        frame.setSize(300, 200);
        return frame;
    }
}

class ExtJComponent extends JComponent {
    public ExtJComponent() {
        super();
        setOpaque(true);
        setBackground(Color.green);
        setForeground(Color.red);
        setBorder(new BevelBorder(BevelBorder.RAISED));
    }
    public void paintComponent(Graphics g) {
        g.drawString("Text", 20, 30);
    }
    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }
}

class CursorBugPanel extends JPanel {
    public CursorBugPanel() {
        // BUG: fails to set cursor for panel
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Create a button
        JButton button = new JButton("Crosshair");

        // Sets cursor for button, no problem
        button.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        add(button);
    }

    public void paintComponent(Graphics g) {
        g.drawString("Hand", 20, 60);
    }
}
