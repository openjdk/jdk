/*
 * Copyright (c) 2004, 2014, Oracle and/or its affiliates. All rights reserved.
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
  @bug        5090325
  @summary    Tests that Window's child can be focused on XAWT.
  @author     anton.tarasov@sun.com: area=awt.focus
  @run        applet ChildWindowFocusTest.html
*/

import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;
import java.lang.reflect.*;

public class ChildWindowFocusTest extends Applet {
    Robot robot;
    Frame frame = new Frame("Owner");
    Button button0 = new Button("button-0");
    TextField text0 = new TextField("text-0");
    TextField text1 = new TextField("text-1");
    Window win1 = new TestWindow(frame, text0, 110);
    Window win2 = new TestWindow(win1, text1, 220);
    Frame outerFrame = new Frame("Outer");
    Button button1 = new Button("button-1");
    int shift;

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
        Sysout.createDialogWithInstructions(new String[]
            {"This is an AUTOMATIC test", "simply wait until it is done"});

        Rectangle bounds = Sysout.dialog.getBounds();
        shift = (int)(bounds.x + bounds.width + 10);
    }

    public void start() {

        frame.setBounds(0, 50, 400, 100);
        frame.setLayout(new FlowLayout());
        frame.add(button0);

        outerFrame.setBounds(0, 390, 400, 100);
        outerFrame.setLayout(new FlowLayout());
        outerFrame.add(button1);

        adjustAndShow(new Component[] {frame, win1, win2, outerFrame});
        robot.waitForIdle();

        test();
    }

    void adjustAndShow(Component[] comps) {
        for (Component comp: comps) {
            comp.setLocation(shift, (int)comp.getLocation().getY());
            comp.setVisible(true);
            robot.waitForIdle();
        }
    }

    void test() {
        clickOnCheckFocusOwner(button0);
        clickOnCheckFocusOwner(text1);
        clickOnCheckFocusOwner(button1);
        clickOn(frame);
        checkFocusOwner(text1);
        clickOnCheckFocusOwner(text0);
        clickOnCheckFocusOwner(button1);
        clickOn(frame);
        checkFocusOwner(text0);

        Sysout.println("Test passed.");
    }

    void clickOnCheckFocusOwner(Component c) {
        clickOn(c);
        if (!checkFocusOwner(c)) {
            throw new RuntimeException("Test failed: couldn't focus <" + c + "> by mouse click!");
        }
    }

    boolean checkFocusOwner(Component comp) {
        return (comp == KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
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
        robot.delay(50);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.delay(50);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.waitForIdle();
    }

}

class TestWindow extends Window {
    TestWindow(Window owner, Component comp, int x) {
        super(owner);
        setBackground(Color.blue);
        setLayout(new FlowLayout());
        add(comp);
        comp.setBackground(Color.yellow);
        setBounds(0, x, 100, 100);
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
