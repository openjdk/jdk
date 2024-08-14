/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;

import javax.swing.JButton;

import jdk.test.lib.Platform;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8142861 8143062 8147016
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @requires (os.family == "windows" | os.family == "mac")
 * @summary Check if multiresolution image behaves properly
 *         on HiDPI + non-HiDPI display pair.
 * @run main/manual MultiDisplayTest
 */

public class MultiDisplayTest {
    private static final String INSTRUCTIONS =
            """
             The test requires two-display configuration, where

             - 1st display is operating in HiDPI mode;
             - 2nd display is non-HiDPI.

             In other cases please simply push "Pass".

             To run test please push "Start".

             Then drag parent / child to different displays and check
             that the proper image is shown for every window
             (must be "black 1x" for non-HiDPI and "blue 2x" for HiDPI).

             Please try to drag both parent and child,
             do it fast several times and check if no artefacts occur.

             Try to switch display resolution (high to low and back).

             For Mac OS X please check also the behavior for
             translucent windows appearing on the 2nd (non-active) display
             and Mission Control behavior.

             Close the Child & Parent windows.

             In case if no issues occur please push "Pass", otherwise "Fail".
            """;

    private static final int W = 200;
    private static final int H = 200;

    private static final BaseMultiResolutionImage IMG =
        new BaseMultiResolutionImage(new BufferedImage[]{
        generateImage(1, Color.BLACK), generateImage(2, Color.BLUE)});

    public static void main(String[] args) throws Exception {
        if (!checkOS()) {
            throw new SkippedException("Invalid OS." +
                    "Please run test on either Windows or MacOS");
        }
        PassFailJFrame
                .builder()
                .title("MultiDisplayTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .splitUIBottom(MultiDisplayTest::createAndShowGUI)
                .build()
                .awaitAndCheck();
    }

    public static JButton createAndShowGUI() {
        JButton b = new JButton("Start");
        b.addActionListener(e -> {
            ParentFrame p = new ParentFrame();
            new ChildDialog(p);
        });
        return b;
    }

    private static boolean checkOS() {
        return Platform.isWindows() || Platform.isOSX();
    }

    private static BufferedImage generateImage(int scale, Color c) {
        BufferedImage image = new BufferedImage(
            scale * W, scale * H, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        g.setColor(c);
        g.fillRect(0, 0, scale * W, scale * H);

        g.setColor(Color.WHITE);
        Font f = g.getFont();
        g.setFont(new Font(f.getName(), Font.BOLD, scale * 48));
        g.drawChars((scale + "X").toCharArray(), 0, 2,
                scale * W / 2, scale * H / 2);
        return image;
    }

    private static class ParentFrame extends Frame {
        public ParentFrame() {
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) { dispose(); }
            });
            setSize(W, H);
            setLocation(50, 50);
            setTitle("parent");
            setResizable(false);
            setVisible(true);
        }

        @Override
        public void paint(Graphics gr) {
            gr.drawImage(IMG, 0, 0, this);
        }
    }

    private static class ChildDialog extends Dialog {
        public ChildDialog(Frame f) {
            super(f);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) { dispose(); }
            });
            setSize(W, H);
            setTitle("child");
            setResizable(false);
            setModal(true);
            setVisible(true);
        }

        @Override
        public void paint(Graphics gr) {
            gr.drawImage(IMG, 0, 0, this);
        }
    }
}
