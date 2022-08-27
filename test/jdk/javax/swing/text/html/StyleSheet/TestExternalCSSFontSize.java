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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import java.awt.Component;
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

    private static final int[] expectedFontSizes = { 21, 14, 12 };

    private JEditorPane editor;

    private volatile Throwable failure;

    TestExternalCSSFontSize() {}

    CountDownLatch setUp() throws Exception {
        String fileName = getClass().getName().replace('.', '/') + ".html";
        URL htmlFile = getClass().getClassLoader().getResource(fileName);
        if (htmlFile == null) {
            throw new FileNotFoundException("Resource not found: " + fileName);
        }

        CountDownLatch finishLatch = new CountDownLatch(1);
        editor = new JEditorPane();
        editor.setContentType("text/html");
        editor.addPropertyChangeListener("page", evt -> {
            System.out.append("Loaded: ").println(evt.getNewValue());
            try {
                run();
            } catch (Throwable e) {
                failure = e;
            } finally {
                finishLatch.countDown();
            }
        });
        editor.setPage(htmlFile);
        return finishLatch;
    }

    void run() {
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

    void start() throws Throwable {
        AtomicReference<CountDownLatch> finishLatch = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                finishLatch.set(setUp());
            } catch (Throwable e) {
                failure = e;
            }
        });

        if (finishLatch.get() != null
                && !finishLatch.get().await(5, TimeUnit.SECONDS)
                && failure == null) {
            throw new IllegalStateException("Page loading timed out");
        }

        if (failure != null) {
            throw failure;
        }
    }

    public static void main(String[] args) throws Throwable {
        TestExternalCSSFontSize test = new TestExternalCSSFontSize();
        boolean success = false;
        try {
            test.start();
            success = true;
        } finally {
            if (!success && test.editor != null) {
                SwingUtilities.invokeAndWait(() -> captureImage(test.editor, "-failure"));
            } else if (hasOpt(args, "-capture")) {
                SwingUtilities.invokeAndWait(() -> captureImage(test.editor, "-success"));
            }
        }
    }

    private static boolean hasOpt(String[] args, String opt) {
        return Arrays.asList(args).contains(opt);
    }

    static void captureImage(Component comp, String suffix) {
        try {
            BufferedImage capture = new BufferedImage(comp.getWidth(),
                                comp.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics g = capture.getGraphics();
            comp.paint(g);
            g.dispose();

            ImageIO.write(capture, "png",
                    new File(TestExternalCSSFontSize.class
                                .getSimpleName() + suffix + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
