/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
  @bug       6271849
  @summary   Tests that component in modal excluded Window which parent is blocked responses to mouse clicks.
  @author    anton.tarasov@sun.com: area=awt.focus
  @run       applet ModalExcludedWindowClickTest.html
*/

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;

public class ModalExcludedWindowClickTest extends Applet {
    Robot robot;
    Frame frame = new Frame("Frame");
    Window w = new Window(frame);
    Dialog d = new Dialog ((Dialog)null, "NullParentDialog", true);
    Button button = new Button("Button");
    boolean actionPerformed = false;

    public static void main (String args[]) {
        ModalExcludedWindowClickTest app = new ModalExcludedWindowClickTest();
        app.init();
        app.start();
    }

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
    }

    public void start() {

        if ("sun.awt.motif.MToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) {
            Sysout.println("No testing on MToolkit.");
            return;
        }

        button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    actionPerformed = true;
                    Sysout.println(e.paramString());
                }
            });

        EventQueue.invokeLater(new Runnable() {
                public void run() {
                    frame.setSize(200, 200);
                    frame.setVisible(true);

                    w.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
                    w.add(button);
                    w.setSize(200, 200);
                    w.setLocation(230, 230);
                    w.setVisible(true);

                    d.setSize(200, 200);
                    d.setLocation(0, 230);
                    d.setVisible(true);

                }
            });

        waitTillShown(d);

        test();
    }

    void test() {
        clickOn(button);
        waitForIdle();
        if (!actionPerformed) {
            throw new RuntimeException("Test failed!");
        }
        Sysout.println("Test passed.");
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
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        waitForIdle();
    }
    void waitTillShown(Component c) {
        while (true) {
            try {
                Thread.sleep(100);
                c.getLocationOnScreen();
                break;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (IllegalComponentStateException e) {}
        }
    }
    void waitForIdle() {
        try {
            robot.waitForIdle();
            EventQueue.invokeAndWait( new Runnable() {
                    public void run() {} // Dummy implementation
                });
        } catch(InterruptedException ie) {
            Sysout.println("waitForIdle, non-fatal exception caught:");
            ie.printStackTrace();
        } catch(InvocationTargetException ite) {
            Sysout.println("waitForIdle, non-fatal exception caught:");
            ite.printStackTrace();
        }

        // wait longer...
        robot.delay(200);
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
