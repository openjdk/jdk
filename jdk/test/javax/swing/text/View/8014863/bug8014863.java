/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8014863
 * @summary  Tests the calculation of the line breaks when a text is inserted
 * @author Dmitry Markov
 * @library ../../../regtesthelpers
 * @build Util
 * @run main bug8014863
 */

import sun.awt.SunToolkit;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.KeyEvent;

public class bug8014863 {

    private static JEditorPane editorPane;
    private static Robot robot;
    private static SunToolkit toolkit;

    public static void main(String[] args) throws Exception {
        toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        robot = new Robot();

        createAndShowGUI();

        toolkit.realSync();

        Util.hitKeys(robot, KeyEvent.VK_HOME);
        Util.hitKeys(robot, KeyEvent.VK_O);

        toolkit.realSync();

        if (3 != getNumberOfTextLines()) {
            throw new RuntimeException("The number of texts lines does not meet the expectation");
        }

        Util.hitKeys(robot, KeyEvent.VK_N);

        toolkit.realSync();

        if (3 != getNumberOfTextLines()) {
            throw new RuntimeException("The number of texts lines does not meet the expectation");
        }

        Util.hitKeys(robot, KeyEvent.VK_E);
        Util.hitKeys(robot, KeyEvent.VK_SPACE);
        Util.hitKeys(robot, KeyEvent.VK_T);
        Util.hitKeys(robot, KeyEvent.VK_W);

        toolkit.realSync();

        if (3 != getNumberOfTextLines()) {
            throw new RuntimeException("The number of texts lines does not meet the expectation");
        }
    }

    private static int getNumberOfTextLines() throws Exception {
        int numberOfLines = 0;
        int caretPosition = getCaretPosition();
        int current = 1;
        int previous;

        setCaretPosition(current);
        do {
            previous = current;
            Util.hitKeys(robot, KeyEvent.VK_DOWN);
            toolkit.realSync();
            current = getCaretPosition();
            numberOfLines++;
        } while (current != previous);

        setCaretPosition(caretPosition);
        return numberOfLines;
    }

    private static int getCaretPosition() throws Exception {
        final int[] result = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                result[0] = editorPane.getCaretPosition();
            }
        });
        return result[0];
    }

    private static void setCaretPosition(final int position) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                editorPane.setCaretPosition(position);
            }
        });
    }

    private static void createAndShowGUI() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                JFrame frame = new JFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                editorPane = new JEditorPane();
                HTMLEditorKit editorKit = new HTMLEditorKit();
                editorPane.setEditorKit(editorKit);
                editorPane.setText("<p>qqqq <em>pp</em> qqqq <em>pp</em> " +
                        "qqqq <em>pp</em> qqqq <em>pp</em> qqqq <em>pp</em> qqqq <em>pp" +
                        "</em> qqqq <em>pp</em> qqqq <em>pp</em> qqqq <em>pp</em> qqqq</p>");
                editorPane.setCaretPosition(1);

                frame.add(editorPane);
                frame.setSize(200, 200);
                frame.setVisible(true);
            }
        });
    }
}
