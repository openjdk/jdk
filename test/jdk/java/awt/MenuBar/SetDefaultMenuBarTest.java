/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8333589
 * @key headful
 * @summary Check menu item actions work with Desktop.setDefaultMenuBar()
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetDefaultMenuBarTest
 */

public class SetDefaultMenuBarTest {
    static JFrame frame;
    static JTextArea text;
    final static boolean useDefaultMenuBar = true;

    public static void main(String[] args) throws Exception {
        try {
            String INSTRUCTIONS = """
                    Press cmd + Backspace.
                    If a "Delete pressed" message appears, pass the test.
                    Otherwise, fail the test.""";

            PassFailJFrame.builder()
                    .title("SetDefaultMenuBarTest")
                    .instructions(INSTRUCTIONS)
                    .rows(5)
                    .columns(35)
                    .testUI(SetDefaultMenuBarTest::createAndShowGUI)
                    .build()
                    .awaitAndCheck();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static JFrame createAndShowGUI() {
        frame = new JFrame("SetDefaultMenuBarTest");

        if (!useDefaultMenuBar) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 300);

        JMenuBar menuBar = new JMenuBar();

        if (!useDefaultMenuBar) {
            Desktop.getDesktop().setDefaultMenuBar(menuBar);
        } else {
            frame.setJMenuBar(menuBar);
        }

        JMenu menu = new JMenu("Test");
        menuBar.add(menu);

        var action = new DeleteAction();
        menu.add(action);

        text = new JTextArea();
        frame.add(text);

        return frame;
    }

    static class DeleteAction extends AbstractAction {
        public DeleteAction() {
            putValue(Action.NAME, "Delete");
            var keystroke = KeyStroke.getKeyStroke(
                    KeyEvent.VK_BACK_SPACE,
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
            );
            putValue(Action.ACCELERATOR_KEY, keystroke);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            text.append("Delete pressed\n");
        }
    }
}
