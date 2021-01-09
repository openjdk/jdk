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


import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Line2D;

import javax.swing.JFrame;
import javax.swing.JPanel;

/*
 * @test
 * @bug 7018932
 * @summary fix LoopPipe.getStrokedSpans() performance (clipping enabled by Marlin renderer)
 * @run main DrawingTest7018932
 */
public class DrawingTest7018932 extends JPanel {

    private static final boolean USE_AA = false;

    private static final long MAX_TIME = 5 * 1000L; // 5s

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(new DrawingTest7018932(), BorderLayout.CENTER);
        frame.setSize(400, 400);
        frame.setVisible(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(MAX_TIME);
                } catch (InterruptedException ie) {
                    System.err.println("DrawingTest7018932: interrupted.");
                    ie.printStackTrace(System.err);
                }
                // too long, exit KO:
                end(false);
            }

        }).start();
    }

    @Override
    public void paintComponent(Graphics g) {
        final long start = System.nanoTime();

        Graphics2D g2 = (Graphics2D) g;

        if (USE_AA) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

// clip - doesn't help
        g2.setClip(0, 0, getWidth(), getHeight());

// this part is just testing the drawing - so I can see I am actually drawing something
// IGNORE
        /**
     g.setColor(Color.GREEN);
     g.fillRect(0, 0, getWidth(), getHeight());
     g.setColor(Color.black);
        g2.setStroke(new BasicStroke(2));
     g2.draw(new Line2D.Double(20, 20, 200, 20));
    
     /**/
// Now we re-create the exact conditions that lead to the system crash in the JDK
// BUG HERE - setting the stroke leads to the crash
        Stroke stroke = new BasicStroke(2.0f, 1, 0, 1.0f, new float[]{0.0f, 4.0f}, 0.0f);
        g2.setStroke(stroke);

        // NOTE: Large values to trigger crash / infinite loop?
        g2.draw(new Line2D.Double(4.0, 1.794369841E9, 567.0, -2.147483648E9));

        // after 1 draw finishes, exit OK:
        System.out.println("DrawingTest7018932: duration= " + (1e-6 * (System.nanoTime() - start)) + " ms.");
        end(true);
    }

    static void end(boolean ok) {
        System.out.println("DrawingTest7018932: " + ((ok) ? "PASS" : "FAIL"));
        System.exit((ok) ? 0 : 1);
    }

}
