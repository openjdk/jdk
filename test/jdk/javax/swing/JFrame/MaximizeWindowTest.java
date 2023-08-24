/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Robot;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @key headful
 * @summary setExtendedFrame not executed immediately
 * @run main MaximizeWindowTest
 */
@SuppressWarnings("serial")
public class MaximizeWindowTest extends JFrame {
    private static JFrame frame;
    private static final Dimension ORIGINAL_SIZE = new Dimension(200, 200);

    public static void main(String[] arguments) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JSplitPane splitPane = new JSplitPane();

                frame = new JFrame();
                frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
                frame.setSize(ORIGINAL_SIZE);
                frame.setLocation(400, 400);
                frame.add(splitPane);
                frame.setExtendedState(MAXIMIZED_BOTH);

                frame.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        System.out.println("Component size: " + e.getComponent().getSize());
                        if (e.getComponent().getSize().equals(ORIGINAL_SIZE)) {
                            throw new RuntimeException("Test Failed! " +
                                    "Frame was visible at original size before maximizing");
                        }
                    }
                });

                splitPane.setDividerLocation(1000);
                frame.setVisible(true);
            });

            robot.delay(1000);

        } finally {
            if (frame != null) {
                SwingUtilities.invokeAndWait(() -> frame.dispose());
            }
        }
    }
}
