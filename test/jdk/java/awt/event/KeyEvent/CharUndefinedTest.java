/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4115484 4164672 4167893
 * @summary Ensures that KeyEvent has right keyChar for modifier and action keys.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CharUndefinedTest
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;

public class CharUndefinedTest extends Frame implements KeyListener {

    static String INSTRUCTIONS = """
            Click on the text field inside the window named "Check KeyChar values".
            Of any of the keys mentioned in this list that exist on your keyboard
            press each of the listed keys once and also press them in two-key combinations such as
            Control-Shift or Alt-Control.
            The list of keys is: "Control, Shift, Meta, Alt, Command, Option".
            After that press all function keys from F1 to F12 once,
            Insert, Home, End, PageUp, PageDown and four arrow keys.
            Check the log area below. If there are no messages starting with word "ERROR"
            press "Pass" otherwise press "Fail".
            """;

    public CharUndefinedTest() {
        super("Check KeyChar values");
        setLayout(new BorderLayout());
        TextField tf = new TextField(30);
        tf.addKeyListener(this);
        add(tf, BorderLayout.CENTER);
        pack();
        tf.requestFocus();
    }

    public void keyPressed(KeyEvent e) {
        if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            PassFailJFrame.log("ERROR: KeyPressed: keyChar = " + e.getKeyChar() +
                    " keyCode = " + e.getKeyCode() + " " + e.getKeyText(e.getKeyCode()));
        }
    }

    public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            PassFailJFrame.log("ERROR: KeyTyped: keyChar = " + e.getKeyChar() +
                    " keyCode = " + e.getKeyCode() + " " + e.getKeyText(e.getKeyCode()));
        }
    }

    public void keyReleased(KeyEvent e) {
        if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            PassFailJFrame.log("ERROR: KeyReleased: keyChar = " + e.getKeyChar() +
                    " keyCode = " + e.getKeyCode() + " " + e.getKeyText(e.getKeyCode()));
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .logArea(10)
                .testUI(CharUndefinedTest::new)
                .build()
                .awaitAndCheck();
    }
}
