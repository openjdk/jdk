/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6796710 7124242
 * @summary Html content in JEditorPane is overlapping on swing components while resizing the application.
 * @library ../../../regtesthelpers
 * @build Util
 * @author Pavel Porvatov
   @run main bug6796710
 */

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import sun.awt.SunToolkit;

public class bug6796710 {
    // The page is inlined because we want to be sure that the JEditorPane filled synchronously
    public static final String TEXT = "<html>" +
            "<body>" +
            "<table cellpadding=\"0\" cellspacing=\"0\" border=\"1\">" +
            "    <tbody>" +
            "        <tr>" +
            "            <td>Col1</td>" +
            "            <td>Col2</td>" +
            "            <td>Col3</td>" +
            "        </tr>" +
            "        <tr>" +
            "            <td>1. It's a regression from CR 4419748. The problem is in the CSSBorder#paintBorder, which ignores clip area while painting.</td>" +
            "            <td>2. It's a regression from CR 4419748. The problem is in the CSSBorder#paintBorder, which ignores clip area while painting.</td>" +
            "            <td>3. It's a regression from CR 4419748. The problem is in the CSSBorder#paintBorder, which ignores clip area while painting.</td>" +
            "        </tr>" +
            "    </tbody>" +
            "</table>" +
            "</body>" +
            "</html>";

    private static Robot robot;

    private static JFrame frame;

    private static JPanel pnBottom;

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame = new JFrame();

                frame.setUndecorated(true);

                pnBottom = new JPanel();
                pnBottom.add(new JLabel("Some label"));
                pnBottom.add(new JButton("A button"));

                JEditorPane editorPane = new JEditorPane();

                editorPane.setContentType("text/html");
                editorPane.setText(TEXT);
                editorPane.setEditable(false);

                JPanel pnContent = new JPanel(new BorderLayout());

                pnContent.add(new JScrollPane(editorPane), BorderLayout.CENTER);
                pnContent.add(pnBottom, BorderLayout.SOUTH);

                frame.setContentPane(pnContent);
                frame.setSize(400, 600);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });

        ((SunToolkit) SunToolkit.getDefaultToolkit()).realSync();

        // This delay should be added for MacOSX, realSync is not enough
        Thread.sleep(1000);

        BufferedImage bufferedImage = getPnBottomImage();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame.setSize(400, 150);
            }
        });

        ((SunToolkit) SunToolkit.getDefaultToolkit()).realSync();

        // On Linux platforms realSync doesn't guaranties setSize completion
        Thread.sleep(1000);

        if (!Util.compareBufferedImages(bufferedImage, getPnBottomImage())) {
            throw new RuntimeException("The test failed");
        }

        System.out.println("The test bug6796710 passed.");
    }

    private static BufferedImage getPnBottomImage() {
        Rectangle rect = pnBottom.getBounds();

        Util.convertRectToScreen(rect, pnBottom.getParent());

        return robot.createScreenCapture(rect);
    }
}
