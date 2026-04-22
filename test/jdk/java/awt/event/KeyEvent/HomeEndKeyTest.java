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
 * bug 4268912 4115514
 * summary Ensures that KeyEvent has right results for the following
 *          non-numpad keys: Home/Eng/PageUp/PageDn
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HomeEndKeyTest
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;


public class HomeEndKeyTest extends Frame implements KeyListener {
   static String INSTRUCTIONS = """
           Before starting this test make sure that system shortcuts and
           keybindings for the keys in the list below are disabled.
           For example pressing "Print Screen" key should not launch
           screen capturing software.
           Click on the text field in the window named "Check keyCode values"
           and one by one start typing the keys in the list below.
           (If you do not have some of the keys on your keyboard skip it
           and move to the next line).
           After clicking each key look at the log area - the printed name
           and key code should correspond to the ones for the key you typed.
           Note that on some systems the graphical symbol for the key
           can be printed instead of the symbolic name.
           If you do not encounter unexpected key codes for the keys you typed,
           press "Pass". Otherwise press "Fail".

                    Key     Keycode
                    -------------------------
                    PrintScreen  154
                    ScrollLock   145
                    Pause         19

                    Insert       155
                    Del          127
                    Home          36
                    End           35
                    PageUp        33
                    PageDown      34

                    Left Arrow    37
                    Up Arrow      38
                    Right Arrow   39
                    Down Arrow    40
           """;

   public HomeEndKeyTest() {
       super("Check KeyCode values");
       setLayout(new BorderLayout());
       TextField tf = new TextField(30);
       tf.addKeyListener(this);
       add(tf, BorderLayout.CENTER);
       pack();
   }

    public void keyPressed(KeyEvent evt) {
        printKey(evt);
    }

    public void keyTyped(KeyEvent ignore) {
    }

    public void keyReleased(KeyEvent evt) {
        printKey(evt);
    }

    protected void printKey(KeyEvent evt) {
        String str;
        switch (evt.getID()) {
        case KeyEvent.KEY_PRESSED:
            str = "KEY_PRESSED";
            break;
        case KeyEvent.KEY_RELEASED:
            str = "KEY_RELEASED";
            break;
        default:
            str = "unknown type";
        }

        str = str + ":name=" + KeyEvent.getKeyText(evt.getKeyCode()) +
            " keyCode=" + evt.getKeyCode();
        PassFailJFrame.log(str);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("HomeEndKeyTest Instructions")
                .instructions(INSTRUCTIONS)
                .logArea(20)
                .testUI(HomeEndKeyTest::new)
                .build()
                .awaitAndCheck();
    }
}
