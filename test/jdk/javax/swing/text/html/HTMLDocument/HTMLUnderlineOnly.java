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
 * @bug 8326734
 * @summary Tests different combinations of setting 'underline'
 * @run main HTMLUnderlineOnly
 */
public class HTMLUnderlineOnly {
    private static final String HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>underline</title>
                <style>
                    .underline   { text-decoration: underline }
                </style>
            </head>
            <body>
            <p><u><span style='text-decoration: underline'>underline?</span></u></p>
            <p><span style='text-decoration: underline'><u>underline?</u></span></p>

            <p><u><span class="underline">underline?</span></u></p>
            <p><span class="underline"><u>underline?</u></span></p>

            <p style='text-decoration: underline'><u>underline?</u></p>
            <p style='text-decoration: underline'><span style='text-decoration: underline'>underline?</span></p>

            <p class="underline"><u>underline</u></p>
            <p class="underline"><span style='text-decoration: underline'>underline</span></p>
            <p class="underline"><span class="underline">underline</span></p>
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
                || decoration.contains("line-through")) {
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
