/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 6425089
 * @summary PIT. Frame does not show a big size jpg image as icon
 * @requires (os.family != "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetLargeIconTest
 */

public class SetLargeIconTest {
    private static final String INSTRUCTIONS = """
            Case 1: Press "Pass" button if this frame does not have icon with green color.

            Case 2: Press "Change to red" if the frame icon is in green color.
            For case 2, press "Pass" button if green icon changes to a larger red icon,
            press "Fail" otherwise.
            """;
    private static JFrame frame;

    private static void createAndShowGUI() {
        frame = new JFrame();

        setColoredIcon(Color.green, 128, 128);
        JButton btnChangeIcon = new JButton("Change to red");
        btnChangeIcon.addActionListener(e -> setColoredIcon(Color.red, 400, 400));

        frame.add(btnChangeIcon, BorderLayout.CENTER);
        frame.setSize(200,65);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame,
                PassFailJFrame.Position.HORIZONTAL);
        frame.setVisible(true);
    }

    private static void setColoredIcon(Color color, int width, int height) {
        BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics gr = image.createGraphics();
        gr.setColor(color);
        gr.fillRect(0, 0, width, height);

        ArrayList<Image> imageList = new java.util.ArrayList<>();
        imageList.add(image);

        frame.setIconImages(imageList);
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame("Large Icon " +
                "Test Instructions", INSTRUCTIONS, 5, 8, 50);
        SwingUtilities.invokeAndWait(SetLargeIconTest::createAndShowGUI);
        passFailJFrame.awaitAndCheck();
    }
}
