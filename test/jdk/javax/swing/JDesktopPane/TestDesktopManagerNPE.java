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

/* @test
 * @bug 8170794
 * @key headful
 * @summary Verifies iconifying internalframe after setting DesktopManager
 *          does not return NPE
 *  @run main TestDesktopManagerNPE
 */

import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import javax.swing.DefaultDesktopManager;
import javax.swing.JInternalFrame;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JMenuBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class TestDesktopManagerNPE {

    static JDesktopPane desktop;
    static JInternalFrame internalFrame;
    static JFrame frame;

    //Create a new internal frame.
    protected static void createFrame() {
        internalFrame = new JInternalFrame();
        internalFrame.setSize(300, 300);
        internalFrame.setLocation(30, 30);
        internalFrame.setVisible(true); //necessary as of 1.3
        desktop.add(internalFrame);
        try {
            internalFrame.setSelected(true);
        } catch (java.beans.PropertyVetoException e) {}
    }

    /**
     * Create the GUI and show it. For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowGUI() {
        JFrame.setDefaultLookAndFeelDecorated(true);

        int inset = 50;
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame = new JFrame("Test");
        frame.setBounds(inset, inset,
                  screenSize.width - inset*2,
                  screenSize.height - inset*2);

        //Set up the GUI.
        desktop = new JDesktopPane(); //a specialized layered pane

        // If you add this line, iconification will fail
        desktop.setDesktopManager(new DefaultDesktopManager());

        createFrame(); //create first "window"
        frame.setContentPane(desktop);
        //Display the window.
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> createAndShowGUI());
            Robot robot = new Robot();
            robot.delay(1000);
            internalFrame.setIcon(true);
            robot.delay(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
