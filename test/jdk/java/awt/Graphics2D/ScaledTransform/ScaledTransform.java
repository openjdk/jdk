/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Panel;
import java.awt.geom.AffineTransform;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @bug 8069361
 * @key headful
 * @summary SunGraphics2D.getDefaultTransform() does not include scale factor
 * @run main/timeout=300 ScaledTransform
 */
public class ScaledTransform {

    private static volatile CountDownLatch painted;
    private static volatile boolean passed;
    private static volatile Dialog dialog;
    private static volatile long startTime;
    private static volatile long endTime;

    public static void main(String[] args) throws Exception {
        GraphicsEnvironment ge = GraphicsEnvironment.
                getLocalGraphicsEnvironment();

        for (GraphicsDevice gd : ge.getScreenDevices()) {
            System.out.println("Screen = " + gd);
            test(gd.getDefaultConfiguration());
            /* Don't want to run too long. Test the default and up to 10 more */
            GraphicsConfiguration[] configs = gd.getConfigurations();
            for (int c = 0; c < configs.length && c < 10; c++) {
                test(configs[c]);
            }
        }
    }

    static void test(GraphicsConfiguration gc) throws Exception {
        try {
            /* reset vars for each run */
            passed = false;
            dialog = null;
            painted = new CountDownLatch(1);
            EventQueue.invokeLater(() -> showDialog(gc));
            startTime = System.currentTimeMillis();
            endTime = startTime;
            if (!painted.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Panel is not painted!");
            }
            System.out.println("Time to paint = " + (endTime - startTime) + "ms.");
            if (!passed) {
                throw new RuntimeException("Transform is not scaled!");
            }
        } finally {
            EventQueue.invokeAndWait(() -> disposeDialog());
        }
    }

    private static void showDialog(final GraphicsConfiguration gc) {
        System.out.println("Creating dialog for gc=" + gc + " with tx=" + gc.getDefaultTransform());
        dialog = new Dialog((Frame) null, "ScaledTransform", true, gc);
        dialog.setSize(300, 100);

        Panel panel = new Panel() {

            @Override
            public void paint(Graphics g) {
                System.out.println("Painting panel");
                if (g instanceof Graphics2D g2d) {
                    AffineTransform gcTx = gc.getDefaultTransform();
                    AffineTransform gTx = g2d.getTransform();
                    System.out.println("GTX = " + gTx);
                    passed = (gcTx.getScaleX() == gTx.getScaleX()) &&
                             (gcTx.getScaleY() == gTx.getScaleY());
                } else {
                    passed = true;
                }
                endTime = System.currentTimeMillis();
                painted.countDown();
                System.out.println("Painted panel");
            }
        };
        dialog.add(panel);
        dialog.setVisible(true);
    }

    private static void disposeDialog() {
       if (dialog != null) {
           System.out.println("Disposing dialog");
           dialog.setVisible(false);
           dialog.dispose();
       }
    }
}
