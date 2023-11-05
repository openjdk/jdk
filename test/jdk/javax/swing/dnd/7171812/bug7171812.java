/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 7171812
 * @summary [macosx] Views keep scrolling back to the drag position after DnD
 * @run main bug7171812
 */

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class bug7171812 {
    static JFrame mainFrame;
    static String listData[];
    static JListWithScroll<String> list;
    static JScrollPane scrollPane;
    static volatile Point pt;
    static volatile int height;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception{

        Robot robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                setupGUI();
            }
        });

        robot.waitForIdle();
        robot.delay(1000);
        SwingUtilities.invokeAndWait(() -> {
            pt = scrollPane.getLocationOnScreen();
            height = scrollPane.getHeight();
        });
        robot.mouseMove(pt.x + 5, pt.y + 5);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for(int offset = 5; offset < height - 20; offset++) {
            robot.mouseMove(pt.x + 5, pt.y + offset);
        }
        for(int offset = 5; offset < 195; offset++) {
            robot.mouseMove(pt.x + offset, pt.y + height - 20);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    if(scrollPane.getViewport().getViewPosition().getY() < 30) {
                        throw new RuntimeException("Incorrect view position.");
                    };
                }
            });
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw new RuntimeException("Test failed, scroll on drag doesn't work!");
        }
    }

    public static void setupGUI() {
        listData = new String[100];
        for (int i=0; i<100; i++) {
            listData[i] = "Long Line With Item "+i;
        }
        mainFrame = new JFrame("Rest frame");
        mainFrame.setSize(300, 500);
        mainFrame.setLayout(new BorderLayout());
        list = new JListWithScroll(listData);
        list.setDragEnabled(true);
        list.setAutoscrolls(true);
        final DropTarget dropTarget = new DropTarget(list, DnDConstants.ACTION_MOVE, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dragOver(dtde);
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_MOVE);
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
            }
        }, true);
        scrollPane = new JScrollPane(list);
        mainFrame.add(scrollPane, BorderLayout.CENTER);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocation(100, 100);
        mainFrame.setVisible(true);
    }
}
