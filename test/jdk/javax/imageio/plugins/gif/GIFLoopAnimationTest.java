/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Image;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * @ test id=one-frame
 * @ignore Sanity check
 * @key headful
 * @summary One-frame GIF without loop extension doesn't animate
 * @build GIFWriter
 * @run main GIFLoopAnimationTest one-frame
 */

/*
 * @test id=one-frame-loop
 * @bug 8316165
 * @key headful
 * @summary One-frame GIF with the infinite loop stops animation
 * @build GIFWriter
 * @run main GIFLoopAnimationTest one-frame-loop
 */

/*
 * @test id=one-frame-loop2
 * @bug 8316165
 * @key headful
 * @summary One-frame GIF with a fixed loop repeat stops animating
 * @build GIFWriter
 * @run main GIFLoopAnimationTest one-frame-loop2
 */

/*
 * @test id=five-frames
 * @key headful
 * @summary Five-frame GIF without loop stops animating at the last frame
 * @build GIFWriter
 * @run main GIFLoopAnimationTest five-frames
 */

/*
 * @test id=five-frames-loop5
 * @bug 8316165
 * @key headful
 * @summary Five-frame GIF with 5 repeats stops animating after
 *          repeating animation 5 times
 * @build GIFWriter
 * @run main GIFLoopAnimationTest five-frames-loop5
 */

public class GIFLoopAnimationTest {

    private static JFrame frame;

    private record TestData(String fileName,
                            int frameCount,
                            int loopCount) {
    }

    private static final List<TestData> testList =
        List.of(new TestData("one-frame.gif", 1, 1),
                new TestData("one-frame-loop.gif", 1, 0),
                new TestData("one-frame-loop2.gif", 1, 2),
                new TestData("five-frames.gif", 5, 1),
                new TestData("five-frames-loop5.gif", 5, 5)
        );


    private static final CountDownLatch done = new CountDownLatch(1);
    // Access to the error is synchronized by the above latch
    private static Throwable error;


    private static void createGIFImage(final TestData test) throws IOException {
        try (GIFWriter gifWriter = new GIFWriter(test.fileName,
                                                 test.frameCount,
                                                 test.loopCount)) {
            gifWriter.writeFrames();
        }
    }

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Pass a test name as the first and only argument");
        }

        final TestData test =
                testList.stream()
                        .filter(t -> t.fileName.startsWith(args[0]))
                        .findFirst()
                        .orElseThrow();


        System.out.println("Starting " + test);
        createGIFImage(test);

        SwingUtilities.invokeAndWait(() -> createUI(test));

        try {
            if (test.loopCount == 1 && test.frameCount == 1) {
                if (done.await(3, SECONDS)) {
                    throw new RuntimeException("imageUpdate should not be called for one-frame GIF");
                }
            }

            if (done.await(3, SECONDS)) {
                if (error != null) {
                    throw error;
                }
            }

            System.out.println("Test: " + test + " - passed");
        } finally {
            SwingUtilities.invokeAndWait(GIFLoopAnimationTest::disposeUI);
        }
    }

    private static JLabel createGIFLabel(final TestData test) {
        return new JLabel(new ImageIcon(test.fileName)) {
            private int frame = 0;
            @Override
            public boolean imageUpdate(Image img, int flags,
                                       int x, int y, int w, int h) {
                if (test.frameCount == 1 && test.loopCount == 1) {
                    error = new Error(
                            "Animation must not start for one-frame image - "
                            + "imageUpdate must never be called");
                    done.countDown();
                }

                final boolean result = super.imageUpdate(img, flags, x, y, w, h);
                if ((flags & (ImageObserver.ALLBITS
                              | ImageObserver.FRAMEBITS)) == 0) {
                    // Skip any other invocations of imageUpdate
                    return result;
                }

                System.out.println("> imageUpdate: " + frame);

                if ((flags & ImageObserver.ALLBITS) != 0) {
                    System.out.println("    ALLBITS");
                    done.countDown();
                } else if ((flags & ImageObserver.FRAMEBITS) != 0) {
                    System.out.println("    FRAMEBITS");
                    System.out.println("    " + (++frame));

                    if (test.frameCount == 1) {
                        error = new Error(
                                "GIF with one frame should stop animating "
                                + "after showing the frame");
                        done.countDown();
                    }

                    if (test.loopCount != 0) {
                        if (frame > test.frameCount * test.loopCount) {
                            error = new Error(
                                    "GIF with fixed number of loops should stop "
                                    + "animating after repeating animation loop "
                                    + "the specified amount of times");
                            System.out.println("    too many loops");
                            done.countDown();
                        }
                    } else {
                        System.out.println("    infinite looping");
                        if (frame > 500) {
                            if (error != null) {
                                error = new Error("Infinite animation - too many frames");
                            }
                            done.countDown();
                        }
                    }
                }

                System.out.println("< imageUpdate");
                return result;
            }
        };
    }

    private static void createUI(TestData test) {
        JLabel gifLabel = createGIFLabel(test);

        frame = new JFrame("GIF Loop Animation Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setMinimumSize(new Dimension(300, 200));
        frame.add(gifLabel);
        frame.pack();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void disposeUI() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
