/*
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyright (c) 2012 IBM Corporation
 */

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import sun.awt.SunToolkit;

import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

/* @test 1.1 2012/04/12
 * @bug 7154030
 * @summary Swing components fail to hide after calling hide()
 * @author Jonathan Lu
 * @library ../../regtesthelpers/
 * @build Util
 * @run main bug7154030
 */

public class bug7154030 {

    private static JButton button = null;

    public static void main(String[] args) throws Exception {
        BufferedImage imageInit = null;

        BufferedImage imageShow = null;

        BufferedImage imageHide = null;

        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();

        Robot robot = new Robot();

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                JDesktopPane desktop = new JDesktopPane();
                button = new JButton("button");
                JFrame frame = new JFrame();

                button.setSize(200, 200);
                button.setLocation(100, 100);
                button.setForeground(Color.RED);
                button.setBackground(Color.RED);
                button.setOpaque(true);
                button.setVisible(false);
                desktop.add(button);

                frame.setContentPane(desktop);
                frame.setSize(300, 300);
                frame.setLocation(0, 0);
                frame.setVisible(true);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            }
        });

        toolkit.realSync();
        imageInit = robot.createScreenCapture(new Rectangle(0, 0, 300, 300));

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                button.show();
            }
        });

        toolkit.realSync();
        imageShow = robot.createScreenCapture(new Rectangle(0, 0, 300, 300));
        if (Util.compareBufferedImages(imageInit, imageShow)) {
            throw new Exception("Failed to show opaque button");
        }

        toolkit.realSync();

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                button.hide();
            }
        });

        toolkit.realSync();
        imageHide = robot.createScreenCapture(new Rectangle(0, 0, 300, 300));

        if (!Util.compareBufferedImages(imageInit, imageHide)) {
            throw new Exception("Failed to hide opaque button");
        }

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                button.setOpaque(false);
                button.setBackground(new Color(128, 128, 0));
                button.setVisible(false);
            }
        });

        toolkit.realSync();
        imageInit = robot.createScreenCapture(new Rectangle(0, 0, 300, 300));

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                button.show();
            }
        });

        toolkit.realSync();
        imageShow = robot.createScreenCapture(new Rectangle(0, 0, 300, 300));

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                button.hide();
            }
        });

        if (Util.compareBufferedImages(imageInit, imageShow)) {
            throw new Exception("Failed to show non-opaque button");
        }

        toolkit.realSync();
        imageHide = robot.createScreenCapture(new Rectangle(0, 0, 300, 300));

        if (!Util.compareBufferedImages(imageInit, imageHide)) {
            throw new Exception("Failed to hide non-opaque button");
        }
    }
}
