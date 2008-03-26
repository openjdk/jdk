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
  @bug 4823903
  @summary Tests actual focused window retaining.
  @author Anton Tarasov: area=awt.focus
  @run applet ActualFocusedWindowRetaining.html
*/

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.applet.*;

public class ActualFocusedWindowRetaining extends Applet {
    public static Frame frame = new Frame("Other Frame");
    public static Frame owner = new Frame("Test Frame");
    public static Button otherButton1 = new Button("Other Button 1");
    public static Button otherButton2 = new Button("Other Button 2");
    public static Button otherButton3 = new Button("Other Button 3");
    public static Button testButton1 = new Button("Test Button 1");
    public static Button testButton2 = new Button("Test Button 2");
    public static Button testButton3 = new Button("Test Button 3");
    public static Window window1 = new TestWindow(owner, otherButton2, testButton2, 800, 200);
    public static Window window2 = new TestWindow(owner, otherButton3, testButton3, 800, 300);
    public static int step;
    public static Robot robot;

    public static void main(String[] args) {
        ActualFocusedWindowRetaining a = new ActualFocusedWindowRetaining();
        a.init();
        a.start();
    }

    public void init()
    {
        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());

        String[] instructions =
        {
            "This is an AUTOMATIC test",
            "simply wait until it is done"
        };
        Sysout.createDialogWithInstructions( instructions );
    }

    public void start ()
    {
        if (Toolkit.getDefaultToolkit().getClass()
                .getName().equals("sun.awt.motif.MToolkit")) {
            Sysout.println("No testing on Motif.");
            return;
        }

        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Error: unable to create robot", e);
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    Object src = e.getSource();
                    Class cls = src.getClass();

                    if (cls == TestWindow.class) {
                        Sysout.println(e.paramString() + " on <" + (src == window1 ? "Window 1" : "Window 2") + ">");
                    } else if (cls == Frame.class) {
                        Sysout.println(e.paramString() + " on <" + ((Frame)src).getTitle() + ">");
                    } else if (cls == Button.class) {
                        Sysout.println(e.paramString() + " on <" + ((Button)src).getLabel() + ">");
                    } else {
                        Sysout.println(e.paramString() + " on <Non-testing component>");
                    }
                }
            }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK);

        setSize (200,200);
        setVisible(true);
        validate();

        frame.setSize(new Dimension(400, 100));
        frame.setLocation(800, 400);
        frame.setVisible(true);
        frame.toFront();

        owner.setLayout(new FlowLayout());
        owner.add(testButton1);
        owner.add(otherButton1);
        owner.pack();
        owner.setLocation(800, 100);
        owner.setSize(new Dimension(400, 100));
        owner.setVisible(true);
        owner.toFront();
        waitTillShown(owner);

        window1.setVisible(true);
        window2.setVisible(true);
        window1.toFront();
        window2.toFront();
        // Wait longer...
        waitTillShown(window1);
        waitTillShown(window2);

        test();

        frame.dispose();
        owner.dispose();
    }

    public void test() {

        Button[] butArr = new Button[] {testButton3, testButton2, testButton1};
        Window[] winArr = new Window[] {window2, window1, owner};

        step = 1;
        for (int i = 0; i < 3; i++) {
            clickOnCheckFocusOwner(butArr[i]);
            clickOnCheckFocusedWindow(frame);
            clickOn(owner);
            if (!checkFocusedWindow(winArr[i])) {
                stopTest("Test failed: actual focused window didn't get a focus");
            }
            if (!checkFocusOwner(butArr[i])) {
                stopTest("Test failed: actual focus owner didn't get a focus");
            }
            step++;
        }

        step = 4;
        clickOnCheckFocusOwner(testButton3);
        clickOnCheckFocusOwner(testButton1);
        clickOnCheckFocusedWindow(frame);
        clickOn(owner);
        if (!checkFocusedWindow(owner)) {
            stopTest("Test failed: actual focused window didn't get a focus");
        }
        if (!checkFocusOwner(testButton1)) {
            stopTest("Test failed: actual focus owner didn't get a focus");
        }

        step = 5;
        clickOnCheckFocusOwner(testButton3);
        clickOnCheckFocusOwner(testButton2);
        clickOnCheckFocusedWindow(frame);
        clickOn(owner);
        if (!checkFocusedWindow(window1)) {
            stopTest("Test failed: actual focused window didn't get a focus");
        }
        if (!checkFocusOwner(testButton2)) {
            stopTest("Test failed: actual focus owner didn't get a focus");
        }

        step = 6;
        clickOnCheckFocusOwner(testButton1);
        clickOnCheckFocusOwner(testButton2);
        clickOnCheckFocusedWindow(frame);
        clickOn(owner);
        if (!checkFocusedWindow(window1)) {
            stopTest("Test failed: actual focused window didn't get a focus");
        }
        if (!checkFocusOwner(testButton2)) {
            stopTest("Test failed: actual focus owner didn't get a focus");
        }

        step = 7;
        clickOnCheckFocusOwner(testButton1);
        clickOnCheckFocusOwner(testButton2);
        clickOnCheckFocusedWindow(frame);
        window1.setVisible(false);
        clickOn(owner);
        if (!checkFocusedWindow(owner)) {
            stopTest("Test failed: actual focused window didn't get a focus");
        }
        if (!checkFocusOwner(testButton1)) {
            stopTest("Test failed: actual focus owner didn't get a focus");
        }

        step = 8;
        window1.setVisible(true);
        waitTillShown(window1);
        clickOnCheckFocusOwner(testButton2);
        clickOnCheckFocusedWindow(frame);
        clickOn(owner);
        if (!checkFocusedWindow(window1)) {
            stopTest("Test failed: actual focused window didn't get a focus");
        }
        if (!checkFocusOwner(testButton2)) {
            stopTest("Test failed: actual focus owner didn't get a focus");
        }
    }

    boolean checkFocusOwner(Component comp) {
        return (comp == KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    }

    boolean checkFocusedWindow(Window win) {
        return (win == KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow());
    }

    void waitTillShown(Component c) {
        ((sun.awt.SunToolkit) Toolkit.getDefaultToolkit()).realSync();
    }

    void clickOnCheckFocusOwner(Component c) {
        clickOn(c);
        if (!checkFocusOwner(c)) {
            stopTest("Error: can't bring a focus on Component by clicking on it");
        }
    }

    void clickOnCheckFocusedWindow(Frame f) {
        clickOn(f);
        if (!checkFocusedWindow(f)) {
            stopTest("Error: can't bring a focus on Frame by clicking on it");
        }
    }

    void clickOn(Component c)
    {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();

        if (c instanceof Frame) {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + ((Frame)c).getInsets().top/2);
        } else {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + (int)(d.getHeight()/2));
        }

        pause(100);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        pause(100);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        waitForIdle();
    }

    void waitForIdle() {
        ((sun.awt.SunToolkit) Toolkit.getDefaultToolkit()).realSync();
    }

    void pause(int msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException e) {
            Sysout.println("pause: non-fatal exception caught:");
            e.printStackTrace();
        }
    }

    void stopTest(String msg) {
        throw new RuntimeException(new String("Step " + step + ": " + msg));
    }
}

class TestWindow extends Window {
    TestWindow(Frame owner, Button otherButton, Button testButton, int x, int y) {
        super(owner);

        setLayout(new FlowLayout());
        setLocation(x, y);
        add(testButton);
        add(otherButton);
        pack();
        setBackground(Color.green);
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
    private static TestDialog dialog;

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
        dialog.displayMessage( messageIn );
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
