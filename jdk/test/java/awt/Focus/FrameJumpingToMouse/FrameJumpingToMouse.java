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
test
@bug 4752312
@summary Tests that after moving non-focusable window it ungrabs mouse pointer
@author dom@sparc.spb.su: area=awt.focus
@run applet FrameJumpingToMouse.html
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
// Note also the 'FrameJumpingToMouse.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * FrameJumpingToMouse.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import javax.swing.JFrame;

//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class FrameJumpingToMouse extends Applet
{
    //Declare things used in the test, like buttons and labels here
    JFrame frame = new JFrame("Test jumping frame");
    Robot robot = null;
    public void init()
    {
        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.

        this.setLayout (new BorderLayout ());

        frame.setFocusableWindowState(false);
        frame.setBounds(100, 100, 100, 100);
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
            throw new RuntimeException("Can't create robot");
        }

        frame.setVisible(true);

        robot.delay(1000);

        Point frameLoc = frame.getLocationOnScreen();
        robot.mouseMove(frameLoc.x+frame.getWidth()/4, frameLoc.y+frame.getInsets().top/2);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseMove(frameLoc.x+100, frameLoc.y+50);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        Toolkit.getDefaultToolkit().sync();
        robot.waitForIdle();
        frameLoc = frame.getLocation();

        robot.mouseMove(frameLoc.x+frame.getWidth()/2, frameLoc.y+frame.getHeight()/2);

        Toolkit.getDefaultToolkit().sync();
        robot.waitForIdle();

        if (!(frame.getLocation().equals(frameLoc))) {
            throw new RuntimeException("Frame is moving to mouse with grab");
        }
    }// start()

}// class FrameJumpingToMouse


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

        show();
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
