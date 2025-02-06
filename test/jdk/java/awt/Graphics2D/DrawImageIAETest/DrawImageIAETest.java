/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4191004
 * @summary Tests that no IllegalArgumentException is thrown when calling
 *           drawImage with certain conditions
 * @key headful
 */

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.geom.GeneralPath;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class DrawImageIAETest extends Frame {

     static String filename = "/duke.gif";
     private volatile Image dimg;
     private volatile BufferedImage bimg;
     static volatile DrawImageIAETest app;
     static volatile JFrame jframe;
     static volatile boolean passed = true;
     static volatile Exception exception = null;
     static volatile CountDownLatch imageLatch = new CountDownLatch(1);

     DrawImageIAETest(String title) {
         super(title);
     }

     public static void main(final String[] args) throws Exception {
         EventQueue.invokeAndWait(DrawImageIAETest:: createUI);
         imageLatch.await(3, TimeUnit.MILLISECONDS);
         try {
             if (!passed) {
                 throw new RuntimeException("Test FAILED: exception caught:" + exception);
             }
         } finally {
            if (jframe != null) {
                EventQueue.invokeAndWait(jframe::dispose);
            }
            if (app != null) {
                EventQueue.invokeAndWait(app::dispose);
            }
         }
     }

     static void createUI() {
         app = new DrawImageIAETest("DrawImageIAETest");
         app.setLayout (new BorderLayout());
         app.setSize(200,200);
         app.setLocationRelativeTo(null);
         app.setVisible(true);

         String file;
         try {
             String dir = System.getProperty("test.src",
                                             System.getProperty("user.dir"));
             file = dir + filename;
         } catch (Exception e) {
             file = "." + filename;
         }

         Image textureAlphaSource = null;
         MediaTracker tracker = new MediaTracker(app);
         app.dimg = Toolkit.getDefaultToolkit().getImage(file);
         tracker.addImage(app.dimg, 1);
         try {
             tracker.waitForAll();
             imageLatch.countDown();
         } catch (Exception e) {
             System.err.println("Can't load images");
         }

         if (app.dimg == null) {
             passed = false;
             return;
         }

         jframe = new JFrame("Test DrawImageIAETest");
         jframe.setSize(300, 300);
         JPanel jpanel;
         jframe.getContentPane().add("Center", jpanel = new JPanel() {
             public void paint(Graphics _g) {
                 Graphics2D g = (Graphics2D)_g;
                 Dimension d = getSize();
                 Graphics2D g2 = app.createGraphics2D(d.width, d.height);
                 app.drawDemo(d.width, d.height, g2);
                 g2.dispose();
                 g.drawImage(app.bimg, 0, 0, app);
             }
         });
         jpanel.setSize(140, 140);
         jframe.setVisible(true);
    }

    public void drawDemo(int w, int h, Graphics2D g2) {
        GeneralPath p1 = new GeneralPath();
        GeneralPath p2 = new GeneralPath();

        int dukeX = 73;
        int dukeY = 26;

        double x = 118;
        double y = 17;
        double ew = 50;
        double eh = 48;

        p1.append(new Ellipse2D.Double(x, y, ew, eh), false);
        p2.append(new Rectangle2D.Double(x+5, y+5, ew-10, eh-10),false);

        g2.setClip(p1);
        g2.clip(p2);
        try {
            g2.drawImage(dimg, dukeX, dukeY, null);
        } catch (IllegalArgumentException e) {
            passed = false;
            exception = e;
        }
    }

    public Graphics2D createGraphics2D(int w, int h) {
        Graphics2D g2 = null;
        if (bimg == null || bimg.getWidth() != w || bimg.getHeight() != h) {
            bimg = (BufferedImage) createImage(w, h);
        }
        g2 = bimg.createGraphics();
        g2.setBackground(getBackground());
        g2.clearRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        return g2;
    }
}
