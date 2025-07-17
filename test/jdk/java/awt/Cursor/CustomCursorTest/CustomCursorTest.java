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
 * @bug 4174035 4106384 4205805
 * @summary Test for functionality of Custom Cursor
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CustomCursorTest
 */

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class CustomCursorTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test is for switching between a custom cursor and the
                system cursor.

                1. Click on the test window panel to change from the default
                    system cursor to the custom red square cursor
                2. Verify that the square cursor shows when the panel is clicked
                3. Verify that the square cursor is colored red
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(CustomCursorTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        JFrame f = new JFrame("Custom Cursor Test");
        CustomCursorPanel c = null;
        try {
            c = new CustomCursorPanel();
        } catch (IOException e) {
            e.printStackTrace();
        }

        f.setIconImage(c.getImage());
        f.getContentPane().add(c);
        f.setSize(400, 400);
        return f;
    }
}

class CustomCursorPanel extends Panel {
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    Image image;
    Cursor cursor;
    boolean flip = false;

    public CustomCursorPanel() throws IOException {
        generateRedSquareCursor();

        image = toolkit.getImage(System.getProperty("test.classes", ".")
                + java.io.File.separator + "square_cursor.gif");

        setBackground(Color.green);
        cursor = toolkit.createCustomCursor(image, new Point(0, 0), "custom");

        JLabel c = (JLabel) add(new JLabel("click to switch between " +
                "red square and default cursors"));
        c.setBackground(Color.white);
        c.setForeground(Color.red);

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (!flip) {
                    setCursor(cursor);
                    flip = true;
                } else {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    flip = false;
                }
            }
        });
    }

    public Image getImage() {
        return image;
    }

    private static void generateRedSquareCursor() throws IOException {
        Path p = Path.of(System.getProperty("test.classes", "."));
        BufferedImage bImg = new BufferedImage(35, 34, TYPE_INT_ARGB);
        Graphics2D cg = bImg.createGraphics();
        cg.setColor(Color.RED);
        cg.fillRect(0, 0, 35, 34);
        ImageIO.write(bImg, "png", new File(p + java.io.File.separator +
                "square_cursor.gif"));
    }
}
