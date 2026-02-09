/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6436437
 * @requires (os.family == "windows")
 * @summary Test setIconImages() for client-decorated JFrame
 * @library ../../regtesthelpers
 * @run main/manual ClientDecoratedIconTest
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextArea;

public class ClientDecoratedIconTest extends SwingTestHelper implements ActionListener {
    JButton passed;
    JButton failed;

    java.util.List<Image> icons1;
    java.util.List<Image> icons2;
    IconFrame frame1;
    IconFrame frame2;

    Object lock = new Object();
    boolean done = false;

    protected String getInstructions() {
        StringBuilder instructionsStr = new StringBuilder();
        instructionsStr.append("This tests the functionality of the setIconImages() API\n");
        instructionsStr.append("You will see two JFrames with custom icons1.  Both JFrames should have the same icon: a colored box.\n");
        instructionsStr.append("If either of the JFrames has the default, coffe-cup icon, the test fails.\n");
        instructionsStr.append("If the JFrames DO NOT both have the same colored box as their icon, the test fails.\n");
        instructionsStr.append("If both JFrames DO have the same colored box as their icon, then the test passes.");
        return instructionsStr.toString();
    }

    protected Component createContentPane() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        JTextArea instructions = new JTextArea(getInstructions());
        panel.add(instructions, BorderLayout.CENTER);

        passed = new JButton("Icons match (PASS)");
        passed.addActionListener(this);
        failed = new JButton("Icons don't match (FAIL)");
        failed.addActionListener(this);
        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new FlowLayout());
        btnPanel.add(passed);
        btnPanel.add(failed);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    public void onEDT10() throws IOException {
        Image img1 = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img1.getGraphics();
        g.setColor(Color.green);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        Image img2 = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        g = img2.getGraphics();
        g.setColor(Color.magenta);
        g.fillRect(0, 0, 24, 24);
        g.dispose();
        Image img3 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        g = img3.getGraphics();
        g.setColor(Color.red);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        Image img4 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        g = img4.getGraphics();
        g.setColor(Color.blue);
        g.fillRect(0, 0, 64, 64);
        g.dispose();

        icons1 = new ArrayList(4);
        icons1.add(img1);
        icons1.add(img2);
        icons1.add(img3);
        icons1.add(img4);

        icons2 = new ArrayList(4);
        icons2.add(img4);
        icons2.add(img3);
        icons2.add(img2);
        icons2.add(img1);

        frame1 = new IconFrame(icons1);
        frame2 = new IconFrame(icons2);

        frame1.setLocation(50, 250);
        frame2.setLocation(275, 250);

        frame1.setVisible(true);
        frame2.setVisible(true);
    }

    public void onEDT20() {
        waitForCondition(new Runnable() {
            public void run() {
                while (true) {
                    synchronized(lock) {
                        if (done) {
                            return;
                        }
                    }
                    try {
                        Thread.sleep(250);
                    }
                    catch(InterruptedException e) {}
                }
            }
        });
        System.out.println("done waiting");
    }

    public void onEDT30() {
        // Needed so waitForCondition() has something to wait for :)
    }

    public void actionPerformed(ActionEvent e) {
        System.out.println("actionPerformed()");
        if (e.getSource() == passed) {
            synchronized(lock) {
                done = true;
            }
        }
        if (e.getSource() == failed) {
            throw new RuntimeException("Test Failed");
        }
    }

    class IconFrame extends JFrame {
        public IconFrame(java.util.List<Image> icons) {
            super("Custom Icon Frame");
            setUndecorated(true);
            getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
            setIconImages(icons);
            setSize(200, 200);
        }
    }

    public static void main(String[] args) throws Throwable {
        new ClientDecoratedIconTest().run(args);
        System.out.println("end of main()");
    }
}
