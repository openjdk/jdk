/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
  @bug       6426132
  @summary   Modal blocked window shouldn't steal focus when shown, or brought to front.
  @author    anton.tarasov@...: area=awt.focus
  @run       applet ModalBlockedStealsFocusTest.html
*/

import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;
import sun.awt.SunToolkit;
import test.java.awt.regtesthelpers.Util;

public class ModalBlockedStealsFocusTest extends Applet {
    SunToolkit toolkit = (SunToolkit)Toolkit.getDefaultToolkit();
    Frame frame = new Frame("Blocked Frame");
    Dialog dialog = new Dialog(frame, "Modal Dialog", Dialog.ModalityType.TOOLKIT_MODAL);
    AtomicBoolean lostFocus = new AtomicBoolean(false);

    public static void main(String[] args) {
        ModalBlockedStealsFocusTest app = new ModalBlockedStealsFocusTest();
        app.init();
        app.start();
    }

    public void init() {
        // Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());
        Sysout.createDialogWithInstructions(new String[]
            {"This is an automatic test. Simply wait until it is done."
            });
    }

    public void start() {
        if ("sun.awt.motif.MToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) {
            Sysout.println("The test is not for MToolkit.");
            return;
        }

        dialog.setBounds(800, 0, 200, 100);
        frame.setBounds(800, 150, 200, 100);

        dialog.addWindowFocusListener(new WindowAdapter() {
                public void windowLostFocus(WindowEvent e) {
                    Sysout.println(e.toString());
                    synchronized (lostFocus) {
                        lostFocus.set(true);
                        lostFocus.notifyAll();
                    }
                }
            });

        new Thread(new Runnable() {
                public void run() {
                    dialog.setVisible(true);
                }
            }).start();

        Util.waitTillShown(dialog);
        toolkit.realSync();

        // Test 1. Show a modal blocked frame, check that it doesn't steal focus.

        frame.setVisible(true);

        if (Util.waitForCondition(lostFocus, 2000L)) {
            throw new TestFailedException("the modal blocked frame stole focus on its showing!");
        }

        // Test 2. Brought a modal blocked frame to front, check that it doesn't steal focus.

        frame.toFront();

        if (Util.waitForCondition(lostFocus, 2000L)) {
            throw new TestFailedException("the modal blocked frame stole focus on its bringing to front!");
        } else {
            Sysout.println("Test passed");
        }
    }
}

class TestFailedException extends RuntimeException {
    TestFailedException(String msg) {
        super("Test failed: " + msg);
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
