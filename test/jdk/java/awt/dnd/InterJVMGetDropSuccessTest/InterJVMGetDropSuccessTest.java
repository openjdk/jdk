/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4658741
  @summary verifies that getDropSuccess() returns correct value for inter-JVM DnD
  @author das@sparc.spb.su area=dnd
  @run applet InterJVMGetDropSuccessTest.html
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
// Note also the 'InterJVMGetDropSuccessTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * InterJVMGetDropSuccessTest.java
 *
 * summary: verifies that getDropSuccess() returns correct value for inter-JVM DnD
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
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


public class InterJVMGetDropSuccessTest extends Applet {

    private int returnCode = Util.CODE_NOT_RETURNED;
    private boolean successCodes[] = { true, false };
    private int dropCount = 0;

    final Frame frame = new Frame("Target Frame");

    final DropTargetListener dropTargetListener = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                dtde.dropComplete(successCodes[dropCount]);
                dropCount++;
            }
        };
    final DropTarget dropTarget = new DropTarget(frame, dropTargetListener);

    public void init() {
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

        frame.setTitle("Test frame");
        frame.setBounds(100, 100, 150, 150);
    } // init()

    public void start() {

        frame.setVisible(true);

        try {
            Thread.sleep(Util.FRAME_ACTIVATION_TIMEOUT);

            Point p = frame.getLocationOnScreen();
            Dimension d = frame.getSize();

            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " + System.getProperty("test.classes", ".") +
                " Child " +
                p.x + " " + p.y + " " + d.width + " " + d.height;

            Process process = Runtime.getRuntime().exec(command);
            returnCode = process.waitFor();

            InputStream errorStream = process.getErrorStream();
            int count = errorStream.available();
            if (count > 0) {
                byte[] b = new byte[count];
                errorStream.read(b);
                System.err.println("========= Child VM System.err ========");
                System.err.print(new String(b));
                System.err.println("======================================");
            }

            InputStream outputStream = process.getInputStream();
            count = outputStream.available();
            if (count > 0) {
                byte[] b = new byte[count];
                outputStream.read(b);
                System.err.println("========= Child VM System.out ========");
                System.err.print(new String(b));
                System.err.println("======================================");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        switch (returnCode) {
        case Util.CODE_NOT_RETURNED:
            throw new RuntimeException("Child VM: failed to start");
        case Util.CODE_FAILURE:
            throw new RuntimeException("Child VM: abnormal termination");
        default:
            if (dropCount == 2) {
                int expectedRetCode = 0;
                if (successCodes[0]) {
                    expectedRetCode |= Util.CODE_FIRST_SUCCESS;
                }
                if (successCodes[1]) {
                    expectedRetCode |= Util.CODE_SECOND_SUCCESS;
                }
                if (expectedRetCode != returnCode) {
                    throw new RuntimeException("The test failed. Expected:" +
                                               expectedRetCode + ". Returned:" +
                                               returnCode);
                }
            }
            break;
        }
    } // start()
} // class InterJVMGetDropSuccessTest

final class Util implements AWTEventListener {
    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_FIRST_SUCCESS = 0x2;
    public static final int CODE_SECOND_SUCCESS = 0x2;
    public static final int CODE_FAILURE = 0x1;

    public static final int FRAME_ACTIVATION_TIMEOUT = 3000;

    static final Object SYNC_LOCK = new Object();
    static final int MOUSE_RELEASE_TIMEOUT = 1000;

    static final Util theInstance = new Util();

    static {
        Toolkit.getDefaultToolkit().addAWTEventListener(theInstance, AWTEvent.MOUSE_EVENT_MASK);
    }

    public static Point getCenterLocationOnScreen(Component c) {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();
        p.translate(d.width / 2, d.height / 2);
        return p;
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    private Component clickedComponent = null;

    private void reset() {
        clickedComponent = null;
    }

    public void eventDispatched(AWTEvent e) {
        if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            clickedComponent = (Component)e.getSource();
            synchronized (SYNC_LOCK) {
                SYNC_LOCK.notifyAll();
            }
        }
    }

    public static boolean pointInComponent(Robot robot, Point p, Component comp)
      throws InterruptedException {
        return theInstance.pointInComponentImpl(robot, p, comp);
    }

    private boolean pointInComponentImpl(Robot robot, Point p, Component comp)
      throws InterruptedException {
        robot.waitForIdle();
        reset();
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        synchronized (SYNC_LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            SYNC_LOCK.wait(MOUSE_RELEASE_TIMEOUT);
        }

        Component c = clickedComponent;

        while (c != null && c != comp) {
            c = c.getParent();
        }

        return c == comp;
    }
}

class Child {
    static class DragSourceDropListener extends DragSourceAdapter {
        private boolean finished = false;
        private boolean dropSuccess = false;

        public void reset() {
            finished = false;
            dropSuccess = false;
        }

        public boolean isDropFinished() {
            return finished;
        }

        public boolean getDropSuccess() {
            return dropSuccess;
        }

        public void dragDropEnd(DragSourceDropEvent dsde) {
            finished = true;
            dropSuccess = dsde.getDropSuccess();
            synchronized (Util.SYNC_LOCK) {
                Util.SYNC_LOCK.notifyAll();
            }
        }
    }

    final Frame frame = new Frame("Source Frame");
    final DragSource dragSource = DragSource.getDefaultDragSource();
    final DragSourceDropListener dragSourceListener = new DragSourceDropListener();
    final Transferable transferable = new StringSelection("TEXT");
    final DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, transferable, dragSourceListener);
            }
        };
    final DragGestureRecognizer dragGestureRecognizer =
        dragSource.createDefaultDragGestureRecognizer(frame, DnDConstants.ACTION_COPY,
                                                      dragGestureListener);

    public static void main(String[] args) {
        Child child = new Child();
        child.run(args);
    }

    public void run(String[] args) {
        try {
            if (args.length != 4) {
                throw new RuntimeException("Incorrect command line arguments.");
            }

            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int w = Integer.parseInt(args[2]);
            int h = Integer.parseInt(args[3]);

            frame.setBounds(300, 200, 150, 150);
            frame.setVisible(true);

            Thread.sleep(Util.FRAME_ACTIVATION_TIMEOUT);

            Point sourcePoint = Util.getCenterLocationOnScreen(frame);

            Point targetPoint = new Point(x + w / 2, y + h / 2);

            Robot robot = new Robot();
            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (Point p = new Point(sourcePoint); !p.equals(targetPoint);
                 p.translate(Util.sign(targetPoint.x - p.x),
                             Util.sign(targetPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
                Thread.sleep(50);
            }

            synchronized (Util.SYNC_LOCK) {
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                Util.SYNC_LOCK.wait(Util.FRAME_ACTIVATION_TIMEOUT);
            }

            if (!dragSourceListener.isDropFinished()) {
                throw new RuntimeException("Drop not finished");
            }

            boolean success1 = dragSourceListener.getDropSuccess();

            dragSourceListener.reset();
            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (Point p = new Point(sourcePoint); !p.equals(targetPoint);
                 p.translate(Util.sign(targetPoint.x - p.x),
                             Util.sign(targetPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
                Thread.sleep(50);
            }

            synchronized (Util.SYNC_LOCK) {
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                Util.SYNC_LOCK.wait(Util.FRAME_ACTIVATION_TIMEOUT);
            }

            if (!dragSourceListener.isDropFinished()) {
                throw new RuntimeException("Drop not finished");
            }

            boolean success2 = dragSourceListener.getDropSuccess();
            int retCode = 0;

            if (success1) {
                retCode |= Util.CODE_FIRST_SUCCESS;
            }
            if (success2) {
                retCode |= Util.CODE_SECOND_SUCCESS;
            }
            // This returns the diagnostic code from the child VM
            System.exit(retCode);
        } catch (Throwable e) {
            e.printStackTrace();
            // This returns the diagnostic code from the child VM
            System.exit(Util.CODE_FAILURE);
        }
    } // run()
} // class child

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
