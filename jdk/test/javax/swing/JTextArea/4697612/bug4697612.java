/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4697612 6244705
 * @author Peter Zhelezniakov
 * @library ../../regtesthelpers
 * @build Util
 * @run main bug4697612
 */
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import javax.swing.text.BadLocationException;

public class bug4697612 {

    static final int FRAME_WIDTH = 300;
    static final int FRAME_HEIGHT = 300;
    static final int FONT_HEIGHT = 16;
    private static volatile int frameHeight;
    private static volatile int fontHeight;
    private static JFrame frame;
    private static JTextArea text;
    private static JScrollPane scroller;

    public static void main(String[] args) throws Throwable {
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                createAndShowGUI();
            }
        });

        robot.waitForIdle();

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                text.requestFocus();
            }
        });

        robot.waitForIdle();

        // 4697612: pressing PgDn + PgUp should not alter caret position
        Util.hitKeys(robot, KeyEvent.VK_HOME);
        Util.hitKeys(robot, KeyEvent.VK_PAGE_DOWN);


        int pos0 = getTextCaretPosition();
        int caretHeight = getTextCaretHeight();
        fontHeight = FONT_HEIGHT;

        // iterate two times, for different (even and odd) font height
        for (int i = 0; i < 2; i++) {

            SwingUtilities.invokeAndWait(new Runnable() {

                public void run() {
                    text.setFont(text.getFont().deriveFont(fontHeight));
                }
            });

            frameHeight = FRAME_HEIGHT;

            for (int j = 0; j < caretHeight; j++) {
                SwingUtilities.invokeAndWait(new Runnable() {

                    public void run() {
                        frame.setSize(FRAME_WIDTH, frameHeight);
                    }
                });

                robot.waitForIdle();

                Util.hitKeys(robot, KeyEvent.VK_PAGE_DOWN);
                Util.hitKeys(robot, KeyEvent.VK_PAGE_UP);
                robot.waitForIdle();

                int pos = getTextCaretPosition();
                if (pos0 != pos) {
                    throw new RuntimeException("Failed 4697612: PgDn & PgUp keys scroll by different amounts");
                }
                frameHeight++;
            }
            fontHeight++;
        }


        // 6244705: pressing PgDn at the very bottom should not scroll
        LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf.getID().equals("Aqua")) {
            Util.hitKeys(robot, KeyEvent.VK_END);
        } else {
            Util.hitKeys(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_END);
        }

        robot.waitForIdle();

        pos0 = getScrollerViewPosition();
        Util.hitKeys(robot, KeyEvent.VK_PAGE_DOWN);
        robot.waitForIdle();

        int pos = getScrollerViewPosition();

        if (pos0 != pos) {
            throw new RuntimeException("Failed 6244705: PgDn at the bottom causes scrolling");
        }
    }

    private static int getTextCaretPosition() throws Exception {
        final int[] result = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                result[0] = text.getCaretPosition();
            }
        });

        return result[0];
    }

    private static int getTextCaretHeight() throws Exception {
        final int[] result = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                try {
                    int pos0 = text.getCaretPosition();
                    Rectangle dotBounds = text.modelToView(pos0);
                    result[0] = dotBounds.height;
                } catch (BadLocationException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        return result[0];
    }

    private static int getScrollerViewPosition() throws Exception {
        final int[] result = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                result[0] = scroller.getViewport().getViewPosition().y;
            }
        });

        return result[0];
    }

    private static void createAndShowGUI() {
        frame = new JFrame();
        frame.setSize(FRAME_WIDTH, FRAME_HEIGHT);
        frame.setPreferredSize(new Dimension(FRAME_WIDTH, FRAME_HEIGHT));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        text = new JTextArea();
        try {
            InputStream is =
                    bug4697612.class.getResourceAsStream("bug4697612.txt");
            text.read(new InputStreamReader(is), null);
        } catch (IOException e) {
            throw new Error(e);
        }

        scroller = new JScrollPane(text);

        frame.getContentPane().add(scroller);

        frame.pack();
        frame.setVisible(true);
    }
}
