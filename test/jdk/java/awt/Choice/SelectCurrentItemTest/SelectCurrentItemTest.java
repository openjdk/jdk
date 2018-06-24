/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
  test 1.3 02/06/25
  @bug 4902933
  @summary Test that selecting the current item sends an ItemEvent
  @author bchristi : area= Choice
  @run applet SelectCurrentItemTest.html
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
// Note also the 'SelectCurrentItemTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * SelectCurrentItemTest.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;

//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class SelectCurrentItemTest extends Applet implements ItemListener,
 WindowListener, Runnable
{
    //Declare things used in the test, like buttons and labels here
    Frame frame;
    Choice theChoice;
    Robot robot;

    Object lock = new Object();
    boolean passed = false;

    public void init()
    {
        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.

        this.setLayout (new BorderLayout ());

        frame = new Frame("SelectCurrentItemTest");
        theChoice = new Choice();
        for (int i = 0; i < 10; i++) {
            theChoice.add(new String("Choice Item " + i));
        }
        theChoice.addItemListener(this);
        frame.add(theChoice);
        frame.addWindowListener(this);

        try {
            robot = new Robot();
            robot.setAutoDelay(500);
        }
        catch (AWTException e) {
            throw new RuntimeException("Unable to create Robot.  Test fails.");
        }

    }//End  init()

    public void start ()
    {
        //Get things going.  Request focus, set size, et cetera
        setSize (200,200);
        setVisible(true);
        validate();

        //What would normally go into main() will probably go here.
        //Use System.out.println for diagnostic messages that you want
        //to read after the test is done.
        //Use System.out.println for messages you want the tester to read.

        frame.setLocation(1,20);
        robot.mouseMove(10, 30);
        frame.pack();
        frame.setVisible(true);
        synchronized(lock) {
        try {
        lock.wait(120000);
        }
        catch(InterruptedException e) {}
        }
        robot.waitForIdle();
        if (!passed) {
            throw new RuntimeException("TEST FAILED!");
        }

        // wait to make sure ItemEvent has been processed

//        try {Thread.sleep(10000);} catch (InterruptedException e){}
    }// start()

    public void run() {
        try {Thread.sleep(1000);} catch (InterruptedException e){}
        // get loc of Choice on screen
        Point loc = theChoice.getLocationOnScreen();
        // get bounds of Choice
        Dimension size = theChoice.getSize();
        robot.mouseMove(loc.x + size.width - 10, loc.y + size.height / 2);

        robot.setAutoDelay(250);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        robot.setAutoDelay(1000);
        robot.mouseMove(loc.x + size.width / 2, loc.y + size.height + size.height / 2);
        robot.setAutoDelay(250);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.waitForIdle();
        synchronized(lock) {
            lock.notify();
        }
    }

    public void itemStateChanged(ItemEvent e) {
        System.out.println("ItemEvent received.  Test passes");
        passed = true;
    }

    public void windowOpened(WindowEvent e) {
        System.out.println("windowActivated()");
        Thread testThread = new Thread(this);
        testThread.start();
    }
    public void windowActivated(WindowEvent e) {
    }
    public void windowDeactivated(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowClosing(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}

}// class SelectCurrentItemTest
