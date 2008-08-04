/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6728834
 * @summary Tests that LCD AA text rendering works properly with destinations
 * being VolatileImage of all transparency types
 * @author Dmitri.Trembovetski: area=Graphics
 * @run main/manual/othervm NonOpaqueDestLCDAATest
 * @run main/manual/othervm -Dsun.java2d.opengl=True NonOpaqueDestLCDAATest
 */

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.VolatileImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import static java.awt.Transparency.*;

public class NonOpaqueDestLCDAATest extends JFrame implements ActionListener {
    private static volatile boolean passed = true;
    private static CountDownLatch complete = new CountDownLatch(1);

    public NonOpaqueDestLCDAATest() {
        JTextArea desc = new JTextArea();
        desc.setText(
            "\n  Instructions: the three text strings below should appear\n" +
            "  readable, without smudges or misshapen bold glyphs.\n\n" +
            "  If they look fine the test PASSED otherwise it FAILED.\n");
        desc.setEditable(false);
        desc.setBackground(Color.black);
        desc.setForeground(Color.green);
        add("North", desc);
        JPanel renderPanel = new JPanel() {
            public void paintComponent(Graphics g) {
                render(g, getWidth(), getHeight());
            }
        };
        renderPanel.setPreferredSize(new Dimension(350, 150));
        renderPanel.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                images = null;
            }
        });
        add("Center", renderPanel);

        JButton passed = new JButton("Passed");
        JButton failed = new JButton("Failed");
        passed.addActionListener(this);
        failed.addActionListener(this);
        JPanel p = new JPanel();
        p.add(passed);
        p.add(failed);
        add("South", p);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                complete.countDown();
            }
        });
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void render(Graphics g, int w, int h) {
        initImages(w, h);

        Graphics2D g2d = (Graphics2D) g.create();
        for (VolatileImage vi : images) {
            g2d.drawImage(vi, 0, 0, null);
            g2d.translate(0, vi.getHeight());
        }
    }

    String tr[] = { "OPAQUE", "BITMASK", "TRANSLUCENT" };
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Passed")) {
            passed = true;
            System.out.println("Test Passed");
        } else if (e.getActionCommand().equals("Failed")) {
            System.out.println("Test Failed");
            for (int i = 0; i < images.length; i++) {
                String f = "NonOpaqueDestLCDAATest_"+tr[i]+".png";
                try {
                    ImageIO.write(images[i].getSnapshot(), "png", new File(f));
                    System.out.printf("Dumped %s image to %s\n", tr[i], f);
                } catch (Throwable t) {}
            }
            passed = false;
        }
        dispose();
        complete.countDown();
    }

    static void clear(Graphics2D  g, int w, int h) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setColor(new Color(0, 0, 0, 0));
        gg.setComposite(AlphaComposite.Src);
        gg.fillRect(0, 0, w, h);
    }

    VolatileImage images[];
    private void initImages(int w, int h) {
        if (images == null) {
            images = new VolatileImage[3];
            GraphicsConfiguration gc = getGraphicsConfiguration();
            for (int i = OPAQUE; i <= TRANSLUCENT; i++) {
                VolatileImage vi =
                    gc.createCompatibleVolatileImage(w,h/3,i);
                images[i-1] = vi;
                vi.validate(gc);
                Graphics2D g2d = (Graphics2D) vi.getGraphics();
                if (i > OPAQUE) {
                    clear(g2d, vi.getWidth(), vi.getHeight());
                }
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                String s = "LCD AA Text rendered to "+tr[i-1]+ " destination";
                g2d.drawString(s, 10, vi.getHeight()/2);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                NonOpaqueDestLCDAATest t = new NonOpaqueDestLCDAATest();
                t.pack();
                t.setVisible(true);
            }
        });

        complete.await();
        if (!passed) {
            throw new RuntimeException("Test Failed!");
        }
    }
}
