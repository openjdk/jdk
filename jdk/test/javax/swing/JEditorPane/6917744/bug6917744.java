/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

/* @test
 * @bug 6917744
 * @summary JScrollPane Page Up/Down keys do not handle correctly html tables with different cells contents
 * @author Pavel Porvatov
 * @run main bug6917744
 */

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import javax.swing.*;

import sun.awt.SunToolkit;

public class bug6917744 {
    private static JFrame frame;

    private static JEditorPane editorPane;

    private static JScrollPane scrollPane;

    private static Robot robot;

    public static void main(String[] args) throws Exception {
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();

        robot = new Robot();
        robot.setAutoDelay(100);

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                frame = new JFrame();

                editorPane = new JEditorPane();

                try {
                    editorPane.setPage(bug6917744.class.getResource("/test.html"));
                } catch (IOException e) {
                    throw new RuntimeException("HTML resource not found", e);
                }

                scrollPane = new JScrollPane(editorPane);

                frame.getContentPane().add(scrollPane);
                frame.setSize(400, 300);
                frame.setVisible(true);
            }
        });

        toolkit.realSync();

        for (int i = 0; i < 50; i++) {
            robot.keyPress(KeyEvent.VK_PAGE_DOWN);
        }

        toolkit.realSync();

        // Check that we at the end of document
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                BoundedRangeModel model = scrollPane.getVerticalScrollBar().getModel();

                if (model.getValue() + model.getExtent() != model.getMaximum()) {
                    throw new RuntimeException("Invalid HTML position");
                }
            }
        });

        toolkit.realSync();

        for (int i = 0; i < 50; i++) {
            robot.keyPress(KeyEvent.VK_PAGE_UP);
        }

        toolkit.realSync();

        // Check that we at the begin of document
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                BoundedRangeModel model = scrollPane.getVerticalScrollBar().getModel();

                if (model.getValue() != model.getMinimum()) {
                    throw new RuntimeException("Invalid HTML position");
                }

                frame.dispose();
            }
        });
    }
}
