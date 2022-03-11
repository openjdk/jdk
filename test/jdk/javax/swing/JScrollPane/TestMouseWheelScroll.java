/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.DefaultListModel;
import javax.swing.ListModel;
import javax.swing.JScrollPane;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @key headful
 * @requires (os.family != "mac")
 * @bug  6911375
 * @summary Verifies mouseWheel effect on JList without scrollBar
 */
public class TestMouseWheelScroll {

    static JFrame frame;
    static JScrollPane scrollPane;
    static volatile Point p;
    static volatile int width;
    static volatile int height;
    static volatile Point viewPosition;
    static volatile Point newPosition;

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {

        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F: " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            Robot robot = new Robot();
            robot.setAutoDelay(100);

            try {
                SwingUtilities.invokeAndWait(() -> {
                    frame = new JFrame();
                    JList list = new JList(createListModel());
                    // disable list bindings
                    list.getInputMap().getParent().clear();
                    scrollPane = new JScrollPane(list);

                    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
                    frame.add(scrollPane);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    frame.setSize(200,200);
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                });
                robot.waitForIdle();
                robot.delay(1000);
                SwingUtilities.invokeAndWait(() -> {
                    p = frame.getLocationOnScreen();
                    width = frame.getWidth();
                    height = frame.getHeight();
                });
                robot.mouseMove(p.x + width / 2, p.y + height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle();
                SwingUtilities.invokeAndWait(() -> {
                    viewPosition = scrollPane.getViewport().getViewPosition();
                });
                robot.delay(1000);
                robot.mouseWheel(1);
                robot.delay(500);
                SwingUtilities.invokeAndWait(() -> {
                    newPosition = scrollPane.getViewport().getViewPosition();
                });
                robot.delay(1000);
                if (newPosition.equals(viewPosition)) {
                    throw new RuntimeException("Mouse wheel not handled");
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (frame != null) {
                        frame.dispose();
                    }
                });
            }
        }
    }

    private static ListModel createListModel() {
        DefaultListModel model = new DefaultListModel();
        for (int i = 0; i < 100; i++) {
            model.addElement("element " + i);
        }
        return model;
    }
}
