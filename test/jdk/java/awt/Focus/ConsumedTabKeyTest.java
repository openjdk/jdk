/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
@test
@bug 4715649
@summary Tests that KEY_TYPED event for Tab key arrives if Tab key is not focus traversal key
@key headful
@run main ConsumedTabKeyTest
*/

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

public class ConsumedTabKeyTest extends Panel {
    TextField text;
    Button button = new Button("none");
    Semaphore focusSema = new Semaphore();
    Semaphore releaseSema = new Semaphore();
    Semaphore buttonFocusSema = new Semaphore();
    Robot robot;
    volatile boolean keyTyped;
    volatile boolean hasFocus;
    static Frame frame;

    public void init() {
        this.setLayout(new FlowLayout());
        text = new TextField();

        text.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                focusSema.raise();
            }
        });
        button.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                buttonFocusSema.raise();
            }
        });
        add(text);
        add(button);
        setSize(200, 200);
        setVisible(true);
        validate();
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            public void eventDispatched(AWTEvent e) {
                if (e.getID() == KeyEvent.KEY_RELEASED) {
                    releaseSema.raise();
                }
                if (e.getID() == KeyEvent.KEY_TYPED) {
                    keyTyped = true;
                }
            }
        }, InputEvent.KEY_EVENT_MASK);
        try {
            robot = new Robot();
        } catch (Exception re) {
            throw new RuntimeException("Couldn't create Robot");
        }
    }

    public void start() throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            if (!text.isFocusOwner()) {
                text.requestFocus();
            }

            text.setFocusTraversalKeysEnabled(false);
        });

        try {
            focusSema.doWait(1000);
        } catch (InterruptedException ie1) {
            throw new RuntimeException("Interrupted");
        }

        EventQueue.invokeAndWait(() -> {
            hasFocus = text.isFocusOwner();
        });

        if (!focusSema.getState() && !hasFocus) {
            throw new RuntimeException("Text didn't receive focus");
        }

        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);
        try {
            releaseSema.doWait(1000);
        } catch (InterruptedException ie2) {
            throw new RuntimeException("Interrupted");
        }

        if (!releaseSema.getState()) {
            throw new RuntimeException("KEY_RELEASED hasn't arrived");
        }

        if (!keyTyped) {
            throw new RuntimeException("KEY_TYPED for Tab key hasn't arrived");
        }

        EventQueue.invokeAndWait(() -> {
            text.setFocusTraversalKeysEnabled(true);
        });

        releaseSema.setState(false);
        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);
        try {
            buttonFocusSema.doWait(1000);
            releaseSema.doWait(1000);
        } catch (InterruptedException ie2) {
            throw new RuntimeException("Interrupted");
        }

        EventQueue.invokeAndWait(() -> {
            hasFocus = button.isFocusOwner();
        });

        if (!buttonFocusSema.getState() && !hasFocus) {
            throw new RuntimeException("Button hasn't received focus");
        }
        keyTyped = false;
        releaseSema.setState(false);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        try {
            releaseSema.doWait(1000);
        } catch (InterruptedException ie2) {
            throw new RuntimeException("Interrupted");
        }

        if (!releaseSema.getState()) {
            throw new RuntimeException("KEY_RELEASED hasn't arrived");
        }
        if (!keyTyped) {
            throw new RuntimeException("KEY_TYPED for A key hasn't arrived");
        }
        System.err.println("PASSED");
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        ConsumedTabKeyTest test = new ConsumedTabKeyTest();

        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("InvocationTargetException");
                frame.setLayout(new BorderLayout());
                frame.add(test, BorderLayout.CENTER);
                test.init();
                frame.setLocationRelativeTo(null);
                frame.pack();
                frame.setVisible(true);
            });
            test.start();
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }
}

class Semaphore {
    boolean state = false;
    int waiting = 0;

    public void doWait(int timeout) throws InterruptedException {
        synchronized (this) {
            if (state) return;
            waiting++;
            wait(timeout);
            waiting--;
        }
    }

    public void raise() {
        synchronized (this) {
            state = true;
            if (waiting > 0) {
                notifyAll();
            }
        }
    }

    public boolean getState() {
        synchronized (this) {
            return state;
        }
    }

    public void setState(boolean newState) {
        synchronized (this) {
            state = newState;
        }
    }
}
