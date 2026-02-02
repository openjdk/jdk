/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4083691
 * @summary Ensures that KeyEvent has right results for the following
 *         keys \*-+
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NumpadTest
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;


public class NumpadTest extends Frame implements KeyListener {
   static String INSTRUCTIONS = """
           This test requires a keyboard with a numeric keypad (numpad).
           If your keyboard does not have a numpad press "Pass" to skip testing.
           Make sure NumLock is on.
           Click on the text field in the window named "Check KeyChar values".
           Then, type the following keys/characters in the TextField.
           using the numpad keys:
           /*-+

           Verify that the keyChar and keyCode is correct for each key pressed.
           Remember that the keyCode for the KEY_TYPED event should be zero.
           Also verify that the character you typed appears in the TextField.

           Key    Name        keyChar    Keycode
           -------------------------------------
            /     Divide       /  47        111
            *     Multiply     *  42        106
            -     Subtract     -  45        109
            +     Add          +  43        107

           Now repeat with the NumLock off.

           If all keycodes are valid and expected characters appear
           in the text field press "Pass". Otherwise press "Fail".
           """;

   public NumpadTest() {
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
            return;
        }

        if (evt.isActionKey()) {
            PassFailJFrame.log("params= " + evt.paramString() + "  KeyChar:  " +
              (int) evt.getKeyChar() + "   Action Key");
        } else {
            PassFailJFrame.log("params= " + evt.paramString() + "  KeyChar:  " +
              (int) evt.getKeyChar());
        }
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        PassFailJFrame.builder()
                .title("NumpadTest Instructions")
                .instructions(INSTRUCTIONS)
                .logArea(20)
                .testUI(NumpadTest::new)
                .build()
                .awaitAndCheck();
    }
}
