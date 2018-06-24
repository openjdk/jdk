/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
test
@bug 4799136
@summary Tests that type-ahead for dialog works and doesn't block program
@author  area=awt.focus
@run applet TestDialogTypeAhead.html
*/

// Note there is no @ in front of test above.  This is so that the
//  harness will not mistake this file as a test file.  It should
//  only see the html file as a test file. (the harness runs all
//  valid test files, so it would run this test twice if this file
//  were valid as well as the html file.)
// Also, note the area= after Your Name in the author tag.  Here, you
//  should put which functional area the test falls in.  See the
//  AWT-core home page -> test areas and/or -> AWT team  for a list of
//  areas.
// Note also the 'TestDialogTypeAhead.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * TestDialogTypeAhead.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;
import test.java.awt.regtesthelpers.Util;

//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class TestDialogTypeAhead extends Applet
{
    //Declare things used in the test, like buttons and labels here
    static Frame f;
    static Button b;
    static Dialog d;
    static Button ok;
    static Semaphore pressSema = new Semaphore();
    static Semaphore robotSema = new Semaphore();
    static volatile boolean gotFocus = false;
    static Robot robot;
    public void init()
    {
        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.

        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    System.err.println(e.toString());
                }
            }, AWTEvent.KEY_EVENT_MASK);

        KeyboardFocusManager.setCurrentKeyboardFocusManager(new TestKFM());

        this.setLayout (new BorderLayout ());

        f = new Frame("frame");
        b = new Button("press");
        d = new Dialog(f, "dialog", true);
        ok = new Button("ok");
        d.add(ok);
        d.pack();

        ok.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    System.err.println("OK pressed");
                    d.dispose();
                    f.dispose();
                    // Typed-ahead key events should only be accepted if
                    // they arrive after FOCUS_GAINED
                    if (gotFocus) {
                        pressSema.raise();
                    }
                }
            });
        ok.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    gotFocus = true;
                    System.err.println("Ok got focus");
                }
            });
        f.add(b);
        f.pack();
        b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    System.err.println("B pressed");

                    EventQueue.invokeLater(new Runnable() {
                            public void run() {
                                waitTillShown(d);
                                TestDialogTypeAhead.this.d.toFront();
                                TestDialogTypeAhead.this.moveMouseOver(d);
                            }
                        });

                    d.setVisible(true);
                }
            });

    }//End  init()

    public void start ()
    {
        //Get things going.  Request focus, set size, et cetera
        setSize (200,200);
        setVisible(true);
        validate();
        try {
            robot = new Robot();
        } catch (Exception e) {
            throw new RuntimeException("Can't create robot:" + e);
        }

        f.setVisible(true);
        waitTillShown(b);
        System.err.println("b is shown");
        f.toFront();
        moveMouseOver(f);
        waitForIdle();
        makeFocused(b);
        waitForIdle();
        System.err.println("b is focused");

        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);
        try {
            robotSema.doWait(1000);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted!");
        }
        if (!robotSema.getState()) {
            throw new RuntimeException("robotSema hasn't been triggered");
        }

        System.err.println("typing ahead");
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);
        waitForIdle();
        try {
            pressSema.doWait(3000);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted!");
        }
        if (!pressSema.getState()) {
            throw new RuntimeException("Type-ahead doesn't work");
        }

    }// start()

    private void moveMouseOver(Container c) {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();
        robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + (int)(d.getHeight()/2));
    }
    private void waitForIdle() {
        try {
            robot.waitForIdle();
            EventQueue.invokeAndWait( new Runnable() {
                                            public void run() {
                                                // dummy implementation
                                            }
                                        } );
        } catch(InterruptedException ite) {
            System.err.println("Robot.waitForIdle, non-fatal exception caught:");
            ite.printStackTrace();
        } catch(InvocationTargetException ine) {
            System.err.println("Robot.waitForIdle, non-fatal exception caught:");
            ine.printStackTrace();
        }
    }

    private void waitTillShown(Component c) {
        while (true) {
            try {
                Thread.sleep(100);
                c.getLocationOnScreen();
                break;
            } catch (InterruptedException ie) {
                ie.printStackTrace();
                break;
            } catch (Exception e) {
            }
        }
    }
    private void makeFocused(Component comp) {
        if (comp.isFocusOwner()) {
            return;
        }
        final Semaphore sema = new Semaphore();
        final FocusAdapter fa = new FocusAdapter() {
                public void focusGained(FocusEvent fe) {
                    sema.raise();
                }
            };
        comp.addFocusListener(fa);
        comp.requestFocusInWindow();
        if (comp.isFocusOwner()) {
            return;
        }
        try {
            sema.doWait(3000);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        comp.removeFocusListener(fa);
        if (!comp.isFocusOwner()) {
            throw new RuntimeException("Can't make " + comp + " focused, current owner is " + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        }
    }

    static class Semaphore {
        boolean state = false;
        int waiting = 0;
        public Semaphore() {
        }
        public synchronized void doWait() throws InterruptedException {
            if (state) {
                return;
            }
            waiting++;
            wait();
            waiting--;
        }
        public synchronized void doWait(int timeout) throws InterruptedException {
            if (state) {
                return;
            }
            waiting++;
            wait(timeout);
            waiting--;
        }
        public synchronized void raise() {
            state = true;
            if (waiting > 0) {
                notifyAll();
            }
        }
        public synchronized boolean getState() {
            return state;
        }
    }

    class TestKFM extends DefaultKeyboardFocusManager {
        protected synchronized void enqueueKeyEvents(long after,
                                                     Component untilFocused)
        {
            super.enqueueKeyEvents(after, untilFocused);

            if (untilFocused == TestDialogTypeAhead.this.ok) {
                TestDialogTypeAhead.this.robotSema.raise();
            }
        }
    }
}// class TestDialogTypeAhead

