/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.View;
import javax.swing.text.html.CSS;

/*
 * @test
 * @bug 8323801 8326734
 * @summary Tests different combination of 'underline' and 'line-through';
 *          the text should render with both 'underline' and 'line-through'.
 * @run main HTMLTextDecoration
 */
public final class HTMLTextDecoration {
    private static final String HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>underline + line-through text</title>
                <style>
                    .underline   { text-decoration: underline }
                    .lineThrough { text-decoration: line-through }
                </style>
            </head>
            <body>
            <p><u><span style='text-decoration: line-through'>underline + line-through?</span></u></p>
            <p><s><span style='text-decoration: underline'>underline + line-through?</span></s></p>
            <p><strike><span style='text-decoration: underline'>underline + line-through?</span></strike></p>

            <p><span style='text-decoration: line-through'><u>underline + line-through?</u></span></p>
            <p><span style='text-decoration: underline'><s>underline + line-through?</s></span></p>
            <p><span style='text-decoration: underline'><strike>underline + line-through?</strike></span></p>

            <p><span style='text-decoration: line-through'><span style='text-decoration: underline'>underline + line-through?</span></span></p>
            <p><span style='text-decoration: underline'><span style='text-decoration: line-through'>underline + line-through?</span></span></p>

            <p style='text-decoration: line-through'><u>underline + line-through?</u></p>
            <p style='text-decoration: underline'><s>underline + line-through?</s></p>
            <p style='text-decoration: underline'><strike>underline + line-through?</strike></p>

            <p style='text-decoration: line-through'><span style='text-decoration: underline'>underline + line-through?</span></p>
            <p style='text-decoration: underline'><span style='text-decoration: line-through'>underline + line-through?</span></p>

            <p class="underline"><span class="lineThrough">underline + line-through?</span></p>
            <p class="underline"><s>underline + line-through?</s></p>
            <p class="underline"><strike>underline + line-through?</strike></p>

            <p class="lineThrough"><span class="underline">underline + line-through?</span></p>
            <p class="lineThrough"><u>underline + line-through?</u></p>

            <div class="underline"><span class="lineThrough">underline + line-through?</span></div>
            <div class="underline"><s>underline + line-through?</s></div>
            <div class="underline"><strike>underline + line-through?</strike></div>

            <div class="lineThrough"><span class="underline">underline + line-through?</span></div>
            <div class="lineThrough"><u>underline + line-through?</u></div>

            <div class="underline"><p class="lineThrough">underline + line-through?</p></div>
            <div class="lineThrough"><p class="underline">underline + line-through?</p></div>

            <div class="underline"><div class="lineThrough">underline + line-through?</div></div>
            <div class="lineThrough"><div class="underline">underline + line-through?</div></div>
            </body>
            </html>
            """;

    public static void main(String[] args) {
        final JEditorPane html = new JEditorPane("text/html", HTML);
        html.setEditable(false);

        final Dimension size = html.getPreferredSize();
        html.setSize(size);

        BufferedImage image = new BufferedImage(size.width, size.height,
                                                BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        // Paint the editor pane to ensure all views are created
        html.paint(g);
        g.dispose();

        int errorCount = 0;
        String firstError = null;

        System.out.println("----- Views -----");
        final View bodyView = html.getUI()
                                  .getRootView(html)
                                  .getView(1)
                                  .getView(1);
        for (int i = 0; i < bodyView.getViewCount(); i++) {
            View pView = bodyView.getView(i);
            View contentView = getContentView(pView);

            String decoration =
                    contentView.getAttributes()
                               .getAttribute(CSS.Attribute.TEXT_DECORATION)
                               .toString();

            System.out.println(i + ": " + decoration);
            if (!decoration.contains("underline")
                || !decoration.contains("line-through")) {
                errorCount++;
                if (firstError == null) {
                    firstError = "Line " + i + ": " + decoration;
                }
            }
        }

        if (errorCount > 0) {
            saveImage(image);
            throw new RuntimeException(errorCount + " error(s) found, "
                                       + "the first one: " + firstError);
        }
    }

    private static View getContentView(View parent) {
        View view = parent.getView(0);
        return view.getViewCount() > 0
               ? getContentView(view)
               : view;
    }

    private static void saveImage(BufferedImage image) {
        try {
            ImageIO.write(image, "png",
                          new File("html.png"));
        } catch (IOException ignored) { }
    }
}
