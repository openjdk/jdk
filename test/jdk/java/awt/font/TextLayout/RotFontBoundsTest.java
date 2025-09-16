/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import static javax.swing.BorderFactory.createEmptyBorder;

/*
 * @test
 * @bug 4650997
 * @summary rotate a TextLayout and verify that the bounds are correct
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RotFontBoundsTest
 */
public final class RotFontBoundsTest {
    private static final String TEXT = ".This is a STRINg.";

    private static final String INSTRUCTIONS =
            "A string \u201C" + TEXT + "\u201D is drawn at eight different "
            + "angles, and eight boxes that surround the bounds of the text "
            + "layouts (give or take a pixel) are drawn in red. The boxes "
            + "are always composed of horizontal and vertical lines \u2014 "
            + "they are not rotated.\n"
            + "\n"
            + "By default, all the rotations are displayed. Select or clear "
            + "a check box with an angle to show or hide a particular "
            + "rotation. Click \"Select All\" or \"Clear All\" to show all "
            + "the rotations or to hide them.\n"
            + "\n"
            + "Click the Pass button if each box encloses its corresponding "
            + "text layout.\n"
            + "Otherwise, click Screenshot to save a screenshot for failure "
            + "analysis and then click Fail.";

    private static boolean verbose;

    public static void main(String[] args) throws Exception {
        verbose = (args.length > 0 && args[0].equalsIgnoreCase("verbose"));

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(20)
                      .columns(50)
                      .testTimeOut(15)
                      .screenCapture()
                      .testUI(RotFontBoundsTest::createUI)
                      .build()
                      .awaitAndCheck();
    }

    private static final int ROTATIONS = 8;

    private static JComponent createUI() {
        final RotatedTextBounds rotatedText = new RotatedTextBounds();

        final JPanel checkBoxes = new JPanel(new FlowLayout(FlowLayout.CENTER,
                                                            4, 4));
        checkBoxes.setBorder(createEmptyBorder(0, 8, 8, 8));
        for (int i = 0; i < ROTATIONS; i++) {
            checkBoxes.add(new JCheckBox(new SelectRotationAction(i, rotatedText)));
        }

        JButton selectAll = new JButton("Select All");
        selectAll.addActionListener(
                e -> selectAllCheckBoxes(checkBoxes.getComponents(), true));
        selectAll.setMnemonic('S');

        JButton clearAll = new JButton("Clear All");
        clearAll.addActionListener(
                e -> selectAllCheckBoxes(checkBoxes.getComponents(), false));
        clearAll.setMnemonic('C');

        Box controls = Box.createHorizontalBox();
        controls.add(new JLabel("Visible Rotations:"));
        controls.add(Box.createHorizontalGlue());
        controls.add(selectAll);
        controls.add(Box.createHorizontalStrut(4));
        controls.add(clearAll);
        controls.setBorder(createEmptyBorder(8, 8, 0, 8));

        Box controlPanel = Box.createVerticalBox();
        controlPanel.add(controls);
        controlPanel.add(checkBoxes);

        Box javaVersion = Box.createHorizontalBox();
        javaVersion.setBorder(createEmptyBorder(8, 8, 8, 8));
        javaVersion.add(new JLabel("Java version: "
                                        + System.getProperty("java.runtime.version")));
        javaVersion.add(Box.createHorizontalGlue());

        Box main = Box.createVerticalBox();
        main.setName("Rotated TextLayout Test");
        main.add(controlPanel);
        main.add(rotatedText);
        main.add(javaVersion);

        return main;
    }

    private static final class RotatedTextBounds extends JComponent {
        private final Font font = new Font(Font.DIALOG, Font.PLAIN, 24);

        private final boolean[] rotationVisible = new boolean[ROTATIONS];

        private RotatedTextBounds() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(400, 400));
            Arrays.fill(rotationVisible, true);
        }

        public void setRotationVisible(int rotation, boolean visible) {
            rotationVisible[rotation] = visible;
            repaint();
        }

        // Counts the number of paints
        private int counter = 0;

        @Override
        public void paintComponent(Graphics _g) {
            Graphics2D g = (Graphics2D) _g;
            Dimension d = getSize();

            g.setColor(getBackground());
            g.fillRect(0, 0, d.width, d.height);

            counter++;
            int x = d.width / 2;
            int y = d.height / 2;
            FontRenderContext frc = g.getFontRenderContext();

            for (int i = 0; i < ROTATIONS; i++) {
                if (!rotationVisible[i]) {
                    continue;
                }

                double angle = -Math.PI / 4.0 * i;
                AffineTransform flip = AffineTransform.getRotateInstance(angle);
                Font flippedFont = font.deriveFont(flip);
                TextLayout tl = new TextLayout(TEXT, flippedFont, frc);
                Rectangle2D bb = tl.getBounds();
                g.setPaint(Color.BLACK);
                tl.draw(g, x, y);
                g.setPaint(Color.RED);
                g.drawRect(x + (int) bb.getX(), y + (int) bb.getY(),
                           (int) bb.getWidth(), (int) bb.getHeight());

                if (verbose) {
                    if (counter == 1) {
                        printDetails(angle, tl);
                    } else if (i == 0) {
                        System.out.println("Paint, counter=" + counter);
                    }
                }
            }
        }

        private static void printDetails(double angle, TextLayout tl) {
            System.out.println("Angle: " + angle);
            System.out.println("getAscent: " + tl.getAscent());
            System.out.println("getAdvance: " + tl.getAdvance());
            System.out.println("getBaseline: " + tl.getBaseline());
            System.out.println("getBounds: " + tl.getBounds());
            System.out.println("getDescent: " + tl.getDescent());
            System.out.println("getLeading: " + tl.getLeading());
            System.out.println("getVisibleAdvance: " + tl.getVisibleAdvance());
            System.out.println(".");
        }
    }

    private static final class SelectRotationAction
            extends AbstractAction
            implements PropertyChangeListener {
        private final int rotation;
        private final RotatedTextBounds rotatedText;

        private SelectRotationAction(int rotation,
                                     RotatedTextBounds rotatedText) {
            super(rotation * (360 / ROTATIONS) + "\u00B0");
            this.rotation = rotation;
            this.rotatedText = rotatedText;

            putValue(SELECTED_KEY, true);

            addPropertyChangeListener(this);
        }

        private void updateRotationVisible() {
            rotatedText.setRotationVisible(rotation,
                                           (Boolean) getValue(SELECTED_KEY));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            updateRotationVisible();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(SELECTED_KEY)) {
                updateRotationVisible();
            }
        }
    }

    private static void selectAllCheckBoxes(Component[] checkBoxes,
                                            boolean visible) {
        Arrays.stream(checkBoxes)
              .forEach(c -> ((JCheckBox) c).setSelected(visible));
    }
}
