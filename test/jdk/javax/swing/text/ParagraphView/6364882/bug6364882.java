/*
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/*
 * @test
 * @bug 6364882 8273634
 * @summary tests if broken and last lines in paragraph are not justified
 * @run main bug6364882
 */
public class bug6364882 {
    private static final String TEXT =
            "<html><body><p style=\"text-align: justify\">"
            + "should be justified should be justified should be justified "
            + "should be justified should be justified should be justified "
            + "should be justified should be justified should be justified "
            + "should be justified should be justified should be justified "
            + "should be justified should be justified should be justified "
            + "should be justified should be justified should be justified "
            + "<br>"
            + "should not be justified <br>"
            + "should not be justified"
            + "</body></html>";

    private static final int WIDTH = 580;
    private static final int HEIGHT = 600;

    public static final String IMAGE_FILENAME = "editorPane.png";

    private static JEditorPane editorPane;

    private static volatile List<Error> errors;

    public static void main(String[] args) throws Exception {
        List<String> argList = Arrays.asList(args);
        // Show frame for visual inspection
        final boolean showFrame = argList.contains("-show");
        // Save the rendered image even if the test passes
        // If the test fails, the image is always saved
        final boolean saveImage = argList.contains("-save");

        SwingUtilities.invokeAndWait(() -> {
            createUI(showFrame);

            BufferedImage image = paintToImage();
            errors = checkJustification();

            if (errors.size() > 0 || saveImage) {
                saveImage(image);
                dumpViews();
            }
        });

        if (errors != null && errors.size() > 0) {
            String message = "Test failed: " + errors.size() + " error(s)";
            System.err.println(message);
            for (Error e : errors) {
                e.printStackTrace();
            }
            throw new RuntimeException(message + " - " + errors.get(0).getMessage());
        }

        System.out.println("Test passed");
    }

    private static void createUI(boolean showFrame) {
        editorPane = new JEditorPane();
        editorPane.setEditorKit(new HTMLEditorKit());
        ((AbstractDocument) editorPane.getDocument()).setAsynchronousLoadPriority(-1);
        editorPane.setText(TEXT);

        editorPane.setSize(WIDTH, HEIGHT);

        if (showFrame) {
            JFrame frame = new JFrame("bug6364882");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            frame.getContentPane().add(editorPane);

            frame.setSize(WIDTH, HEIGHT);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
    }

    private static List<Error> checkJustification() {
        final List<Error> errors = new ArrayList<>(15);
        try {
            final View rootView = editorPane.getUI().getRootView(editorPane);
            final View blockView = rootView.getView(0);
            assert blockView.getViewCount() == 2
                   : "blockView doesn't have 2 child views";
            final View bodyView = blockView.getView(1);
            final View paragraphView = bodyView.getView(0);
            // Expected to have 6 rows in the paragraph
            assert paragraphView.getViewCount() == 6
                   : "paragraph doesn't have 6 rows of text";

            final Rectangle bounds = editorPane.getBounds();

            // Three rows should be justified
            final int oneX = getEndOfLineX(paragraphView.getView(0), bounds);
            if (oneX < bounds.width - 15) {
                errors.add(new Error("Text is not justified at line " + 0 + ": "
                                     + oneX + " < " + (bounds.width - 15)));
            }
            for (int i = 1; i < 2; i++) {
                int lineX = getEndOfLineX(paragraphView.getView(i),
                                          bounds);
                if (oneX != lineX) {
                    errors.add(new Error("Text is not justified at line " + i
                                         + ": " + oneX + " != " + lineX));
                }
            }

            // Fourth row should not be justified
            final int fourX = getEndOfLineX(paragraphView.getView(3), bounds);
            if (oneX == fourX) {
                errors.add(new Error("Fourth line is justified: "
                                     + oneX + " vs " + fourX));
            }
            if (fourX > (bounds.width - bounds.width / 4)) {
                errors.add(new Error("Fourth line is justified: "
                                     + fourX + " > "
                                     + (bounds.width - bounds.width / 4)));
            }

            // Fifth and sixth lines should not be justified
            final int fiveX = getEndOfLineX(paragraphView.getView(4), bounds);
            if (oneX == fiveX) {
                errors.add(new Error("Fifth line is justified: "
                                     + oneX + "==" + fiveX));
            }
            if (fiveX > bounds.width / 2) {
                errors.add(new Error("Fifth line is justified: "
                                     + fiveX + " > " + (bounds.width / 2)));
            }
            if (fiveX > fourX) {
                errors.add(new Error("Fifth line is justified: "
                                     + fiveX + " > " + fourX));
            }
            final int sixX = getEndOfLineX(paragraphView.getView(5), bounds);
            if (fiveX != sixX) {
                errors.add(new Error("Fifth and sixth lines aren't of  the "
                                     + "same width: " + fiveX + " != " + sixX));
            }

            return errors;
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getEndOfLineX(final View rowView,
                                     final Rectangle bounds)
            throws BadLocationException {
        final View inlineView = rowView.getView(0);
        Shape loc = inlineView.modelToView(inlineView.getEndOffset() - 1,
                                           bounds,
                                           Position.Bias.Backward);
        return loc instanceof Rectangle
               ? ((Rectangle) loc).x
               : loc.getBounds().x;
    }

    private static BufferedImage paintToImage() {
        Dimension bounds = editorPane.getSize();
        BufferedImage im = new BufferedImage(bounds.width, bounds.height,
                                             TYPE_INT_RGB);
        Graphics g = im.getGraphics();
        editorPane.paint(g);
        g.dispose();
        return im;
    }

    private static void saveImage(BufferedImage image) {
        try {
            ImageIO.write(image, "png", new File(IMAGE_FILENAME));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void dumpViews() {
        final View view = editorPane.getUI().getRootView(editorPane);
        dumpViews(view, "");
    }

    private static void dumpViews(final View view, final String indent) {
        System.out.println(indent + view.getClass().getName() + ": "
                           + view.getStartOffset() + ", " + view.getEndOffset()
                           + "; span: " + view.getPreferredSpan(View.X_AXIS));
        final String nestedIndent = indent + "    ";
        for (int i = 0; i < view.getViewCount(); i++) {
            dumpViews(view.getView(i), nestedIndent);
        }
    }
}
