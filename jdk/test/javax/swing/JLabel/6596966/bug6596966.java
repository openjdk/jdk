/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6596966
   @summary Some JFileChooser mnemonics do not work with sticky keys
   @run main bug6596966
   @author Pavel Porvatov
*/


import sun.awt.SunToolkit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class bug6596966 {
    private static JFrame frame;

    private static JLabel label;
    private static JButton button;
    private static JComboBox comboBox;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        SunToolkit toolkit = (SunToolkit) SunToolkit.getDefaultToolkit();

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                button = new JButton("Button");
                comboBox = new JComboBox();

                label = new JLabel("Label");
                label.setDisplayedMnemonic('L');
                label.setLabelFor(comboBox);

                JPanel pnContent = new JPanel();

                pnContent.add(button);
                pnContent.add(label);
                pnContent.add(comboBox);

                frame = new JFrame();

                frame.add(pnContent);
                frame.pack();
                frame.setVisible(true);
            }
        });

        toolkit.realSync();

        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_L);

        toolkit.realSync();

        toolkit.getSystemEventQueue().postEvent(new KeyEvent(label, KeyEvent.KEY_RELEASED,
                EventQueue.getMostRecentEventTime(), 0, KeyEvent.VK_L, 'L'));

        toolkit.realSync();

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    if (!comboBox.isFocusOwner()) {
                        throw new RuntimeException("comboBox isn't focus owner");
                    }
                }
            });
        } finally {
            robot.keyRelease(KeyEvent.VK_ALT);
        }
    }
}
