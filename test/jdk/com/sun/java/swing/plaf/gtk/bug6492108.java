/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

import jtreg.SkippedException;

/*
 * @test
 * @bug 6492108 8160755
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies that the background is painted the same for
 *          JTextArea, JTextPane, and JEditorPane.
 * @library /javax/swing/regtesthelpers /test/lib
 * @build SwingTestHelper Util
 * @run main/othervm bug6492108
 */

public class bug6492108 extends SwingTestHelper {

    private JPanel panel;

    public static void main(String[] args) throws Throwable {
        try {
            UIManager.setLookAndFeel(
                "com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        } catch (Exception e) {
            throw new SkippedException("GTK LAF is not supported on this system");
        }
        new bug6492108().run(args);
    }

    private static void addTextComps(Container parent,
                                     Class<? extends JTextComponent> type)
            throws Throwable
    {
        JTextComponent text = type.newInstance();
        addTextComp(parent, text);

        text = type.newInstance();
        text.setEditable(false);
        addTextComp(parent, text);

        text = type.newInstance();
        text.setEnabled(false);
        addTextComp(parent, text);

        text = type.newInstance();
        text.setEnabled(false);
        text.setEditable(false);
        addTextComp(parent, text);
    }

    private static void addTextComp(Container parent, JTextComponent text) {
        JScrollPane sp = new JScrollPane(text);
        text.setFocusable(false); // to avoid showing the blinking caret
        sp.setPreferredSize(new Dimension(150, 150));
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        parent.add(sp);
    }

    protected Component createContentPane() {
        panel = new JPanel();
        panel.setLayout(new GridLayout(3, 4));
        try {
            addTextComps(panel, JTextArea.class);
            addTextComps(panel, JEditorPane.class);
            addTextComps(panel, JTextPane.class);
        } catch (Throwable t) {
            fail("Problem creating text components");
        }
        return panel;
    }

    private void onEDT10() {
        requestAndWaitForFocus(panel);
    }

    private void onEDT20() {
        // For each component on the top row, compare against the two
        // components below in the same column.  All three components in
        // that column should be the same pixel-for-pixel.
        for (int count = 0; count < 4; count++) {
            Component ref = panel.getComponent(count);
            Rectangle refRect = new Rectangle(ref.getLocationOnScreen(), ref.getSize());
            BufferedImage refImg = robot.createScreenCapture(refRect);

            for (int k = 1; k < 3; k++) {
                int index = count + (k*4);
                Component test = panel.getComponent(index);
                Rectangle testRect = new Rectangle(test.getLocationOnScreen(), test.getSize());
                BufferedImage testImg = robot.createScreenCapture(testRect);

                if (!Util.compareBufferedImages(refImg, testImg)) {
                    try {
                        ImageIO.write(refImg, "png", new File("refImg.png"));
                        ImageIO.write(testImg, "png", new File("testImg.png"));
                    } catch (IOException ignored) {}

                    fail("Image comparison failed for images at index " + count + " and " + index);
                }
            }
        }
    }
}
