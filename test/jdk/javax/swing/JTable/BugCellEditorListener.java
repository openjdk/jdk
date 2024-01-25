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
/*
 * @test
 * @bug 6788481
 *  @key headful
 * @summary  Verifies if CellEditorListener.editingCanceled is called
 * @run main BugCellEditorListener
 */

import java.awt.Robot;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.DefaultCellEditor;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;

public class BugCellEditorListener {
    static Robot robot;
    static JFrame frame;
    static JTable table;
    static volatile Point pt;
    static volatile boolean cancelled;

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

    public static void main(String [] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing l&f : " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            test();
        }
    }

    private static void test() throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                table = new JTable(5, 5);
                table.setPreferredScrollableViewportSize(table.getPreferredSize());
                JScrollPane scrollpane = new JScrollPane(table);
                frame.getContentPane().add(scrollpane);

                DefaultCellEditor dce = (DefaultCellEditor) table.getDefaultEditor(Object.class);
                dce.addCellEditorListener(new CellEditorListener() {
                    public void editingStopped(ChangeEvent e) {
                        System.out.println("stopped");
                    }

                    public void editingCanceled(ChangeEvent e) {
                        System.out.println("canceled");
                        cancelled = true;
                    }
                });


                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                Rectangle rect = table.getCellRect(2, 0, false);
                pt = new Point(rect.x + rect.width / 2, rect.y + rect.height / 2);
                SwingUtilities.convertPointToScreen(pt, table);
            });
            robot.mouseMove(pt.x, pt.y);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
            robot.delay(1000);
            if (!cancelled) {
                throw new RuntimeException("Cell editing cancel listener not called");
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
