/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
  @test
  @bug       6182359
  @summary   Tests that Window having non-focusable owner can't be a focus owner.
  @author    Anton Tarasov: area=awt.focus
  @run       applet NonfocusableOwnerTest.html
*/

import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;
import java.lang.reflect.*;
import java.io.*;

public class NonfocusableOwnerTest extends Applet {
    Robot robot;
    Frame frame;
    Dialog dialog;
    Window window1;
    Window window2;
    Button button = new Button("button");
//    PrintStream Sysout = System.out;

    public static void main(String[] args) {
        NonfocusableOwnerTest test = new NonfocusableOwnerTest();
        test.init();
        test.start();
    }

    public void init() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Error: unable to create robot", e);
        }
        // Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());
    }

    public void start() {
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    Sysout.println(e.toString());
                }
            }, FocusEvent.FOCUS_EVENT_MASK | WindowEvent.WINDOW_FOCUS_EVENT_MASK | WindowEvent.WINDOW_EVENT_MASK);

        frame = new Frame("Frame");
        frame.setName("Frame-owner");
        dialog = new Dialog(frame, "Dialog");
        dialog.setName("Dialog-owner");

        window1 = new Window(frame);
        window1.setName("1st child");
        window2 = new Window(window1);
        window2.setName("2nd child");

        test1(frame, window1);
        test2(frame, window1, window2);
        test3(frame, window1, window2);

        window1 = new Window(dialog);
        window1.setName("1st child");
        window2 = new Window(window1);
        window2.setName("2nd child");

        test1(dialog, window1);
        test2(dialog, window1, window2);
        test3(dialog, window1, window2);

        Sysout.println("Test passed.");
    }

    void test1(Window owner, Window child) {
        Sysout.println("* * * STAGE 1 * * *\nowner=" + owner);

        owner.setFocusableWindowState(false);
        owner.setSize(100, 100);
        owner.setVisible(true);

        child.add(button);
        child.setBounds(0, 300, 100, 100);
        child.setVisible(true);

        waitTillShown(child);

        clickOn(button);
        if (button == KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()) {
            throw new RuntimeException("Test Failed.");
        }
        owner.dispose();
        child.dispose();
    }

    void test2(Window owner, Window child1, Window child2) {
        Sysout.println("* * * STAGE 2 * * *\nowner=" + owner);

        owner.setFocusableWindowState(false);
        owner.setSize(100, 100);
        owner.setVisible(true);

        child1.setFocusableWindowState(true);
        child1.setBounds(0, 300, 100, 100);
        child1.setVisible(true);

        child2.add(button);
        child2.setBounds(0, 500, 100, 100);
        child2.setVisible(true);

        clickOn(button);
        if (button == KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()) {
            throw new RuntimeException("Test failed.");
        }
        owner.dispose();
        child1.dispose();
        child2.dispose();
    }

    void test3(Window owner, Window child1, Window child2) {
        Sysout.println("* * * STAGE 3 * * *\nowner=" + owner);

        owner.setFocusableWindowState(true);
        owner.setSize(100, 100);
        owner.setVisible(true);

        child1.setFocusableWindowState(false);
        child1.setBounds(0, 300, 100, 100);
        child1.setVisible(true);

        child2.setFocusableWindowState(true);
        child2.add(button);
        child2.setBounds(0, 500, 100, 100);
        child2.setVisible(true);

        clickOn(button);

        System.err.println("focus owner: " + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        if (button != KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()) {
            throw new RuntimeException("Test failed.");
        }
        owner.dispose();
        child1.dispose();
        child2.dispose();
    }

    void clickOn(Component c) {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();

        Sysout.println("Clicking " + c);

        if (c instanceof Frame) {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + ((Frame)c).getInsets().top/2);
        } else {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + (int)(d.getHeight()/2));
        }
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        waitForIdle();
    }

    void waitTillShown(Component c) {
        while (true) {
            try {
                Thread.sleep(100);
                c.getLocationOnScreen();
                break;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (IllegalComponentStateException e) {}
        }
    }
    void waitForIdle() {
        try {
            Toolkit.getDefaultToolkit().sync();
            sun.awt.SunToolkit.flushPendingEvents();
            EventQueue.invokeAndWait( new Runnable() {
                    public void run() {} // Dummy implementation
                });
        } catch(InterruptedException ie) {
            Sysout.println("waitForIdle, non-fatal exception caught:");
            ie.printStackTrace();
        } catch(InvocationTargetException ite) {
            Sysout.println("waitForIdle, non-fatal exception caught:");
            ite.printStackTrace();
        }

        // wait longer...
        robot.delay(200);
    }
}

/****************************************************
 Standard Test Machinery
 DO NOT modify anything below -- it's a standard
  chunk of code whose purpose is to make user
  interaction uniform, and thereby make it simpler
  to read and understand someone else's test.
 ****************************************************/

/**
 This is part of the standard test machinery.
 It creates a dialog (with the instructions), and is the interface
  for sending text messages to the user.
 To print the instructions, send an array of strings to Sysout.createDialog
  WithInstructions method.  Put one line of instructions per array entry.
 To display a message for the tester to see, simply call Sysout.println
  with the string to be displayed.
 This mimics System.out.println but works within the test harness as well
  as standalone.
 */

class Sysout
{
    static TestDialog dialog;

    public static void createDialogWithInstructions( String[] instructions )
    {
        dialog = new TestDialog( new Frame(), "Instructions" );
        dialog.printInstructions( instructions );
        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }

    public static void createDialog( )
    {
        dialog = new TestDialog( new Frame(), "Instructions" );
        String[] defInstr = { "Instructions will appear here. ", "" } ;
        dialog.printInstructions( defInstr );
        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }


    public static void printInstructions( String[] instructions )
    {
        dialog.printInstructions( instructions );
    }


    public static void println( String messageIn )
    {
        System.err.println(messageIn);
    }

}// Sysout  class

/**
  This is part of the standard test machinery.  It provides a place for the
   test instructions to be displayed, and a place for interactive messages
   to the user to be displayed.
  To have the test instructions displayed, see Sysout.
  To have a message to the user be displayed, see Sysout.
  Do not call anything in this dialog directly.
  */
class TestDialog extends Dialog
{

    TextArea instructionsText;
    TextArea messageText;
    int maxStringLength = 80;

    //DO NOT call this directly, go through Sysout
    public TestDialog( Frame frame, String name )
    {
        super( frame, name );
        int scrollBoth = TextArea.SCROLLBARS_BOTH;
        instructionsText = new TextArea( "", 15, maxStringLength, scrollBoth );
        add( "North", instructionsText );

        messageText = new TextArea( "", 5, maxStringLength, scrollBoth );
        add("Center", messageText);

        pack();

        setVisible(true);
    }// TestDialog()

    //DO NOT call this directly, go through Sysout
    public void printInstructions( String[] instructions )
    {
        //Clear out any current instructions
        instructionsText.setText( "" );

        //Go down array of instruction strings

        String printStr, remainingStr;
        for( int i=0; i < instructions.length; i++ )
        {
            //chop up each into pieces maxSringLength long
            remainingStr = instructions[ i ];
            while( remainingStr.length() > 0 )
            {
                //if longer than max then chop off first max chars to print
                if( remainingStr.length() >= maxStringLength )
                {
                    //Try to chop on a word boundary
                    int posOfSpace = remainingStr.
                        lastIndexOf( ' ', maxStringLength - 1 );

                    if( posOfSpace <= 0 ) posOfSpace = maxStringLength - 1;

                    printStr = remainingStr.substring( 0, posOfSpace + 1 );
                    remainingStr = remainingStr.substring( posOfSpace + 1 );
                }
                //else just print
                else
                {
                    printStr = remainingStr;
                    remainingStr = "";
                }

                instructionsText.append( printStr + "\n" );

            }// while

        }// for

    }//printInstructions()

    //DO NOT call this directly, go through Sysout
    public void displayMessage( String messageIn )
    {
        messageText.append( messageIn + "\n" );
        System.out.println(messageIn);
    }

}// TestDialog  class
