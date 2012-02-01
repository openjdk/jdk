/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6827786
 * @summary Tests duplicate mnemonics
 * @author Peter Zhelezniakov
 * @library ../../regtesthelpers
 * @build Util
 * @run main bug6827786
 */
import java.awt.*;
import java.awt.event.KeyEvent;
import javax.swing.*;
import sun.awt.SunToolkit;

public class bug6827786 {

    private static JMenu menu;
    private static Component focusable;

    public static void main(String[] args) throws Exception {
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        Robot robot = new Robot();
        robot.setAutoDelay(50);

        SwingUtilities.invokeAndWait(new Runnable() {

            public void run() {
                createAndShowGUI();
            }
        });

        toolkit.realSync();

        SwingUtilities.invokeAndWait(new Runnable() {

            public void run() {
                focusable.requestFocus();
            }
        });

        toolkit.realSync();
        checkfocus();

        // select menu
        Util.hitKeys(robot, KeyEvent.VK_ALT, KeyEvent.VK_F);
        // select submenu
        Util.hitKeys(robot, KeyEvent.VK_S);
        toolkit.realSync();
        // verify submenu is selected
        verify(1);

        Util.hitKeys(robot, KeyEvent.VK_S);
        toolkit.realSync();
        // verify last item is selected
        verify(2);

        Util.hitKeys(robot, KeyEvent.VK_S);
        toolkit.realSync();
        // selection should wrap to first item
        verify(0);

        System.out.println("PASSED");

    }

    private static void checkfocus() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            public void run() {
                if (!focusable.isFocusOwner()) {
                    throw new RuntimeException("Button is not the focus owner.");
                }
            }
        });
    }

    private static void verify(final int index) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                MenuElement[] path =
                        MenuSelectionManager.defaultManager().getSelectedPath();
                MenuElement item = path[3];
                if (item != menu.getMenuComponent(index)) {
                    System.err.println("Selected: " + item);
                    System.err.println("Should be: "
                            + menu.getMenuComponent(index));
                    throw new RuntimeException("Test Failed");
                }
            }
        });
    }

    private static JMenuBar createMenuBar() {
        menu = new JMenu("File");
        menu.setMnemonic('F');

        menu.add(new JMenuItem("Save", 'S'));

        JMenu sub = new JMenu("Submenu");
        sub.setMnemonic('S');
        sub.add(new JMenuItem("Sub Item"));
        menu.add(sub);

        menu.add(new JMenuItem("Special", 'S'));

        JMenuBar bar = new JMenuBar();
        bar.add(menu);
        return bar;
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("bug6827786");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setJMenuBar(createMenuBar());
        focusable = new JButton("Set Focus Here");
        frame.add(focusable);
        frame.pack();
        frame.setVisible(true);
    }
}
