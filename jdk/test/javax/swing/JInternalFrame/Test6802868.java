/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug 6802868
 * @summary JInternalFrame is not maximized when maximized parent frame
 * @author Alexander Potochkin
 */

import sun.awt.SunToolkit;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.beans.PropertyVetoException;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;

public class Test6802868 {
    static JInternalFrame jif;
    static JFrame frame;
    static Dimension size;
    static Point location;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(20);
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                frame = new JFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                JDesktopPane jdp = new JDesktopPane();
                frame.getContentPane().add(jdp);

                jif = new JInternalFrame("Title", true, true, true, true);
                jdp.add(jif);
                jif.setVisible(true);

                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                try {
                    jif.setMaximum(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        toolkit.realSync();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                size = jif.getSize();
                frame.setSize(300, 300);
            }
        });
        toolkit.realSync();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                if (jif.getSize().equals(size)) {
                    throw new RuntimeException("InternalFrame hasn't changed its size");
                }
                try {
                    jif.setIcon(true);
                } catch (PropertyVetoException e) {
                    e.printStackTrace();
                }
                location = jif.getDesktopIcon().getLocation();
                frame.setSize(400, 400);
            }
        });
        toolkit.realSync();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                if (jif.getDesktopIcon().getLocation().equals(location)) {
                    throw new RuntimeException("JDesktopIcon hasn't moved");
                }
            }
        });
    }
}
