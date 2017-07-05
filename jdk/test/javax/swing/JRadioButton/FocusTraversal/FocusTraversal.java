/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
   @bug 8129940
   @summary JRadioButton does not honor non-standard FocusTraversalKeys
   @author Semyon Sadetsky
  */

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public class FocusTraversal {

    private static JFrame frame;
    private static JRadioButton a;
    private static JRadioButton d;
    private static JTextField next;
    private static JTextField prev;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame = new JFrame("FocusTraversalTest");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setUndecorated(true);

                Set<KeyStroke> keystrokes = new HashSet<KeyStroke>();
                keystrokes.add(KeyStroke.getKeyStroke("TAB"));
                keystrokes.add(KeyStroke.getKeyStroke("ENTER"));
                frame.setFocusTraversalKeys(
                        KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                        keystrokes);

                a = new JRadioButton("a");
                JRadioButton b = new JRadioButton("b");
                JRadioButton c = new JRadioButton("c");
                d = new JRadioButton("d");

                ButtonGroup radioButtonGroup = new ButtonGroup();
                radioButtonGroup.add(a);
                radioButtonGroup.add(b);
                radioButtonGroup.add(c);
                radioButtonGroup.add(d);

                JPanel panel = new JPanel();
                prev = new JTextField("text");
                panel.add(prev);
                panel.add(a);
                panel.add(b);
                panel.add(c);
                panel.add(d);
                next = new JTextField("text");
                panel.add(next);

                JPanel root = new JPanel();
                root.setLayout(new BorderLayout());
                root.add(panel, BorderLayout.CENTER);
                root.add(new JButton("OK"), BorderLayout.SOUTH);

                frame.add(root);
                frame.pack();
                frame.setVisible(true);
            }
        });

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                a.requestFocus();
            }
        });

        Robot robot = new Robot();
        robot.waitForIdle();

        robot.setAutoDelay(200);

        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                Component focusOwner =
                        FocusManager.getCurrentManager().getFocusOwner();
                if (focusOwner != next) {
                    throw new RuntimeException(
                            "Focus component is wrong after forward key " + focusOwner);
                }
            }
        });

        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                Component focusOwner =
                        FocusManager.getCurrentManager().getFocusOwner();
                if (focusOwner != d) {
                    throw new RuntimeException(
                            "Focus component is wrong after backward key " + focusOwner);
                }
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.dispose();
            }
        });
        System.out.println("ok");

    }
}
