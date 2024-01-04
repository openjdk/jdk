/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6680988
 * @key headful
 * @summary verify that various shortcuts and accelerators work
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AcceleratorTest
 */

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class AcceleratorTest {
    static JFrame output;
    static JTextArea text;
    static JFrame jfr;
    static Hashtable<String, Integer> cmdHash = new Hashtable<String, Integer>();
    static String[] CMD = {
            "\u042E, keep me in focus",
            "Item Ctrl Be",
            "Item Ctrl English Period",
            "Item Ctrl English N",
            "\u0436"
    };

    public static void main(String[] args) throws Exception {
        String instructions =
                "Ensure you have Russian keyboard layout as a currently active.\n" +
                        "(1) Press Ctrl + \u0411 (a key with \",<\" on it) \n" +
                        "(2) Find a . (period) in this layout (perhaps \"/?\" or \"7&\" key). " +
                        "Press Ctrl + .\n" +
                        "(3) Press Ctrl + regular English . (period) key (on \".>\" )\n" +
                        "(4) Press Ctrl + key with English N.\n" +
                        "(5) Press Alt + \u042E (key with \".>\")\n" +
                        "(6) Press Alt + \u0436 (key with \";:\")\n" +
                        "If all expected commands will be fired, look for message\n" +
                        "\"All tests passed\"";

        for (int i = 0; i < CMD.length; i++) {
            cmdHash.put(CMD[i], 0);
        }

        PassFailJFrame testFrame = new PassFailJFrame.Builder()
                .title("Test Instructions Frame")
                .instructions(instructions)
                .testTimeOut(10)
                .rows(10)
                .columns(45)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            output = new JFrame("output");
            text = new JTextArea();
            output.getContentPane().add(text);

            jfr = new JFrame("AcceleratorTest");
            jfr.setLayout(new BorderLayout());
            JButton jbu;
            jfr.add((jbu = new JButton(CMD[0])));
            jbu.setMnemonic(java.awt.event.KeyEvent.getExtendedKeyCodeForChar('\u042E'));
            jbu.addActionListener(new ALi(CMD[0]));

            JMenuBar menuBar = new JMenuBar();
            jfr.setJMenuBar(menuBar);
            JMenu menu = new JMenu("Menu");
            menuBar.add(menu);

            JMenuItem menuItem = new JMenuItem(CMD[1]);
            menuItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent
                            .getExtendedKeyCodeForChar('\u0431'), InputEvent.CTRL_DOWN_MASK));

            JMenuItem menuItemEnglish = new JMenuItem(CMD[2]);
            menuItemEnglish.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD,
                    InputEvent.CTRL_DOWN_MASK));

            JMenuItem menuItemE1 = new JMenuItem(CMD[3]);
            menuItemE1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                    InputEvent.CTRL_DOWN_MASK));

            menuItem.addActionListener(new ALi(CMD[1]));
            menuItemEnglish.addActionListener(new ALi(CMD[2]));
            menuItemE1.addActionListener(new ALi(CMD[3]));

            menu.add(menuItem);
            menu.add(menuItemEnglish);
            menu.add(menuItemE1);

            KeyStroke ks;
            InputMap im = new InputMap();
            ks = KeyStroke.getKeyStroke(KeyEvent.getExtendedKeyCodeForChar('\u0436'),
                    java.awt.event.InputEvent.ALT_DOWN_MASK);
            im.put(ks, "pushAction");
            im.setParent(jbu.getInputMap(JComponent.WHEN_FOCUSED));
            jbu.setInputMap(JComponent.WHEN_FOCUSED, im);

            jbu.getActionMap().put("pushAction",
                    new AbstractAction("pushAction") {
                        public void actionPerformed(ActionEvent evt) {
                            if (evt.getActionCommand().equals(CMD[4])) {
                                cmdHash.put(CMD[4], 1);
                            }
                            boolean notYet = false;
                            for (int i = 0; i < CMD.length; i++) {
                                if (cmdHash.get(CMD[i]) == 0) notYet = true;
                            }
                            text.append(evt.getActionCommand() + " FIRED\n");
                            if (!notYet) {
                                text.append("All tests passed.");
                            }
                        }
                    }
            );
        });

        testFrame.addTestWindow(jfr);
        testFrame.addTestWindow(output);

        PassFailJFrame.positionTestWindow(jfr, PassFailJFrame.Position.HORIZONTAL);
        jfr.setSize(200, 200);

        PassFailJFrame.positionTestWindow(output, PassFailJFrame.Position.HORIZONTAL);
        output.setSize(200, 200);

        jfr.setVisible(true);
        output.setVisible(true);

        testFrame.awaitAndCheck();
    }

    public static class ALi implements ActionListener {
        String expectedCmd;

        public ALi(String eCmd) {
            expectedCmd = eCmd;
        }

        public void actionPerformed(ActionEvent ae) {
            if (cmdHash.containsKey(ae.getActionCommand())) {
                cmdHash.put(expectedCmd, 1);
            }
            boolean notYet = false;
            for (int i = 0; i < CMD.length; i++) {
                if (cmdHash.get(CMD[i]) == 0) notYet = true;
                //text.append(CMD[i]+":"+cmdHash.get(CMD[i]));
            }
            text.append(ae.getActionCommand() + " FIRED\n");
            if (!notYet) {
                text.append("All tests passed.\n");
            }
        }
    }
}
