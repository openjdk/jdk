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
 * @bug 6325652
 * @summary Tests keyboard shortcuts
 * @author Sergey Malenkov
 * @library ..
 */

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.beans.PropertyVetoException;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JTextArea;

public class Test6325652 {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 300;

    public static void main(String[] args) throws Throwable {
        SwingTest.start(Test6325652.class);
    }

    private static Robot robot;
    private JInternalFrame internal;

    public Test6325652(JFrame frame) {
        JDesktopPane desktop = new JDesktopPane();
        desktop.add(create(0));
        desktop.add(this.internal = create(1));
        frame.add(desktop);
    }

    public void select() throws PropertyVetoException {
        this.internal.setSelected(true);
    }

    public static void stepFirst() throws AWTException {
        robot = new Robot(); // initialize shared static field first time
        click(KeyEvent.VK_CONTROL, KeyEvent.VK_F9); // iconify internal frame
    }

    public void stepFirstValidate() {
        if (!this.internal.isIcon()) {
            throw new Error("frame should be an icon");
        }
    }

    public static void stepSecond() {
        click(KeyEvent.VK_CONTROL, KeyEvent.VK_F6); // navigate to the icon
        click(KeyEvent.VK_CONTROL, KeyEvent.VK_F5); // restore the icon
    }

    public void stepSecondValidate() {
        if (this.internal.isIcon()) {
            throw new Error("frame should not be an icon");
        }
    }

    private static void click(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
        }
        for (int key : keys) {
            robot.keyRelease(key);
        }
    }

    private static JInternalFrame create(int index) {
        String text = "test" + index; // NON-NLS: frame identification
        index = index * 3 + 1;

        JInternalFrame internal = new JInternalFrame(text, true, true, true, true);
        internal.getContentPane().add(new JTextArea(text));
        internal.setBounds(10 * index, 10 * index, WIDTH, HEIGHT);
        internal.setVisible(true);
        return internal;
    }
}
