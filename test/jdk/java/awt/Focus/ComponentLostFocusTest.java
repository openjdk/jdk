/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4982943
 * @key headful
 * @summary focus lost in text fields or text areas, unable to enter characters from keyboar
 * @run main ComponentLostFocusTest
 */

import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ComponentLostFocusTest {

    Frame frame = new Frame("Frame");
    TextField tf = new TextField("Text Field");
    static Robot r;
    Dialog dialog = null;
    volatile boolean passed;

    public ComponentLostFocusTest() {
        try{
            r = new Robot();
        } catch(Exception e){
            throw new RuntimeException(e);
        }
        r.setAutoDelay(100);

        dialog = new Dialog(frame, "Dialog", true);

        frame.addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent e) {
                System.out.println("Frame gained focus: "+e);
            }
        });

        frame.setLayout (new FlowLayout ());
        frame.add(tf);
        frame.setSize(400,300);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
        frame.validate();
    }

    public void doTest() {
        // Do requesting focus to the modal dialog in order to after that
        // to do requesting focus to the frame
        System.out.println("dialog.setVisible.... ");
        new Thread(new Runnable() {
            public void run() {
                dialog.setVisible(true);
            }
        }).start();

        // The bug is that this construction leads to the redundant xRequestFocus
        // By the way, the requestFocusInWindow() works fine before the fix
        System.out.println("requesting.... ");
        frame.requestFocus();

        r.delay(1000);

        // Returning the focus to the initial frame will work correctly after the fix
        System.out.println("disposing.... ");
        dialog.dispose();

        r.delay(1000);

        // We want to track the GAIN_FOCUS from this time
        tf.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                System.out.println("TextField gained focus: "+e);
                passed = true;
            }
        });

        doRequestFocusToTextField();

        System.out.println("Focused window: " + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow());
        System.out.println("Focus owner: " + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());

        if (!passed) {
            throw new RuntimeException("TextField got no focus! Test failed.");
        }
    }

    private void doRequestFocusToTextField(){
        // do activation using press title
        Point loc = frame.getLocationOnScreen();
        r.mouseMove(loc.x + frame.getWidth()/2, loc.y + frame.getInsets().top/2);
        r.waitForIdle();
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.waitForIdle();

        // request focus to the text field
        tf.requestFocus();
    }

    public static final void main(String args[]){
        ComponentLostFocusTest test = new ComponentLostFocusTest();
        r.waitForIdle();
        r.delay(1000);
        test.doTest();
    }
}

