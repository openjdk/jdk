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
  @bug       6314575
  @summary   Tests that previosly focused owned window doesn't steal focus when an owner's component requests focus.
  @author    Anton Tarasov: area=awt-focus
  @run       applet ActualFocusedWindowBlockingTest.html
*/

import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;
import sun.awt.SunToolkit;

public class ActualFocusedWindowBlockingTest extends Applet {
    Robot robot;
    Frame owner = new Frame("Owner Frame");
    Window win = new Window(owner);
    Frame frame = new Frame("Auxiliary Frame");
    Button fButton = new Button("frame button") {public String toString() {return "Frame_Button";}};
    Button wButton = new Button("window button") {public String toString() {return "Window_Button";}};
    Button aButton = new Button("auxiliary button") {public String toString() {return "Auxiliary_Button";}};

    public static void main(String[] args) {
        ActualFocusedWindowBlockingTest app = new ActualFocusedWindowBlockingTest();
        app.init();
        app.start();
    }

    public void init() {
        // Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());
        Sysout.createDialogWithInstructions(new String[]
            {"Automatic test. Simply wait until it's done."});

        if ("sun.awt.motif.MToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) {
            return;
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    Sysout.println("--> " + e);
                }
            }, FocusEvent.FOCUS_EVENT_MASK | WindowEvent.WINDOW_FOCUS_EVENT_MASK);

        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Error: unable to create robot", e);
        }
        owner.add(fButton);
        win.add(wButton);
        frame.add(aButton);

        owner.setName("OWNER_FRAME");
        win.setName("OWNED_WINDOW");
        frame.setName("AUX_FRAME");

        tuneAndShowWindows(new Window[] {owner, win, frame});
    }

    public void start() {
        if ("sun.awt.motif.MToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) {
            Sysout.println("No testing on Motif. Test passed.");
            return;
        }

        Sysout.println("\nTest started:\n");

        // Test 1.

        clickOnCheckFocus(wButton);

        clickOnCheckFocus(aButton);

        clickOn(fButton);
        if (!testFocused(fButton)) {
            throw new TestFailedException("The owner's component [" + fButton + "] couldn't be focused by click");
        }

        // Test 2.

        clickOnCheckFocus(wButton);

        clickOnCheckFocus(aButton);

        fButton.requestFocus();
        realSync();
        if (!testFocused(fButton)) {
            throw new TestFailedException("The owner's component [" + fButton + "] couldn't be focused by request");
        }

        // Test 3.

        clickOnCheckFocus(wButton);

        clickOnCheckFocus(aButton);

        clickOnCheckFocus(fButton);

        clickOnCheckFocus(aButton);

        clickOn(owner);
        if (!testFocused(fButton)) {
            throw new TestFailedException("The owner's component [" + fButton + "] couldn't be focused as the most recent focus owner");
        }

        Sysout.println("Test passed.");
    }

    void tuneAndShowWindows(Window[] arr) {
        int y = 0;
        for (Window w: arr) {
            w.setLayout(new FlowLayout());
            w.setBounds(100, y, 400, 150);
            w.setBackground(Color.blue);
            w.setVisible(true);
            y += 200;
            realSync();
        }
    }

    void clickOn(Component c) {
        Sysout.println("Test: clicking " + c);

        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();

        if (c instanceof Frame) {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + ((Frame)c).getInsets().top/2);
            Sysout.println((p.x + (int)(d.getWidth()/2)) + " " +  (p.y + ((Frame)c).getInsets().top/2));
        } else {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + (int)(d.getHeight()/2));
        }
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.delay(100);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        realSync();
    }

    void clickOnCheckFocus(Component c) {
        clickOn(c);
        if (!testFocused(c)) {
            throw new RuntimeException("Error: [" + c + "] couldn't get focus by click.");
        }
    }

    boolean testFocused(Component c) {
        for (int i=0; i<10; i++) {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == c) {
                return true;
            }
            realSync();
        }
        return false;
    }

    void realSync() {
        ((SunToolkit)Toolkit.getDefaultToolkit()).realSync();
    }

    class TestFailedException extends RuntimeException {
        public TestFailedException(String cause) {
            super("Test failed. " + cause);
            Sysout.println(cause);
        }
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
        dialog.setLocation(500,0);
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
