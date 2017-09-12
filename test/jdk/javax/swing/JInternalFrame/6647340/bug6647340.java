/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 6647340
 * @summary Checks that iconified internal frame follows
 *          the main frame borders properly.
 * @author Mikhail Lapshin
 * @library ../../../../lib/testlibrary/
 * @build ExtendedRobot
 * @run main bug6647340
 */

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyVetoException;

public class bug6647340 {
    private JFrame frame;
    private Point location;
    private JInternalFrame jif;
    private static ExtendedRobot robot = createRobot();

    public static void main(String[] args) throws Exception {
        final bug6647340 test = new bug6647340();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    test.setupUI();
                }
            });
            test.test();
        } finally {
            if (test.frame != null) {
                test.frame.dispose();
            }
        }
    }

    private void setupUI() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JDesktopPane desktop = new JDesktopPane();
        frame.add(desktop);

        jif = new JInternalFrame("Internal Frame", true, true, true, true);
        jif.setBounds(20, 20, 200, 100);
        desktop.add(jif);
        jif.setVisible(true);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setBounds((screen.width - 400) / 2, (screen.height - 400) / 2, 400, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void test() throws Exception {
        sync();
        test1();
        sync();
        check1();
        sync();
        test2();
        sync();
        check2();
    }

    private void test1() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                setIcon(true);
                location = jif.getDesktopIcon().getLocation();
                Dimension size = frame.getSize();
                frame.setSize(size.width + 100, size.height + 100);
            }
        });
    }

    private void test2() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                setIcon(false);
            }
        });
        sync();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Dimension size = frame.getSize();
                frame.setSize(size.width - 100, size.height - 100);
            }
        });
        sync();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                setIcon(true);
            }
        });
    }

    private void check1() {
        if (!jif.getDesktopIcon().getLocation().equals(location)) {
            System.out.println("First test passed");
        } else {
            throw new RuntimeException("Icon isn't shifted with the frame bounds");
        }
    }

    private void check2() {
        if (jif.getDesktopIcon().getLocation().equals(location)) {
            System.out.println("Second test passed");
        } else {
            throw new RuntimeException("Icon isn't located near the frame bottom");
        }
    }

    private static void sync() {
        robot.waitForIdle();
    }
    private static ExtendedRobot createRobot() {
        try {
             ExtendedRobot robot = new ExtendedRobot();
             return robot;
         }catch(Exception ex) {
             ex.printStackTrace();
             throw new Error("Unexpected Failure");
         }
    }

    private void setIcon(boolean b) {
        try {
            jif.setIcon(b);
        } catch (PropertyVetoException e) {
            e.printStackTrace();
        }
    }
}
