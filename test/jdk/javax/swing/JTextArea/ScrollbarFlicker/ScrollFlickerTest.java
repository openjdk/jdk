/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 8160246
 * @summary Regression: 4410243 reproducible with GTK LaF
 * @run main ScrollFlickerTest
 */

import javax.swing.*;
import java.awt.*;

public class ScrollFlickerTest {

    private static JFrame frame;
    private static JScrollPane scroll;
    private static int cnt = 0;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame();
            frame.setSize(300, 200);
            frame.getContentPane().setLayout(null);
            JTextArea text = new JTextArea("Test test test test");
            text.setLineWrap(true);
            scroll = new JScrollPane(text);
            frame.getContentPane().add(scroll);
            scroll.setBounds(1, 1, 100, 50);
            frame.setVisible(true);
        });

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(200);

        SwingUtilities.invokeAndWait(() -> {
            Insets insets = scroll.getInsets();
            scroll.setSize(insets.left + insets.right +
                    scroll.getVerticalScrollBar().getPreferredSize().width, 50);
            scroll.revalidate();
        });
        robot.delay(200);
        SwingUtilities.invokeAndWait(() ->
                          scroll.getViewport().addChangeListener((e) -> cnt++));
        robot.delay(1000);

        SwingUtilities.invokeLater(frame::dispose);

        if (cnt > 0) {
            throw new RuntimeException("Scroll bar flickers");
        }
    }
}
