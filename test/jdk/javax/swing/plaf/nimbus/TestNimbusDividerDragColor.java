/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4820080
   @summary RFE: Cannot Change the JSplitPane Divider Color while dragging
   @key headful
   @library /java/awt/regtesthelpers
   @build PassFailJFrame
   @run main/manual TestNimbusDividerDragColor
*/

import java.awt.Color;
import java.awt.Panel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestNimbusDividerDragColor {

    private static JFrame frame;

    private static final String INSTRUCTIONS =
        "Drag the dividers of the splitpanes (both top and bottom).\n " +
        " If the divider color is green while dragging\n " +
        " then test passes, otherwise test fails";

    public static void init() {
        UIManager.put("SplitPaneDivider.draggingColor", Color.green);

        frame = new JFrame();
        Box box = new Box(BoxLayout.Y_AXIS);
        frame.getContentPane().add(box);

        JPanel jleft = new JPanel();
        jleft.setBackground(Color.darkGray);
        JPanel jright = new JPanel();
        jright.setBackground(Color.darkGray);
        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jleft, jright);
        jsp.setContinuousLayout(false);
        box.add(jsp);

        box.add(Box.createVerticalStrut(5));
        box.add(new JSeparator());
        box.add(Box.createVerticalStrut(5));

        Panel left = new Panel();
        left.setBackground(Color.darkGray);
        Panel right = new Panel();
        right.setBackground(Color.darkGray);
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        sp.setContinuousLayout(false);
        box.add(sp);
        frame.add(box);
        frame.setSize(200, 200);
        frame.setVisible(true);
    }


    public static void main(String[] argv) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        PassFailJFrame passFailJFrame = new PassFailJFrame(
                "JFileChooser Test Instructions", INSTRUCTIONS, 5);
        SwingUtilities.invokeAndWait(() -> init());
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(
                frame, PassFailJFrame.Position.HORIZONTAL);
        passFailJFrame.awaitAndCheck();
    }
}
