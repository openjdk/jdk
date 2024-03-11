/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4773378
 * @summary BasicDesktopPaneUI.desktopManager nulled after JDesktopPane.updateUI()
 * @key headful
 * @run main bug4773378
 */

import java.awt.BorderLayout;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.DefaultDesktopManager;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

public class bug4773378 {
    JFrame frame;

    JDesktopPane desktop;
    DefaultDesktopManager desktopManager;
    JInternalFrame jif;

    Robot robot;
    volatile boolean  keyTyped = false;

    public void setupGUI() {
        frame = new JFrame("bug4773378");
        frame.setLayout(new BorderLayout());
        desktop = new JDesktopPane();
        frame.add(desktop, BorderLayout.CENTER);

        jif = new JInternalFrame("jif", true, false, true, true);
        jif.setSize(150, 100);
        jif.setVisible(true);
        desktop.add(jif);

        jif.addInternalFrameListener(new InternalFrameAdapter() {
                public void internalFrameActivated(InternalFrameEvent e) {
                    synchronized (bug4773378.this) {
                        keyTyped = true;
                        bug4773378.this.notifyAll();
                    }
                }
            });

        desktopManager = new MyDesktopManager();
        desktop.setDesktopManager(desktopManager);
        desktop.updateUI();

        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        jif.requestFocus();
    }

    public void performTest() {
        try {
            jif.setSelected(true);

            synchronized (this) {
                while (!keyTyped) {
                    bug4773378.this.wait();
                }
            }

            robot = new Robot();
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_F6);
            robot.keyRelease(KeyEvent.VK_F6);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            Thread.sleep(2000);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void cleanupGUI() {
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    class MyDesktopManager extends DefaultDesktopManager {
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        bug4773378 b = new bug4773378();
        SwingUtilities.invokeAndWait(b::setupGUI);
        b.performTest();
        SwingUtilities.invokeAndWait(b::cleanupGUI);
    }

}
