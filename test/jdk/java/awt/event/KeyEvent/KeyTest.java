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
 * @bug 4151419 4090870 4169733
 * @summary Ensures that KeyEvent has right results for the following
 *          keys  -=\[];,./
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual KeyTest
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;

public class KeyTest extends Frame implements KeyListener {

    static String INSTRUCTIONS = """
           Click on the text field in window named "Check KeyChar values"
           Type the following keys/characters in the TextField:
           - = \\ [ ] ; , . /
           Verify that the keyChar and keyCode is correct for each key pressed.
           Remember that the keyCode for the KEY_TYPED event should be zero.
           Also verify that the character you typed appears in the TextField.

           Key    Name        keyChar    Keycode
           -------------------------------------
            -     Minus        -  45        45
            =     Equals       =  61        61
            \\    Slash        \\   92        92
            [     Left Brace   [  91        91
            ]     Right Brace  ]  93        93
            ;     Semicolon    ;  59        59
            ,     Comma        ,  44        44
            .     Period       .  46        46
            /     Front Slash  /  47        47
           """;
    public KeyTest() {
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
                .title("KeyTest Instructions")
                .instructions(INSTRUCTIONS)
                .logArea(20)
                .testUI(KeyTest::new)
                .build()
                .awaitAndCheck();
    }
}
