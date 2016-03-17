/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import static java.lang.Double.NaN;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * @test
 * @bug 8149338
 * @summary Verifies that Marlin supports NaN coordinates and no JVM crash happens !
 * @run main CrashNaNTest
 */
public class CrashNaNTest {

    static final boolean SAVE_IMAGE = false;

    public static void main(String argv[]) {
        Locale.setDefault(Locale.US);

        // initialize j.u.l Looger:
        final Logger log = Logger.getLogger("sun.java2d.marlin");
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                Throwable th = record.getThrown();
                // detect any Throwable:
                if (th != null) {
                    System.out.println("Test failed:\n" + record.getMessage());
                    th.printStackTrace(System.out);

                    throw new RuntimeException("Test failed: ", th);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        });

        // enable Marlin logging & internal checks:
        System.setProperty("sun.java2d.renderer.log", "true");
        System.setProperty("sun.java2d.renderer.useLogger", "true");
        System.setProperty("sun.java2d.renderer.doChecks", "true");

        final int width = 400;
        final int height = 400;

        final BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setBackground(Color.WHITE);
            g2d.clearRect(0, 0, width, height);

            final Path2D.Double path = new Path2D.Double();
            path.moveTo(30, 30);
            path.lineTo(100, 100);

            for (int i = 0; i < 20000; i++) {
                path.lineTo(110 + 0.01 * i, 110);
                path.lineTo(111 + 0.01 * i, 100);
            }

            path.lineTo(NaN, 200);
            path.lineTo(200, 200);
            path.lineTo(200, NaN);
            path.lineTo(300, 300);
            path.lineTo(NaN, NaN);
            path.lineTo(100, 100);
            path.closePath();

            final Path2D.Double path2 = new Path2D.Double();
            path2.moveTo(0,0);
            path2.lineTo(width,height);
            path2.lineTo(10, 10);
            path2.closePath();

            for (int i = 0; i < 1; i++) {
                final long start = System.nanoTime();
                g2d.setColor(Color.BLUE);
                g2d.fill(path);

                g2d.fill(path2);

                final long time = System.nanoTime() - start;
                System.out.println("paint: duration= " + (1e-6 * time) + " ms.");
            }

            if (SAVE_IMAGE) {
                try {
                    final File file = new File("CrashNaNTest.png");
                    System.out.println("Writing file: "
                            + file.getAbsolutePath());
                    ImageIO.write(image, "PNG", file);
                } catch (IOException ex) {
                    System.out.println("Writing file failure:");
                    ex.printStackTrace();
                }
            }
        } finally {
            g2d.dispose();
        }
    }
}
