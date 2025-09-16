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
 * @bug 4193779 4174399
 * @summary Ensures that KeyEvents have the right modifiers set
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @run main/manual KeyModifiers
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;

import jdk.test.lib.Platform;


public class KeyModifiers extends Frame implements KeyListener {
    public KeyModifiers() {
        super("Check KeyChar values");
        setLayout(new BorderLayout());
        TextField tf = new TextField(30);
        tf.addKeyListener(this);
        add(tf, BorderLayout.CENTER);
        pack();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {

        String keys;
        if (Platform.isWindows()) {
            keys = "\"Shift-n\", \"Alt-n\"\n";
        } else if (Platform.isOSX()) {
            keys = "\"Shift-n\", \"Alt-n\", \"Command-n\"\n";
        } else {
            keys = "\"Shift-n\", \"Alt-n\", \"Meta-n\"\n";
        }

        String INSTRUCTIONS1 = """
                Click on the text field in the window named "Check KeyChar values"
                and type the following key combinations:
                """;
        String INSTRUCTIONS2 = """
                After each combination check that the KeyPressed and KeyTyped modifiers
                are correctly displayed. If modifiers are correct press "Pass",
                otherwise press "Fail".
                """;
        PassFailJFrame.builder()
                .title("KeyModifiers Test Instructions")
                .instructions(INSTRUCTIONS1 + keys + INSTRUCTIONS2)
                .columns(45)
                .logArea(10)
                .testUI(KeyModifiers::new)
                .build()
                .awaitAndCheck();
    }

    public void keyPressed(KeyEvent evt) {
        int kc = evt.getKeyCode();

        if (kc == KeyEvent.VK_CONTROL) {
            return;
        }

        if ((kc == KeyEvent.VK_SHIFT) || (kc == KeyEvent.VK_META) ||
                (kc == KeyEvent.VK_ALT) || (kc == KeyEvent.VK_ALT_GRAPH)) {
            PassFailJFrame.log("Key pressed= " + KeyEvent.getKeyText(kc) +
                    "   modifiers = " + InputEvent.getModifiersExText(evt.getModifiersEx()));
        } else {
            PassFailJFrame.log("Key pressed = " + evt.getKeyChar() +
                    "   modifiers = " + InputEvent.getModifiersExText(evt.getModifiersEx()));
        }
    }

    public void keyTyped(KeyEvent evt) {
        int kc = evt.getKeyCode();

        if (kc == KeyEvent.VK_CONTROL) {
            return;
        }

        if ((kc == KeyEvent.VK_SHIFT) || (kc == KeyEvent.VK_META) ||
                (kc == KeyEvent.VK_ALT) || (kc == KeyEvent.VK_ALT_GRAPH)) {
            PassFailJFrame.log("Key typed = " + KeyEvent.getKeyText(kc) +
                    "   modifiers = " + InputEvent.getModifiersExText(evt.getModifiersEx()));
        } else {
            PassFailJFrame.log("Key typed = " + evt.getKeyChar() +
                    "   modifiers = " + InputEvent.getModifiersExText(evt.getModifiersEx()));
        }
    }

    public void keyReleased(KeyEvent evt) {
        int kc = evt.getKeyCode();

        if (kc == KeyEvent.VK_CONTROL)
            return;

        if ((kc == KeyEvent.VK_SHIFT) || (kc == KeyEvent.VK_META) ||
                (kc == KeyEvent.VK_ALT) || (kc == KeyEvent.VK_ALT_GRAPH)) {
            PassFailJFrame.log("Key = released " + KeyEvent.getKeyText(kc) +
                    "   modifiers = " + InputEvent.getModifiersExText(evt.getModifiersEx()));
        } else {
            PassFailJFrame.log("Key released = " + evt.getKeyChar() +
                    "   modifiers = " + InputEvent.getModifiersExText(evt.getModifiersEx()));
        }
    }
}
