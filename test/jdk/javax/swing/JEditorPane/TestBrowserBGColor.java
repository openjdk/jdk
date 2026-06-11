/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8213781
 * @summary Verify webpage background color renders correctly in JEditorPane
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Integer.toHexString;

public final class TestBrowserBGColor {

    private static final String HTML_DOC =
            "<!DOCTYPE html>"
            + "<html><head>"
            + "<style> body { background: #FFF; } </style>"
            + "<title>Title</title></head>"
            + "<body> </body> </html>";

    private static final int SIZE = 300;

    public static void main(final String[] args) throws Exception {
        JEditorPane browser = new JEditorPane("text/html", HTML_DOC);
        browser.setEditable(false);
        browser.setSize(SIZE, SIZE);

        BufferedImage image = new BufferedImage(SIZE, SIZE, TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        browser.paint(g);
        g.dispose();

        Color bgColor = StyleConstants.getBackground(
                getBodyView(browser.getUI()
                                   .getRootView(browser))
                .getAttributes());
        if (!bgColor.equals(Color.WHITE)) {
            saveImage(image);
            throw new RuntimeException("Wrong background color: "
                                       + toHexString(bgColor.getRGB())
                                       + " vs "
                                       + toHexString(Color.WHITE.getRGB()));
        }
    }

    private static View getBodyView(final View view) {
        if ("body".equals(view.getElement()
                              .getName())) {
            return view;
        }

        return IntStream.range(0, view.getViewCount())
                        .mapToObj(view::getView)
                        .map(TestBrowserBGColor::getBodyView)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
    }

    private static void saveImage(BufferedImage image) {
        try {
            ImageIO.write(image, "png",
                          new File("html-rendering.png"));
        } catch (IOException ignored) {
        }
    }
}
