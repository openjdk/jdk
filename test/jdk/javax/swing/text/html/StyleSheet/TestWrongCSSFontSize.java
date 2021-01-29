/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.GlyphView;
import javax.swing.text.View;

/*
 * @test
 * @bug 8257664
 * @summary  Tests inherited font-size with parent percentage specification.
 * @run main TestWrongCSSFontSize
 */
public class TestWrongCSSFontSize {

    private static String text =
            "<html><head><style>" +
            "body { font-size: 14 }" +
            "div span { font-size: 150% }" +
            "span { font-size: 200% }" +
            "h2, .h2 { font-size: 150% }" +
            "</style></head><body>" +

            "<h2>Foo</h2>" +
            "<div class=h2>Bar</div>" +
            "<ol class=h2><li>Baz</li></ol>" +
            "<table class=h2><tr><td>Qux</td></tr></table>" +
            "<table><thead class=h2><tr><th>Qux</th></tr></thead></table>" +
            "<table><tr class=h2><td>Qux</td></tr></table>" +
            "<table><tr><td class=h2>Qux</td></tr></table>" +
            "<div><span>Quux</span></div>" +

            "</body></html>";

    private static int expectedFontSize = 21;
    private static int expectedAssertions = 8;

    private JEditorPane editor;

    public void setUp() {
        editor = new JEditorPane();
        editor.setContentType("text/html");
        editor.setText(text);
        editor.setSize(editor.getPreferredSize()); // layout
    }

    public void run() {
        int count = forEachTextRun(editor.getUI()
                .getRootView(editor), this::assertFontSize);
        if (count != expectedAssertions) {
            throw new AssertionError("assertion count expected ["
                    + expectedAssertions + "] but found [" + count + "]");
        }
    }

    private int forEachTextRun(View view, Consumer<GlyphView> action) {
        int tested = 0;
        for (int i = 0; i < view.getViewCount(); i++) {
            View child = view.getView(i);
            if (child instanceof GlyphView) {
                if (child.getElement()
                        .getAttributes().getAttribute("CR") == Boolean.TRUE) {
                    continue;
                }
                action.accept((GlyphView) child);
                tested += 1;
            } else {
                tested += forEachTextRun(child, action);
            }
        }
        return tested;
    }

    private void assertFontSize(GlyphView child) {
        printSource(child);
        int actualFontSize = child.getFont().getSize();
        if (actualFontSize != expectedFontSize) {
            throw new AssertionError("font size expected ["
                    + expectedFontSize + "] but found [" + actualFontSize +"]");
        }
    }

    private void printSource(View textRun) {
        try {
            editor.getEditorKit().write(System.out,
                    editor.getDocument(), textRun.getStartOffset(),
                    textRun.getEndOffset() - textRun.getStartOffset());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void captureImage(Component comp, String path) {
        try {
            BufferedImage capture = new BufferedImage(comp.getWidth(),
                    comp.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics g = capture.getGraphics();
            comp.paint(g);
            g.dispose();

            ImageIO.write(capture, "png", new File(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Throwable {
        TestWrongCSSFontSize test = new TestWrongCSSFontSize();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.setUp();
                test.run();
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                if (args.length == 1) {
                    captureImage(test.editor, args[0]);
                }
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

}
