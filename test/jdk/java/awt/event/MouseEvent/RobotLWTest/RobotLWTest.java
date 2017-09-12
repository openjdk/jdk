/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4350402
  @summary Tests that mouse behavior on LW component
*/

/**
 * RobotLWTest.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.Util;

public class RobotLWTest extends Applet
{
    //Declare things used in the test, like buttons and labels here

    public void init()
    {
    }//End  init()

    public void start ()
    {
        //What would normally go into main() will probably go here.
        //Use System.out.println for diagnostic messages that you want
        //to read after the test is done.
        //Use Sysout.println for messages you want the tester to read.
        Frame frame = new Frame();
        MyLWContainer c = new MyLWContainer();
        MyLWComponent b = new MyLWComponent();
        c.add(b);
        frame.add(c);
        frame.setSize(400,400);
        frame.setVisible(true);

        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(100);
            robot.waitForIdle();

            Util.waitForIdle(robot);

            Point pt = frame.getLocationOnScreen();
            Point pt1 = b.getLocationOnScreen();

            //Testing capture with multiple buttons
            robot.mouseMove(pt1.x + b.getWidth()/2, pt1.y + b.getHeight()/2);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.mousePress(InputEvent.BUTTON2_MASK);
            robot.mouseMove(pt.x + frame.getWidth()+10, pt.y + frame.getHeight()+10);
            robot.mouseRelease(InputEvent.BUTTON2_MASK);
            Util.waitForIdle(robot);

            b.last = null;
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            Util.waitForIdle(robot);

            if (b.last == null) {
                throw new RuntimeException("RobotLWTest failed. Mouse Capture failed");
            }
            //Enter/Exit
            b.last = null;
            robot.mouseMove(pt1.x + b.getWidth()/2, pt1.y + b.getHeight()/2);
            Util.waitForIdle(robot);

            if (b.last == null || b.last.getID() != MouseEvent.MOUSE_ENTERED) {
                throw new RuntimeException("RobotLWTest failed. Enter/Exit failed");
            }
            b.last = b.prev = null;
            robot.mousePress(InputEvent.BUTTON1_MASK);
            Util.waitForIdle(robot);

            if (b.prev != null && b.prev.getID() == MouseEvent.MOUSE_ENTERED) {
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                throw new RuntimeException("RobotLWTest failed. Enter/Exit failed");
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);

        } catch (Exception e) {
            throw new RuntimeException("The test was not completed.", e);
        }
    }// start()

}// class RobotLWTest

class MyLWContainer extends Container {
    public MouseEvent last = null;
    public MouseEvent prev = null;

    MyLWContainer() {
        enableEvents(MouseEvent.MOUSE_MOTION_EVENT_MASK);
    }

    public void processMouseEvent(MouseEvent e) {
        prev = last;
        last = e;
        System.out.println(e.toString());
        super.processMouseEvent(e);
    }
}

class MyLWComponent extends Component {
    public MouseEvent last = null;
    public MouseEvent prev = null;

    MyLWComponent() {
        setSize(50,30);
        enableEvents(MouseEvent.MOUSE_EVENT_MASK);
    }

    public void processMouseEvent(MouseEvent e) {
        prev = last;
        last = e;
        System.out.println(e.toString());
        super.processMouseEvent(e);
    }

    public void paint(Graphics g) {
        Dimension d = getSize();
        setBackground(isEnabled() ? Color.red : Color.gray);
        g.clearRect(0, 0, d.width - 1, d.height -1);
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
      dialog.show();
      println( "Any messages for the tester will display here." );
    }

   public static void createDialog( )
    {
      dialog = new TestDialog( new Frame(), "Instructions" );
      String[] defInstr = { "Instructions will appear here. ", "" } ;
      dialog.printInstructions( defInstr );
      dialog.show();
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
      add("South", messageText);

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
    }

 }// TestDialog  class
