/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6213128
 * @key headful
 * @summary Tests that choice is releasing input capture when a modal
 *          dialog is shown
 * @run main ChoiceModalDialogTest
 */

import java.awt.Choice;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ChoiceModalDialogTest {
    static Frame f;
    static Dialog d;
    static volatile boolean keyOK;
    static volatile boolean mouseOK;
    static TextField tf;
    static Choice c;

    public static void main(String[] args) throws Exception {
        Robot r;
        try {
            r = new Robot();
            r.setAutoDelay(100);
            EventQueue.invokeAndWait(() -> {
                f = new Frame("Frame");
                c = new Choice();
                f.setBounds(100, 300, 300, 200);
                f.setLayout(new FlowLayout());
                tf = new TextField(3);
                f.add(tf);

                c.add("1");
                c.add("2");
                c.add("3");
                c.add("4");
                f.add(c);

                tf.addFocusListener(new FocusAdapter() {
                    public void focusLost(FocusEvent ev) {
                        d = new Dialog(f, "Dialog", true);
                        d.setBounds(300, 300, 200, 150);
                        d.addKeyListener(new KeyAdapter() {
                            public void keyPressed(KeyEvent ev) {
                                keyOK = true;
                            }
                        });
                        d.addMouseListener(new MouseAdapter() {
                            public void mousePressed(MouseEvent ev) {
                                mouseOK = true;
                            }
                        });
                        d.setVisible(true);
                    }
                });

                f.setVisible(true);
                f.toFront();
            });
            r.waitForIdle();
            r.delay(1000);
            EventQueue.invokeAndWait(() -> {
                r.mouseMove(tf.getLocationOnScreen().x + tf.getSize().width / 2,
                        tf.getLocationOnScreen().y + tf.getSize().height / 2);
            });
            r.waitForIdle();
            r.delay(500);
            EventQueue.invokeAndWait(() -> {
                r.mouseMove(c.getLocationOnScreen().x + c.getSize().width - 4,
                        c.getLocationOnScreen().y + c.getSize().height / 2);
                r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            });
            r.waitForIdle();
            r.delay(500);
            EventQueue.invokeAndWait(() -> {
                r.mouseMove(d.getLocationOnScreen().x + d.getSize().width / 2,
                        d.getLocationOnScreen().y + d.getSize().height / 2);
                r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                r.keyPress(KeyEvent.VK_A);
                r.keyRelease(KeyEvent.VK_A);
            });
            r.waitForIdle();
            r.delay(500);
            if (!mouseOK) {
                throw new RuntimeException("Test Failed due to Mouse release failure!");
            }
            if (!keyOK) {
                throw new RuntimeException("Test Failed due to Key release failure!");
            }
            System.out.println("Test Passed!");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (d != null) {
                    d.dispose();
                }
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
