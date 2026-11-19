/*
 * Copyright (c) 2026, JetBrains s.r.o. All rights reserved.
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
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.VolatileImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import sun.java2d.SunGraphicsEnvironment;

import jtreg.SkippedException;

/*
 * @test
 * @bug 8397721
 * @key headful
 * @summary VolatileSurfaceManager.displayChanged() must not allocate
 *          backup surface for VolatileImage if it is not needed
 * @library /test/lib
 * @modules java.desktop/sun.java2d
 * @run main/othervm -Xmx64m DisplayChangeOutOfMemory
 */
public class DisplayChangeOutOfMemory {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    // 50 images x 800 x 600 x 4 bytes is ~92M of heap,
    // which does not fit into the specified 64M
    private static final int N = 50;

    public static void main(String[] args) throws Exception {
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (!(ge instanceof SunGraphicsEnvironment sge)) {
            throw new SkippedException("Not a SunGraphicsEnvironment: " + ge);
        }

        CompletableFuture<Frame> frameFuture = new CompletableFuture<>();
        EventQueue.invokeLater(() -> {
            Frame frame = new Frame("DisplayChangeOutOfMemory");
            frame.setSize(WIDTH, HEIGHT);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    frameFuture.complete(frame);
                }
            });
            frame.setVisible(true);
        });
        Frame frame = frameFuture.get();

        try {
            GraphicsConfiguration gc = frame.getGraphicsConfiguration();

            List<VolatileImage> images = new ArrayList<>();
            boolean accelerated = false;
            for (int i = 0; i < N; i++) {
                VolatileImage img = gc.createCompatibleVolatileImage(WIDTH, HEIGHT);
                img.validate(gc);
                fill(img, Color.RED);
                accelerated |= img.getCapabilities(gc).isAccelerated();
                images.add(img);
            }

            if (!accelerated) {
                throw new SkippedException("No accelerated volatile images: "
                        + "the test needs an accelerated pipeline "
                        + "(e.g. -Dsun.java2d.opengl=true)");
            }

            try {
                EventQueue.invokeAndWait(sge::displayChanged);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof OutOfMemoryError oome) {
                    throw new RuntimeException(
                            "OutOfMemoryError during display change", oome);
                }
                throw e;
            }

            // Check the images after the display change
            for (VolatileImage img : images) {
                int rc = img.validate(gc);
                if (rc == VolatileImage.IMAGE_INCOMPATIBLE) {
                    throw new RuntimeException(
                            "Image is incompatible after display change");
                }
                fill(img, Color.GREEN);
            }
        } finally {
            EventQueue.invokeAndWait(frame::dispose);
        }
    }

    private static void fill(VolatileImage img, Color c) {
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(c);
            g.fillRect(0, 0, WIDTH, HEIGHT);
        } finally {
            g.dispose();
        }
    }
}
