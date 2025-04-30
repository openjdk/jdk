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
 * @bug 4257434
 * @summary Ensures that the right results are produced by the
 *          carriage return keys.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CRTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CRTest extends Frame implements KeyListener, ActionListener {
    StringBuilder error = new StringBuilder();
    AtomicBoolean actionCompleted = new AtomicBoolean(false);
    static String INSTRUCTIONS = """
            This test requires keyboard with the numeric keypad (numpad).
            If your keyboard does not have numpad press "Pass" to skip testing.
            Click on the text field in window named "Check KeyChar values".
            Press Enter on keypad. Then press Return key on a standard keyboard.
            Then click on "Done" button. Test will pass or fail automatically.
            """;

    public CRTest() {
        super("Check KeyChar values");
        setLayout(new BorderLayout());
        TextField tf = new TextField(30);

        tf.addKeyListener(this);
        tf.addActionListener(this);

        add(tf, BorderLayout.CENTER);

        Button done = new Button("Done");
        done.addActionListener((event) -> {
            checkAndComplete();
        });
        add(done, BorderLayout.SOUTH);
        pack();
    }

    public void checkAndComplete() {
        if (!actionCompleted.get()) {
            error.append("\nNo action received!");
        }

        if (!error.isEmpty()) {
            PassFailJFrame.forceFail(error.toString());
        } else {
            PassFailJFrame.forcePass();
        }
    }

    public void keyPressed(KeyEvent evt) {
        if ((evt.getKeyChar() != '\n') || (evt.getKeyCode() != KeyEvent.VK_ENTER)) {
            error.append("\nKeyPressed: Unexpected code " + evt.getKeyCode());
        } else {
            PassFailJFrame.log("KeyPressed Test PASSED");
        }
    }

    public void keyTyped(KeyEvent evt) {
        if ((evt.getKeyChar() != '\n') || (evt.getKeyCode() != KeyEvent.VK_UNDEFINED)) {
            error.append("\nKeyTyped: Unexpected code " + evt.getKeyCode());
        } else {
            PassFailJFrame.log("KeyTyped Test PASSED");
        }
    }

    public void keyReleased(KeyEvent evt) {
        if ((evt.getKeyChar() != '\n') || (evt.getKeyCode() != KeyEvent.VK_ENTER)) {
            error.append("\nKeyReleased: Unexpected code " + evt.getKeyCode());
        } else {
            PassFailJFrame.log("KeyReleased Test PASSED");
        }
    }

    public void actionPerformed(ActionEvent evt) {
        PassFailJFrame.log("ActionPerformed Test PASSED");
        actionCompleted.set(true);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .logArea(10)
                .testUI(CRTest::new)
                .build()
                .awaitAndCheck();
    }
}
