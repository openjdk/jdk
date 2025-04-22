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
 * @bug 4035364
 * @summary Checks that Caps Lock key works
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ModifiersTest
 */

import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.TextArea;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ModifiersTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Type some text in the TextArea in upper and lowercase,
                   using the Caps Lock ON/OFF.
                2. If Caps Lock toggles correctly and you are able to type in
                     both cases, the test PASS. Else Test FAILS.
                """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ModifiersTest::initialize)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("Modifiers Test");
        frame.setLayout(new GridLayout(1, 1));
        frame.addKeyListener(new KeyChecker());
        frame.setLayout(new GridLayout(2, 1));
        Label label = new Label("See if you can type in upper and lowercase using Caps Lock:");
        frame.add(label);
        TextArea ta = new TextArea();
        frame.add(ta);
        ta.addKeyListener(new KeyChecker());
        ta.requestFocus();
        frame.setSize(400, 300);
        return frame;
    }
}

// a KeyListener for debugging purposes
class KeyChecker extends KeyAdapter {
    public void keyPressed(KeyEvent ev) {
        System.out.println(ev);
    }

    public void keyReleased(KeyEvent ev) {
        System.out.println(ev);
    }

    public void keyTyped(KeyEvent ev) {
        System.out.println(ev);
    }
}
