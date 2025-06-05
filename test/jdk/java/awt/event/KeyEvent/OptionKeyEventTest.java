/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8311922
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary To test both option key press and release event on Mac OS
 * @run main/manual OptionKeyEventTest
 * @requires (os.family == "mac")
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class OptionKeyEventTest extends JFrame
        implements KeyListener, ActionListener {
    private static JTextArea displayArea;
    private static JTextField typingArea;
    private static final String newline = System.getProperty("line.separator");
    private static final String INSTRUCTIONS =
            "This test checks if the key events for the left and right\n" +
            "option keys are correct. To complete the test, ensure the\n" +
            "OptionKeyEventTest window's typing area is focused.\n" +
            "Press and release the left option key. Then press and release\n" +
            "the right option key. Confirm in the display area and pass the\n" +
            "test if these are correct, otherwise this test fails: \n\n" +
            "1. 'KEY PRESSED' appears first, followed by a 'KEY RELEASED'\n" +
            "for each option button\n" +
            "2. 'key location' shows 'left' or 'right' accordingly, not\n" +
            "'standard' or 'unknown'";

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException {
        PassFailJFrame passFailJFrame = new PassFailJFrame("OptionKeyEventTest",
                INSTRUCTIONS, 5, 15, 35);
        createAndShowGUI();
        passFailJFrame.awaitAndCheck();
    }

    private static void createAndShowGUI()
            throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            OptionKeyEventTest frame =
                    new OptionKeyEventTest("OptionKeyEventTest");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.addComponentsToPane();
            frame.pack();
            frame.setVisible(true);
            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.HORIZONTAL);
            typingArea.requestFocus();
        });
    }

    private void addComponentsToPane() {
        JButton button = new JButton("Clear");
        button.addActionListener(this);

        typingArea = new JTextField(20);
        typingArea.addKeyListener(this);

        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setFocusable(false);
        JScrollPane scrollPane = new JScrollPane(displayArea);
        scrollPane.setPreferredSize(new Dimension(375, 125));

        getContentPane().add(typingArea, BorderLayout.PAGE_START);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(button, BorderLayout.PAGE_END);
    }

    public OptionKeyEventTest(String name) {
        super(name);
    }

    public void keyTyped(KeyEvent e) {
        displayInfo(e, "KEY TYPED: ");
    }

    public void keyPressed(KeyEvent e) {
        displayInfo(e, "KEY PRESSED: ");
    }

    public void keyReleased(KeyEvent e) {
        displayInfo(e, "KEY RELEASED: ");
    }

    public void actionPerformed(ActionEvent e) {
        //Clear the text components.
        displayArea.setText("");
        typingArea.setText("");

        typingArea.requestFocusInWindow();
    }

    private void displayInfo(KeyEvent e, String keyStatus) {
        int id = e.getID();
        String keyString;
        if (id == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            keyString = "key character = '" + c + "'";
        } else {
            int keyCode = e.getKeyCode();
            keyString = "key code = " + keyCode
                    + " ("
                    + KeyEvent.getKeyText(keyCode)
                    + ")";
        }

        int modifiersEx = e.getModifiersEx();
        String modString = "extended modifiers = " + modifiersEx;
        String tmpString = KeyEvent.getModifiersExText(modifiersEx);
        if (tmpString.length() > 0) {
            modString += " (" + tmpString + ")";
        } else {
            modString += " (no extended modifiers)";
        }

        String actionString = "action key? ";
        if (e.isActionKey()) {
            actionString += "YES";
        } else {
            actionString += "NO";
        }

        String locationString = "key location: ";
        int location = e.getKeyLocation();
        if (location == KeyEvent.KEY_LOCATION_STANDARD) {
            locationString += "standard";
        } else if (location == KeyEvent.KEY_LOCATION_LEFT) {
            locationString += "left";
        } else if (location == KeyEvent.KEY_LOCATION_RIGHT) {
            locationString += "right";
        } else if (location == KeyEvent.KEY_LOCATION_NUMPAD) {
            locationString += "numpad";
        } else { // (location == KeyEvent.KEY_LOCATION_UNKNOWN)
            locationString += "unknown";
        }

        displayArea.append(keyStatus + newline
                + " " + keyString + newline
                + " " + modString + newline
                + " " + actionString + newline
                + " " + locationString + newline);
        displayArea.setCaretPosition(displayArea.getDocument().getLength());
    }
}