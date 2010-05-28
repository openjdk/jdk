/*
 * Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6391770
  @summary Content of the Window should be laid out in the area left after WarningWindow was added.
  @author yuri nesterenko: area=
  @run applet WindowWithWarningTest.html
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
// Note also the 'AutomaticAppletTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * WindowWithWarningTest.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class WindowWithWarningTest extends Applet
{
    //Declare things used in the test, like buttons and labels here
    boolean buttonClicked = false;
    public static final int MAX_COUNT = 100;

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
        //Sysout.createDialog( );
        //Sysout.printInstructions( instructions );

    }//End  init()
    public void start ()
    {
        //Get things going.  Request focus, set size, et cetera
        System.setSecurityManager( new SecurityManager() {
        // deny AWTPermission("showWindowWithoutWarningBanner")
            public boolean checkTopLevelWindow(Object window) {
                return false;
            }
         });
        JFrame frame = new JFrame("Window Test");
        frame.setBounds(50, 50, 200, 200);
        frame.show();

        JWindow window = new JWindow( frame );
        JButton jbutton1 = new JButton( "First" );
        jbutton1.addMouseListener( new MouseAdapter() {
            public void mousePressed( MouseEvent me ) {
                buttonClicked = true;
            }
         });
        JButton jbutton2 = new JButton( "Second" );
        window.setLocation( 300, 300 );

        window.add("North", jbutton1);
        window.add("South", jbutton2);

        window.pack();
        window.show();
        //wait for frame to show:
        getLocation( frame );
        window.toFront();

        Dimension size0 = window.getSize();
        Dimension size1 = null;
        try {
            Robot robot = new Robot();

            robot.delay(500);
            window.pack();
            robot.delay(500);
            window.pack();
            // size1 must be the same as size0
            size1 = window.getSize();
            robot.delay(500);
            Point pt = jbutton1.getLocationOnScreen();
            robot.mouseMove((int) jbutton1.getLocationOnScreen().x + jbutton1.getWidth() / 2,
                            (int) jbutton1.getLocationOnScreen().y + jbutton1.getHeight() / 2);
            robot.delay(500);
            robot.mousePress(MouseEvent.BUTTON1_MASK);
            robot.delay(100);
            robot.mouseRelease(MouseEvent.BUTTON1_MASK);
            robot.delay(2000);
         }catch(Exception e) {
            throw new RuntimeException( "Exception "+e );
         }
         if( !size0.equals(size1) ) {
            throw new RuntimeException( "Wrong Window size after multiple pack()s");
         }
         if( !buttonClicked ) {
            throw new RuntimeException( "Button was not clicked");
         }
         window.dispose();
         frame.dispose();

         System.out.println("Test Passed.");
    }// start()
    public static Point getLocation( Component co ) throws RuntimeException {
       Point pt = null;
       boolean bFound = false;
       int count = 0;
       while( !bFound ) {
          try {
             pt = co.getLocationOnScreen();
             bFound = true;
          }catch( Exception ex ) {
             bFound = false;
             count++;
          }
          if( !bFound && count > MAX_COUNT ) {
             throw new RuntimeException("don't see a component to get location");
          }
       }
       return pt;
    }


}// class AutomaticAppletTest


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
