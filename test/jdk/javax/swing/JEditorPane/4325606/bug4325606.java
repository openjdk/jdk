/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4325606
 * @summary Tests getting row start
 * @key headful
 * @run main bug4325606
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Utilities;

public class bug4325606 {

    public volatile boolean passed = true;
    private JFrame frame;
    private JEditorPane pane;

    public void setupGUI() {
        frame = new JFrame("Click Bug");
        frame.setLayout(new BorderLayout());

        pane = new JEditorPane();
        pane.addMouseListener(new ClickListener());
        pane.setContentType("text/html");
        pane.setText("<html><body>" +
                "<p>Here is line one</p>" +
                "<p>Here is line two</p>" +
                "</body></html>");

        frame.add(new JScrollPane(pane), BorderLayout.CENTER);

        frame.addWindowListener(new TestStateListener());
        frame.setLocation(50, 50);
        frame.setSize(400, 300);
        frame.setVisible(true);
    }

    class TestStateListener extends WindowAdapter {
        public void windowOpened(WindowEvent ev) {
            Robot robo;
            try {
                robo = new Robot();
            } catch (AWTException e) {
                throw new RuntimeException("Robot could not be created");
            }
            robo.setAutoDelay(100);
            robo.delay(1000);
            robo.mouseMove(100,100);
            robo.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robo.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robo.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robo.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robo.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robo.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    class ClickListener extends MouseAdapter {
        public void mouseClicked(MouseEvent event) {
            try {
                Utilities.getRowStart(pane, pane.getCaretPosition());
            } catch (BadLocationException blex) {
                passed = false;
            }
        }
    }

    public void cleanupGUI() {
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        bug4325606 b = new bug4325606();
        try {
            SwingUtilities.invokeAndWait(b::setupGUI);
            safeSleep(3000);
            if (!b.passed) {
                throw new RuntimeException("Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(b::cleanupGUI);
        }
    }

    public static void safeSleep(long ms) {
        long wakeup = System.currentTimeMillis() + ms;
        do {
            try {
                Thread.sleep(wakeup - System.currentTimeMillis());
            } catch (InterruptedException ie) {}
        } while (System.currentTimeMillis() < wakeup);
    }
}
