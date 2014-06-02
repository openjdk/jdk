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
  @bug 4870762
  @summary tests that a drop target JVM doesn't crash if the source doesn't export
           data in native formats.
  @author das@sparc.spb.su area=dnd
  @compile NoFormatsCrashTest.java
  @run applet NoFormatsCrashTest.html
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
// Note also the 'NoFormatsCrashTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * NoFormatsCrashTest.java
 *
 * summary: tests that a drop target JVM doesn't crash if the source doesn't export
 *          data in native formats.
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;


//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class NoFormatsCrashTest extends Applet {

    final Frame frame = new Frame();
    private volatile Process process;

    static final int FRAME_ACTIVATION_TIMEOUT = 2000;

    public static void main(String[] args) {
        NoFormatsCrashTest test = new NoFormatsCrashTest();
        test.run(args);
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

            Panel panel = new DragSourcePanel();

            frame.setTitle("Drag source frame");
            frame.setLocation(500, 200);
            frame.add(panel);
            frame.pack();
            frame.setVisible(true);

            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            Point sourcePoint = panel.getLocationOnScreen();
            Dimension d = panel.getSize();
            sourcePoint.translate(d.width / 2, d.height / 2);

            Point targetPoint = new Point(x + w / 2, y + h / 2);

            Robot robot = new Robot();
            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (; !sourcePoint.equals(targetPoint);
                 sourcePoint.translate(sign(targetPoint.x - sourcePoint.x),
                                       sign(targetPoint.y - sourcePoint.y))) {
                robot.mouseMove(sourcePoint.x, sourcePoint.y);
                Thread.sleep(50);
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            if (process.isAlive()) {
                process.destroy();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    } // run()

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

        frame.setTitle("Drop target frame");
        frame.setLocation(200, 200);

    } // init()

    public void start() {
        DropTargetPanel panel = new DropTargetPanel();
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);

        try {
            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            Point p = frame.getLocationOnScreen();
            Dimension d = frame.getSize();

            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " + System.getProperty("test.classes", ".") +
                " NoFormatsCrashTest " +
                p.x + " " + p.y + " " + d.width + " " + d.height;

            process = Runtime.getRuntime().exec(command);
            ProcessResults pres = ProcessResults.doWaitFor(process);
            System.err.println("Child VM return code: " + pres.exitValue);

            if (pres.stderr != null && pres.stderr.length() > 0) {
                System.err.println("========= Child VM System.err ========");
                System.err.print(pres.stderr);
                System.err.println("======================================");
            }

            if (pres.stdout != null && pres.stdout.length() > 0) {
                System.err.println("========= Child VM System.out ========");
                System.err.print(pres.stdout);
                System.err.println("======================================");
            }

        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        if (panel.isTestFailed()) {
            throw new RuntimeException();
        }
    } // start()

    public static int sign(int n) {
        return n < 0 ? -1 : n > 0 ? 1 : 0;
    }
} // class NoFormatsCrashTest

class TestTransferable implements Transferable {

    public static DataFlavor dataFlavor = null;
    static final Object data = new Object();

    static {
        DataFlavor df = null;
        try {
            df = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType +
                                "; class=java.lang.Object");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
        dataFlavor = df;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { dataFlavor };
    }

    public boolean isDataFlavorSupported(DataFlavor df) {
        return dataFlavor.equals(df);
    }

    public Object getTransferData(DataFlavor df)
      throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(df)) {
            throw new UnsupportedFlavorException(df);
        }
        return data;
    }
}

class DragSourcePanel extends Panel {
    public DragSourcePanel() {
        final Transferable t = new TestTransferable();
        final DragSourceListener dsl = new DragSourceAdapter() {
                public void dragDropEnd(DragSourceDropEvent dtde) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // This finishes child VM
                    System.exit(0);
                }
            };
        final DragGestureListener dgl = new DragGestureListener() {
                public void dragGestureRecognized(DragGestureEvent dge) {
                    dge.startDrag(null, t, dsl);
                }
            };
        final DragSource ds = DragSource.getDefaultDragSource();
        final DragGestureRecognizer dgr =
            ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                                                  dgl);
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }
}

class DropTargetPanel extends Panel {
    private boolean testFailed = false;
    public DropTargetPanel() {
        final DropTargetListener dtl = new DropTargetAdapter() {
                public void dragOver(DropTargetDragEvent dtde) {
                    try {
                        dtde.getCurrentDataFlavorsAsList();
                    } catch (Exception e) {
                        testFailed = true;
                        e.printStackTrace();
                    }
                }
                public void drop(DropTargetDropEvent dtde) {
                    dtde.rejectDrop();
                }
            };
        final DropTarget dt = new DropTarget(this, dtl);
    }

    public boolean isTestFailed() {
        return testFailed;
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }
}

class ProcessResults {
    public int exitValue;
    public String stdout;
    public String stderr;

    public ProcessResults() {
        exitValue = -1;
        stdout = "";
        stderr = "";
    }

    /**
     * Method to perform a "wait" for a process and return its exit value.
     * This is a workaround for <code>Process.waitFor()</code> never returning.
     */
    public static ProcessResults doWaitFor(Process p) {
        ProcessResults pres = new ProcessResults();

        InputStream in = null;
        InputStream err = null;

        try {
            in = p.getInputStream();
            err = p.getErrorStream();

            boolean finished = false;

            while (!finished) {
                try {
                    while (in.available() > 0) {
                        pres.stdout += (char)in.read();
                    }
                    while (err.available() > 0) {
                        pres.stderr += (char)err.read();
                    }
                    // Ask the process for its exitValue. If the process
                    // is not finished, an IllegalThreadStateException
                    // is thrown. If it is finished, we fall through and
                    // the variable finished is set to true.
                    pres.exitValue = p.exitValue();
                    finished  = true;
                }
                catch (IllegalThreadStateException e) {
                    // Process is not finished yet;
                    // Sleep a little to save on CPU cycles
                    Thread.currentThread().sleep(500);
                }
            }
            if (in != null) in.close();
            if (err != null) err.close();
        }
        catch (Throwable e) {
            System.err.println("doWaitFor(): unexpected exception");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return pres;
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
