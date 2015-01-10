/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6827032
 * @summary Color chooser with drag enabled shouldn't throw NPE
 * @author Peter Zhelezniakov
 * @library ../regtesthelpers
 */

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;


public class Test6827032 {

    private static volatile Point point;
    private static JColorChooser cc;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(new NimbusLookAndFeel());

        Robot robot = new Robot();
        robot.setAutoDelay(50);


        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });

        robot.waitForIdle();

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Component previewPanel = Util.findSubComponent(cc, "javax.swing.colorchooser.DefaultPreviewPanel");
                point = previewPanel.getLocationOnScreen();
            }
        });

        point.translate(5, 5);

        robot.mouseMove(point.x, point.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
    }


    private static void createAndShowGUI() {
        JFrame frame = new JFrame(Test6827032.class.getName());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        cc = new JColorChooser();
        cc.setDragEnabled(true);
        frame.add(cc);
        frame.pack();
        frame.setVisible(true);
    }
}
