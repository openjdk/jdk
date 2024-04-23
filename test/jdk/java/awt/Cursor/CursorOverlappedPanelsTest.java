/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.lang.reflect.InvocationTargetException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/*
 * @test
 * @bug 8007155
 * @summary [macosx] Disabled panel takes mouse input in JLayeredPane
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CursorOverlappedPanelsTest
 */

public class CursorOverlappedPanelsTest extends Frame {
    public static JFrame initialize() {
        final JFrame frame = new JFrame("Overlapping Panels Cursor Test");

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(400, 400));
        JPanel enabledPanel = createPanel(new Point(10, 10), true);
        JPanel disabledPanel = createPanel(new Point(100, 100), false);
        layeredPane.add(disabledPanel, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(enabledPanel, JLayeredPane.DEFAULT_LAYER);

        frame.getContentPane().add(layeredPane);
        frame.pack();
        return frame;
    }

    private static JPanel createPanel(Point location, boolean enabled) {
        final JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setEnabled(enabled);
        panel.setSize(new Dimension(200, 200));
        panel.setLocation(location);
        panel.setBorder(BorderFactory.createTitledBorder(
                enabled ? "Enabled" : "Disabled"));
        panel.setCursor(Cursor.getPredefinedCursor(
                enabled ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
        return panel;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions = """
            1) Move the mouse cursor into the area
               of Enabled and Disabled panels intersection;
            2) Check that the crosshair cursor is displayed.
            If so, press PASS, otherwise press FAIL.
            """;

        PassFailJFrame.builder()
                .title("Overlapping Panels Cursor Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(30)
                .testUI(CursorOverlappedPanelsTest::initialize)
                .build()
                .awaitAndCheck();
    }
}
