/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4938801 8376169
 * @key headful
 * @summary Verifies popup is removed when the component is removed
 * @run main TestPopupInvoker
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Robot;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TestPopupInvoker {
    static JPopupMenu popupMenu;
    static JFrame frame;
    static JLabel label;
    static Container pane;
    static volatile Component invoker;

    private static final CountDownLatch popupShown = new CountDownLatch(1);
    private static final CountDownLatch popupHidden = new CountDownLatch(1);

    private static void createUI() {
        frame = new JFrame("TestPopupInvoker");
        pane = frame.getContentPane();
        pane.setLayout(new BorderLayout());
        label = new JLabel("Popup Invoker");
        pane.add(label, BorderLayout.CENTER);

        popupMenu = new JPopupMenu("Popup");
        popupMenu.add("One");
        popupMenu.add("Two");
        popupMenu.add("Three");

        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                popupShown.countDown();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                popupHidden.countDown();
                popupMenu.setInvoker(null);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(TestPopupInvoker::createUI);
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> popupMenu.show(label, 0, 0));

            if (!popupShown.await(2, SECONDS)) {
                throw new RuntimeException("Popup isn't displayed");
            }
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                pane.remove(label);
                pane.repaint();
            });
            if (!popupHidden.await(1, SECONDS)) {
                throw new RuntimeException("Popup is visible after component is removed");
            }

            SwingUtilities.invokeAndWait(() -> invoker = popupMenu.getInvoker());
            if (invoker != null) {
                throw new RuntimeException("Invoker is not null");
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
