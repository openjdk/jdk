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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.GlyphView;
import javax.swing.text.View;

/*
 * @test
 * @bug 8292948
 * @summary Tests font-size declarations from an external style sheet.
 * @run main TestExternalCSSFontSize
 */
public class TestExternalCSSFontSize {

    private static final int[] expectedFontSizes = { 24, 16, 12 };

    private volatile JEditorPane editor;

    private volatile CountDownLatch loadLatch;

    TestExternalCSSFontSize() {}

    void setUp() {
        String fileName = getClass().getName().replace('.', '/') + ".html";
        URL htmlFile = getClass().getClassLoader().getResource(fileName);
        if (htmlFile == null) {
            throw new IllegalStateException("Resource not found: " + fileName);
        }

        loadLatch = new CountDownLatch(1);
        editor = new JEditorPane();
        editor.setContentType("text/html");
        editor.addPropertyChangeListener("page", evt -> {
            System.out.append("Loaded: ").println(evt.getNewValue());
            loadLatch.countDown();
        });
        try {
            editor.setPage(htmlFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void verify() {
        editor.setSize(editor.getPreferredSize()); // Do lay out text

        scanFontSizes(editor.getUI().getRootView(editor), 0);
    }

    private int scanFontSizes(View view, int branchIndex) {
        int currentIndex = branchIndex;
        for (int i = 0; i < view.getViewCount(); i++) {
            View child = view.getView(i);
            if (child instanceof GlyphView) {
                if (child.getElement()
                        .getAttributes().getAttribute("CR") == Boolean.TRUE) {
                    continue;
                }
                assertFontSize((GlyphView) child, currentIndex++);
            } else {
                currentIndex = scanFontSizes(child, currentIndex);
            }
        }
        return currentIndex;
    }

    private void assertFontSize(GlyphView child, int index) {
        printSource(child);
        if (index >= expectedFontSizes.length) {
            throw new AssertionError("Unexpected text run #"
                    + index + " (>= " + expectedFontSizes.length + ")");
        }

        int actualFontSize = child.getFont().getSize();
        if (actualFontSize != expectedFontSizes[index]) {
            throw new AssertionError("Font size expected ["
                    + expectedFontSizes[index] + "] but found [" + actualFontSize +"]");
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

    void run() throws Throwable {
        SwingUtilities.invokeAndWait(this::setUp);
        if (loadLatch.await(5, TimeUnit.SECONDS)) {
            SwingUtilities.invokeAndWait(this::verify);
        } else {
            throw new IllegalStateException("Page loading timed out");
        }
    }

    public static void main(String[] args) throws Throwable {
        TestExternalCSSFontSize test = new TestExternalCSSFontSize();
        boolean success = false;
        try {
            test.run();
            success = true;
        } finally {
            if (!success || hasOpt(args, "-capture")) {
                if (test.editor == null) {
                    System.err.println("Can't save image (test.editor is null)");
                } else {
                    String suffix = success ? "-success" : "-failure";
                    SwingUtilities.invokeAndWait(() -> test.captureImage(suffix));
                }
            }
        }
    }

    private static boolean hasOpt(String[] args, String opt) {
        return Arrays.asList(args).contains(opt);
    }

    private void captureImage(String suffix) {
        try {
            BufferedImage capture =
                    new BufferedImage(editor.getWidth(), editor.getHeight(),
                                      BufferedImage.TYPE_INT_ARGB);
            Graphics g = capture.getGraphics();
            editor.paint(g);
            g.dispose();

            ImageIO.write(capture, "png",
                    new File(getClass().getSimpleName() + suffix + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
