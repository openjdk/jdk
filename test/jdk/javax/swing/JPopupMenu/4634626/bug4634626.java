/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4634626
 * @summary Implement context popup menus for components
 * @library /lib/client
 * @build ExtendedRobot
 * @run main bug4634626
 */

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public class bug4634626 {

    public volatile boolean passed = true;
    public volatile boolean done = false;

    public static JFrame mainFrame;
    public JPanel contentPane;
    public JButton nopButton;
    public JTextArea someText;
    public JButton popButton;

    public JPopupMenu btnPopup;
    public JPopupMenu commonPopup;
    static public Error toBeThrown = null;
    static int popTrig = MouseEvent.BUTTON3_MASK;
    static boolean popt = false;

    public static class MouseWatcher extends MouseAdapter {
        public void mousePressed(MouseEvent e) {
            if(e.isPopupTrigger()) popt = true;
            if(e.getComponent() != null &&
               e.getComponent() instanceof JComponent &&
               e.isPopupTrigger() &&
               ((JComponent)e.getComponent()).getComponentPopupMenu() != null) {
                toBeThrown =
                  new Error("The event got through the component with popup: "
                  + e);
            }
        }

        public void mouseReleased(MouseEvent e) {
            if(e.isPopupTrigger()) popt = true;
            if(e.getComponent() != null &&
               e.getComponent() instanceof JComponent &&
               e.isPopupTrigger() &&
               ((JComponent)e.getComponent()).getComponentPopupMenu() != null) {
                toBeThrown =
                  new Error("The event got through the component with popup: "
                  + e);
            }
            if(toBeThrown != null) {
                throw(toBeThrown);
            }
        }
    }

    public static MouseWatcher mouser = new MouseWatcher();

    public static void main(final String[] args) throws Exception {
        try {
            bug4634626 app = new bug4634626();
            app.init();
            app.destroy();
        } finally {
            if (mainFrame != null) SwingUtilities.invokeAndWait(() -> mainFrame.dispose());
        }
    }

    public void init() throws Exception {
        SwingUtilities.invokeLater(() -> {
            mainFrame = new JFrame("Bug4634626");
            contentPane = new JPanel();
            nopButton = new JButton("No popup button");
            someText = new JTextArea("Some text here", 20, 10);
            popButton = new JButton("Button with the popup");

            btnPopup = new JPopupMenu();
            commonPopup = new JPopupMenu();

            try {
                popButton.setComponentPopupMenu(null);
                popButton.setComponentPopupMenu(null);
                popButton.setComponentPopupMenu(btnPopup);
                popButton.setComponentPopupMenu(null);
            } catch (Exception ex) {
                System.err.println("Unexpected exception was thrown by " +
                        "setComponentPopupMenu() method: " + ex);
            }
            btnPopup.add("Button 1");
            btnPopup.add("Button 2");
            btnPopup.add("Button 3");
            popButton.setComponentPopupMenu(btnPopup);
            popButton.addMouseListener(mouser);
            commonPopup.add("One");
            commonPopup.add("Two");
            commonPopup.add("Three");

            contentPane.setLayout(new BorderLayout());
            contentPane.setComponentPopupMenu(commonPopup);
            contentPane.addMouseListener(mouser);
            contentPane.add(nopButton, BorderLayout.NORTH);
            nopButton.addMouseListener(mouser);
            contentPane.add(popButton, BorderLayout.SOUTH);
            someText.addMouseListener(mouser);
            contentPane.add(someText, BorderLayout.CENTER);
            mainFrame.setContentPane(contentPane);

            mainFrame.pack();
            mainFrame.setLocation(50, 50);
            mainFrame.addWindowListener(new TestStateListener());
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
        });

        while(!done) Thread.yield();

        if(!passed) {
            throw new RuntimeException("Test failed");
        }
    }

    public class TestStateListener extends WindowAdapter {
        public void windowOpened(WindowEvent ev) {
            try {
                ev.getWindow().toFront();
                ev.getWindow().requestFocus();
                new Thread(new RobotThread()).start();
            } catch (Exception ex) {
                throw new RuntimeException("Thread Exception");
            }
        }
    }

    class RobotThread implements Runnable {
        public void run() {
            ExtendedRobot robo;
            try {
                robo = new ExtendedRobot();
            }catch(Exception ex) {
                ex.printStackTrace();
                throw new RuntimeException("Cannot create Robot");
            }
            robo.setAutoDelay(100);
            robo.waitForIdle();

            // Determine working popup trigger event
            clickMouseOn(robo, nopButton, popTrig);
            robo.waitForIdle();
            robo.delay(1000);
            if(!popt) popTrig = MouseEvent.BUTTON2_MASK;

            // Inheritance is OFF by default. Popup should not appear.
            clickMouseOn(robo, someText, popTrig);

            // Set inheritance ON watch for popup.
            try {
                SwingUtilities.invokeAndWait(() -> someText.setInheritsPopupMenu(true));
            } catch (Exception ex) {
                throw new RuntimeException("Can not assign popup to button", ex);
            }
            clickMouseOn(robo, someText, popTrig);
            robo.waitForIdle();
            robo.delay(1000);
            AtomicBoolean popupVisible = new AtomicBoolean(false);
            try {
                SwingUtilities.invokeAndWait(() ->
                        popupVisible.set(commonPopup.isVisible()));
            } catch (Exception ex) {
                throw new RuntimeException("Can not get commonPopup status");
            }
            if(!popupVisible.get()) {
                toBeThrown = new Error("Popup should be visible");
                passed = false;
            }
            // Dispose popup.
            robo.type(KeyEvent.VK_ESCAPE);
            robo.waitForIdle();
            try {
                SwingUtilities.invokeAndWait(() -> someText.setInheritsPopupMenu(false));
            } catch (Exception ex) {
                throw new RuntimeException("Can not unassign popup on button", ex);
            }

            // Button with popup assigned. Wathch for popup.
            clickMouseOn(robo, popButton, popTrig);
            robo.waitForIdle();
            robo.delay(1000);
            try {
                SwingUtilities.invokeAndWait(() ->
                    popupVisible.set(btnPopup.isVisible()));
            } catch (Exception ex) {
                throw new RuntimeException("Can not get btnPopup status");
            }
            if(!popupVisible.get()) {
                toBeThrown = new Error("Popup should be visible");
                passed = false;
            }
            // Dispose popup.
            robo.type(KeyEvent.VK_ESCAPE);
            // Test finished.
            done = true;
        }
    }

    public void destroy() {
        if(!passed) {
            throw(toBeThrown);
        }
    }

    private void clickMouseOn(ExtendedRobot robot, Component c, int button) {
        java.awt.Point p = c.getLocationOnScreen();
        java.awt.Dimension size = c.getSize();
        p.x += size.width / 2;
        p.y += size.height / 2;
        robot.mouseMove(p.x, p.y);
        robot.delay(100);
        robot.click(button);
    }
}
