/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4549069
 * @summary  Tests if javax.swing.text.AbstractDocument.BranchElement.getEndOffset() throws AIOOBE
 * @key headful
 */

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.undo.UndoManager;

public class bug4549069 {
    static Timer timer;
    static volatile Point p;

    static JFrame f;
    static JTextArea jta;
    static UndoManager um;
    static Robot robot;

    public static void main(String[] argv) throws Exception {
        robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("bug4549069");
                f.addWindowListener(new TestStateListener());

                jta = new JTextArea();
                um = new UndoManager();
                jta.setDocument(new DefaultStyledDocument());
                jta.getDocument().addUndoableEditListener(um);

                String text = "Press Ctrl-Z (undo) to get\n" +
                        "a stacktrace U shouldn't XX\n";
                jta.setText(text);

                jta.addKeyListener(new KeyAdapter() {
                    public void keyPressed(KeyEvent e) {
                        if (um.canUndo()) {
                            um.undo();
                        }
                    }
                });

                f.getContentPane().add(jta);
                f.pack();
                f.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);
        } finally {
            if (f != null) {
                SwingUtilities.invokeAndWait(() -> {
                    f.dispose();
                });
            }
        }
    }

    static class TestStateListener extends WindowAdapter {
        public void windowOpened(WindowEvent ev) {
            timer = new Timer();
            timer.schedule(new RobotTask(), 1000);
        }
    }

    static class RobotTask extends TimerTask {
        public void run() {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    p = jta.getLocationOnScreen();
                });
            } catch (Exception e) {
                throw new RuntimeException("Could not get location");

            }
            robot.mouseMove(p.x, p.y);
            robot.waitForIdle();

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            robot.keyPress(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_SPACE);
            robot.waitForIdle();
        }
    }
}
