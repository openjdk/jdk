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
  @bug 4782886
  @summary FocusManager consumes wrong KEYTYPED-Events
  @author son: area=awt.focus
  @run applet WrongKeyTypedConsumedTest.html
*/

/**
 * WrongKeyTypedConsumedTest.java
 *
 * summary: FocusManager consumes wrong KEYTYPED-Events
 */

import java.applet.Applet;

import java.awt.AWTException;
import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;

import java.awt.event.KeyEvent;

import java.util.HashSet;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JTextArea;

public class WrongKeyTypedConsumedTest extends Applet
{
    //Declare things used in the test, like buttons and labels here

    public void init()
    {
        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.

        String[] instructions =
        {
            "This is an AUTOMATIC test",
            "simply wait until it is done"
        };
        Sysout.createDialog( );
        Sysout.printInstructions( instructions );

    }//End  init()

    public void start ()
    {
        //Get things going.  Request focus, set size, et cetera
        setSize (200,200);
        setVisible(true);
        validate();

        JFrame frame = new JFrame("The Frame");
        Set ftk = new HashSet();
        ftk.add(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_DOWN, 0));
        frame.getContentPane().
            setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                                  ftk);

        JCheckBox checkbox = new JCheckBox("test");
        frame.getContentPane().add(checkbox, BorderLayout.NORTH);

        JTextArea textarea = new JTextArea(40, 10);
        frame.getContentPane().add(textarea);

        frame.pack();
        frame.setVisible(true);

        try {
            Robot robot = new Robot();

            // wait for activation
            robot.delay(2000);
            if (!frame.isActive()) {
                Point loc = frame.getLocationOnScreen();
                Dimension size = frame.getSize();
                robot.mouseMove(loc.x + size.width/2,
                                loc.y + size.height/2);
                frame.toFront();
                robot.delay(1000);
                if (!frame.isActive()) {
                    throw new RuntimeException("Test Fialed: frame isn't active");
                }
            }

            // verify if checkbox has focus
            if (!checkbox.isFocusOwner()) {
                checkbox.requestFocusInWindow();
                robot.delay(1000);
                if (!checkbox.isFocusOwner()) {
                    throw new RuntimeException("Test Failed: checkbox doesn't have focus");
                }
            }
            // press VK_DOWN
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.delay(250);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.delay(1000);

            // verify if text area has focus
            if (!textarea.isFocusOwner()) {
                throw new RuntimeException("Test Failed: focus wasn't transfered to text area");
            }
            // press '1'
            robot.keyPress(KeyEvent.VK_1);
            robot.delay(250);
            robot.keyRelease(KeyEvent.VK_1);
            robot.delay(1000);

            // verify if KEY_TYPED arraived
            if (!"1".equals(textarea.getText())) {
                throw new RuntimeException("Test Failed: text area text is \"" + textarea.getText() + "\", not \"1\"");
            }
        } catch(AWTException e) {
            e.printStackTrace();
            throw new RuntimeException("Test failed because of some internal exception");
        }
        Sysout.println("Test Passed");
    }// start()

}// class WrongKeyTypedConsumedTest

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
