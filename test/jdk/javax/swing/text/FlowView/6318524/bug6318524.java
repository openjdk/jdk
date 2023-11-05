/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/*
 * @test
 * @bug 6318524 8239502
 * @summary Tests that children of ParagraphView do not mess up their parents
 * @run main bug6318524
 */
/*
 * Test parameters:
 * -show: Show frame for visual inspection
 * -save: Save the start image after the first paragraph is justified,
 *        and the last image before it's checked that the first paragraph
 *        remains justified
 * -saveAll: Save images for all the intermediate steps
 */
public class bug6318524 {
    private static final String LONG_WORD = "consequences";
    private static final String TEXT = "Justified: "
            + LONG_WORD + " " + LONG_WORD;
    private static final int REPEAT_COUNT = 18;

    private static JTextPane textPane;
    private static Dimension bounds;

    private static int step = 0;

    private static Shape firstLineEndsAt;

    public static void main(String[] args) throws Throwable {
        List<String> argList = Arrays.asList(args);

        // Show frame for visual inspection
        final boolean showFrame = argList.contains("-show");
        // Save images for all the intermediate steps
        final boolean saveAllImages = argList.contains("-saveAll");
        // Save the start and last image only
        final boolean saveImage = saveAllImages || argList.contains("-save");

        SwingUtilities.invokeAndWait(() -> {
            createUI(showFrame);
            paintToImage(step++, saveAllImages);
            makeLineJustified();
            paintToImage(step++, saveImage);

            firstLineEndsAt = getEndOfFirstLine();

            moveCursorToStart();
            pressEnter(saveAllImages);

            paintToImage(step++, saveImage);
            checkLineJustified();
        });
    }

    private static void createUI(boolean showFrame) {
        textPane = new JTextPane();
        textPane.setText(TEXT);

        FontMetrics fm = textPane.getFontMetrics(textPane.getFont());
        int textWidth = fm.stringWidth(LONG_WORD);
        int textHeight = fm.getHeight();
        bounds = new Dimension(2 * textWidth,
                               (REPEAT_COUNT + 3) * textHeight);
        textPane.setPreferredSize(bounds);
        textPane.setSize(bounds);

        if (showFrame) {
            JFrame frame = new JFrame("bug6318524");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            frame.getContentPane().add(textPane);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
    }

    private static void makeLineJustified() {
        SimpleAttributeSet sas = new SimpleAttributeSet();
        StyleConstants.setAlignment(sas, StyleConstants.ALIGN_JUSTIFIED);
        textPane.setParagraphAttributes(sas, false);
    }

    private static void moveCursorToStart() {
        // Move cursor to the beginning
        Caret caret = textPane.getCaret();
        caret.setDot(0);
    }

    private static void pressEnter(boolean saveImages) {
        Document doc = textPane.getDocument();
        try {
            for (int i = 0; i < REPEAT_COUNT; i++) {
                // Add a new paragraph at the beginning
                doc.insertString(0, "\n", null);
                // Paint the textPane after each change
                paintToImage(step++, saveImages);
            }
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkLineJustified() {
        Shape newPosition = getEndOfFirstLine();
        if (((Rectangle) firstLineEndsAt).x != ((Rectangle) newPosition).x) {
            System.err.println("Old: " + firstLineEndsAt);
            System.err.println("New: " + newPosition);
            throw new RuntimeException("The first line of the paragraph is not justified");
        }
    }

    private static Shape getEndOfFirstLine() {
        try {
            final View rootView = textPane.getUI().getRootView(textPane);
            final View boxView = rootView.getView(0);
            final View paragraphView = boxView.getView(boxView.getViewCount() - 1);
            assert paragraphView.getViewCount() == 2;
            final View rowView = paragraphView.getView(0);
            return rowView.getView(0)
                          .modelToView(rowView.getEndOffset() - 1,
                                       textPane.getBounds(),
                                       Position.Bias.Backward);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void paintToImage(final int step, boolean saveImage) {
        BufferedImage im = new BufferedImage(bounds.width, bounds.height,
                TYPE_INT_RGB);
        Graphics g = im.getGraphics();
        textPane.paint(g);
        g.dispose();
        if (saveImage) {
            saveImage(im, String.format("%02d.png", step));
        }
    }

    private static void saveImage(BufferedImage image, String fileName) {
        try {
            ImageIO.write(image, "png", new File(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
