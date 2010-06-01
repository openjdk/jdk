/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4453162
  @summary MouseAdapter should implement MouseMotionListener and MouseWheelListener
  @author andrei.dmitriev: area=
  @library ../../regtesthelpers
  @build Util
  @run main MouseAdapterUnitTest
*/

import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.Util;

public class MouseAdapterUnitTest
{
    static Point pt;
    static Frame frame = new Frame("Test Frame");
    static Button b = new Button("Test Button");
    static Robot robot;
    static boolean clicked = false;
    static boolean pressed = false;
    static boolean released = false;
    static boolean entered = false;
    static boolean exited = false;
    static boolean rotated = false;
    static boolean dragged = false;
    static boolean moved = false;

    private static void init()
    {
        String[] instructions =
        {
            "This is an AUTOMATIC test, simply wait until it is done.",
            "The result (passed or failed) will be shown in the",
            "message window below."
        };
        Sysout.createDialog( );
        Sysout.printInstructions( instructions );

        MouseAdapter ma = new MouseAdapter(){
                public void mouseClicked(MouseEvent e) {clicked = true;}

                public void mousePressed(MouseEvent e) { pressed = true;}

                public void mouseReleased(MouseEvent e) {released = true;}

                public void mouseEntered(MouseEvent e) { entered = true;}

                public void mouseExited(MouseEvent e) {exited  = true;}

                public void mouseWheelMoved(MouseWheelEvent e){rotated = true;}

                public void mouseDragged(MouseEvent e){dragged = true;}

                public void mouseMoved(MouseEvent e){moved = true;}

            };

        b.addMouseListener(ma);
        b.addMouseWheelListener(ma);
        b.addMouseMotionListener(ma);

        frame.add(b);
        frame.pack();
        frame.setVisible(true);

        try{
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            Util.waitForIdle(robot);

            pt = b.getLocationOnScreen();
            testPressMouseButton(InputEvent.BUTTON1_MASK);
            testDragMouseButton(InputEvent.BUTTON1_MASK);
            testMoveMouseButton();
            testCrossingMouseButton();
            testWheelMouseButton();
        } catch (Throwable e) {
            throw new RuntimeException("Test failed. Exception thrown: "+e);
        }

        MouseAdapterUnitTest.pass();

    }//End  init()

    public static void testPressMouseButton(int button){
        robot.mouseMove(pt.x + b.getWidth()/2, pt.y + b.getHeight()/2);
        robot.delay(100);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(300);


        if ( !pressed || !released || !clicked ){
            dumpListenerState();
            fail("press, release or click hasn't come");
        }
    }

    public static void testWheelMouseButton(){
        robot.mouseMove(pt.x + b.getWidth()/2, pt.y + b.getHeight()/2);
        robot.mouseWheel(10);
        if ( !rotated){
            dumpListenerState();
            fail("Wheel event hasn't come");
        }
    }

    public static void testDragMouseButton(int button) {
        robot.mouseMove(pt.x + b.getWidth()/2, pt.y + b.getHeight()/2);
        robot.mousePress(button);
        moveMouse(pt.x + b.getWidth()/2, pt.y +
                  b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight());
        robot.mouseRelease(button);

        if ( !dragged){
            dumpListenerState();
            fail("dragged hasn't come");
        }

    }

    public static void testMoveMouseButton() {
        moveMouse(pt.x + b.getWidth()/2, pt.y +
                  b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight());

        if ( !moved){
            dumpListenerState();
            fail("dragged hasn't come");
        }

    }

    public static void moveMouse(int x0, int y0, int x1, int y1){
        int curX = x0;
        int curY = y0;
        int dx = x0 < x1 ? 1 : -1;
        int dy = y0 < y1 ? 1 : -1;

        while (curX != x1){
            curX += dx;
            robot.mouseMove(curX, curY);
        }
        while (curY != y1 ){
            curY += dy;
            robot.mouseMove(curX, curY);
        }
    }

    public static void testCrossingMouseButton() {
        //exit
        moveMouse(pt.x + b.getWidth()/2,
                  pt.y + b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight());
        //enter
        moveMouse(pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + b.getHeight());

        if ( !entered || !exited){
            dumpListenerState();
            fail("enter or exit hasn't come");
        }

    }

    public static void dumpListenerState(){
        System.out.println("pressed = "+pressed);
        System.out.println("released = "+released);
        System.out.println("clicked = "+clicked);
        System.out.println("entered = "+exited);
        System.out.println("rotated = "+rotated);
        System.out.println("dragged = "+dragged);
        System.out.println("moved = "+moved);
    }

    /*****************************************************
     * Standard Test Machinery Section
     * DO NOT modify anything in this section -- it's a
     * standard chunk of code which has all of the
     * synchronisation necessary for the test harness.
     * By keeping it the same in all tests, it is easier
     * to read and understand someone else's test, as
     * well as insuring that all tests behave correctly
     * with the test harness.
     * There is a section following this for test-
     * classes
     ******************************************************/
    private static boolean theTestPassed = false;
    private static boolean testGeneratedInterrupt = false;
    private static String failureMessage = "";

    private static Thread mainThread = null;

    private static int sleepTime = 300000;

    // Not sure about what happens if multiple of this test are
    //  instantiated in the same VM.  Being static (and using
    //  static vars), it aint gonna work.  Not worrying about
    //  it for now.
    public static void main( String args[] ) throws InterruptedException
    {
        mainThread = Thread.currentThread();
        try
        {
            init();
        }
        catch( TestPassedException e )
        {
            //The test passed, so just return from main and harness will
            // interepret this return as a pass
            return;
        }
        //At this point, neither test pass nor test fail has been
        // called -- either would have thrown an exception and ended the
        // test, so we know we have multiple threads.

        //Test involves other threads, so sleep and wait for them to
        // called pass() or fail()
        try
        {
            Thread.sleep( sleepTime );
            //Timed out, so fail the test
            throw new RuntimeException( "Timed out after " + sleepTime/1000 + " seconds" );
        }
        catch (InterruptedException e)
        {
            //The test harness may have interrupted the test.  If so, rethrow the exception
            // so that the harness gets it and deals with it.
            if( ! testGeneratedInterrupt ) throw e;

            //reset flag in case hit this code more than once for some reason (just safety)
            testGeneratedInterrupt = false;

            if ( theTestPassed == false )
            {
                throw new RuntimeException( failureMessage );
            }
        }

    }//main

    public static synchronized void setTimeoutTo( int seconds )
    {
        sleepTime = seconds * 1000;
    }

    public static synchronized void pass()
    {
        Sysout.println( "The test passed." );
        Sysout.println( "The test is over, hit  Ctl-C to stop Java VM" );
        //first check if this is executing in main thread
        if ( mainThread == Thread.currentThread() )
        {
            //Still in the main thread, so set the flag just for kicks,
            // and throw a test passed exception which will be caught
            // and end the test.
            theTestPassed = true;
            throw new TestPassedException();
        }
        theTestPassed = true;
        testGeneratedInterrupt = true;
        mainThread.interrupt();
    }//pass()

    public static synchronized void fail()
    {
        //test writer didn't specify why test failed, so give generic
        fail( "it just plain failed! :-)" );
    }

    public static synchronized void fail( String whyFailed )
    {
        Sysout.println( "The test failed: " + whyFailed );
        Sysout.println( "The test is over, hit  Ctl-C to stop Java VM" );
        //check if this called from main thread
        if ( mainThread == Thread.currentThread() )
        {
            //If main thread, fail now 'cause not sleeping
            throw new RuntimeException( whyFailed );
        }
        theTestPassed = false;
        testGeneratedInterrupt = true;
        failureMessage = whyFailed;
        mainThread.interrupt();
    }//fail()

}// class MouseAdapterUnitTest

//This exception is used to exit from any level of call nesting
// when it's determined that the test has passed, and immediately
// end the test.
class TestPassedException extends RuntimeException
{
}

//*********** End Standard Test Machinery Section **********


//************ Begin classes defined for the test ****************

// if want to make listeners, here is the recommended place for them, then instantiate
//  them in init()

/* Example of a class which may be written as part of a test
class NewClass implements anInterface
 {
   static int newVar = 0;

   public void eventDispatched(AWTEvent e)
    {
      //Counting events to see if we get enough
      eventCount++;

      if( eventCount == 20 )
       {
         //got enough events, so pass

         MouseAdapterUnitTest.pass();
       }
      else if( tries == 20 )
       {
         //tried too many times without getting enough events so fail

         MouseAdapterUnitTest.fail();
       }

    }// eventDispatched()

 }// NewClass class

*/


//************** End classes defined for the test *******************




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
        System.out.println(messageIn);
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
