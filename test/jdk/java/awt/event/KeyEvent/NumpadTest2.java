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
 * @bug 4279566
 * @summary Tests that numpad keys produce the correct key codes and
 *           key chars when both the NumLock and CapsLock are on.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NumpadTest2
*/

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;

public class NumpadTest2 extends Frame implements KeyListener {
    static String INSTRUCTIONS = """
           Make sure that the NumLock and CapsLock are both ON.
           Click on the text field inside the window named "Check KeyChar values"
           Then, type the NumPad 7 key (not the regular 7 key).
           Verify that the keyChar and keyCode is correct for each key pressed.
           Remember that the keyCode for the KEY_TYPED event should be zero.
           If 7 appears in the text field and the key code printed is correct
           press "Pass", otherwise press "Fail".

           Key               Name             keyChar    Keycode
           -------------------------------------------------
           Numpad-7     Numpad-7      55         103
           """;

    public NumpadTest2() {
        super("Check KeyChar values");
        setLayout(new BorderLayout());
        TextField tf = new TextField(30);
        tf.addKeyListener(this);
        add(tf, BorderLayout.CENTER);
        pack();
    }

    public void keyPressed(KeyEvent evt) {
        printKey(evt);
    }

    public void keyTyped(KeyEvent evt) {
        printKey(evt);
    }

    public void keyReleased(KeyEvent evt) {
        printKey(evt);
    }

    protected void printKey(KeyEvent evt) {
        switch (evt.getID()) {
            case KeyEvent.KEY_TYPED:
                break;
            case KeyEvent.KEY_PRESSED:
                break;
            case KeyEvent.KEY_RELEASED:
                break;
            default:
                System.out.println("Other Event ");
                return;
        }

        if (evt.isActionKey()) {
            PassFailJFrame.log("params= " + evt.paramString() + "  KeyChar: " +
                    (int) evt.getKeyChar() + " Action Key");
        } else {
            PassFailJFrame.log("params= " + evt.paramString() + "  KeyChar: " +
                    (int) evt.getKeyChar());
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .logArea(10)
                .testUI(NumpadTest2::new)
                .build()
                .awaitAndCheck();
    }
}
