/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
  @bug       6496958
  @summary   Tests that breaking the proccess of clearing LW requests doesn't break focus.
  @author    anton.tarasov@...: area=awt-focus
  @library    ../../regtesthelpers
  @build      Util
  @run       main ClearLwQueueBreakTest
*/

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.applet.Applet;
import test.java.awt.regtesthelpers.Util;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClearLwQueueBreakTest extends Applet {
    JFrame f1 = new JFrame("frame");
    JFrame f2 = new JFrame("frame");
    JButton b = new JButton("button");
    JTextField tf1 = new JTextField("     ");
    JTextField tf2 = new JTextField("     ");
    JTextField tf3 = new JTextField("     ");
    AtomicBoolean typed = new AtomicBoolean(false);
    FocusListener listener1;
    FocusListener listener2;

    Robot robot;

    public static void main(String[] args) {
        ClearLwQueueBreakTest app = new ClearLwQueueBreakTest();
        app.init();
        app.start();
    }

    public void init() {
        robot = Util.createRobot();

        // Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());
        Sysout.createDialogWithInstructions(new String[]
            {"This is an automatic test. Simply wait until it is done."
            });
    }

    public void start() {
        b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    f2.setVisible(true);
                }
            });
        tf2.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    if (e.getKeyChar() == '9') {
                        synchronized (typed) {
                            typed.set(true);
                            typed.notifyAll();
                        }
                    }
                }
            });
        tf3.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    if (e.getKeyChar() == '8') {
                        synchronized (typed) {
                            typed.set(true);
                            typed.notifyAll();
                        }
                    }
                }
            });

        listener1 = new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    b.requestFocus();
                    tf1.requestFocus();
                    tf1.setFocusable(false);
                    tf2.requestFocus();
                }
            };

        listener2 = new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    b.requestFocus();
                    tf1.requestFocus();
                    tf2.requestFocus();
                    tf2.setFocusable(false);
                }
            };

        f1.add(b);
        f1.add(tf1);
        f1.add(tf2);
        f1.add(tf3);
        f1.setLayout(new FlowLayout());
        f1.pack();
        f1.setVisible(true);
        Util.waitForIdle(robot);

        /*
         * Break the sequence of LW requests in the middle.
         * Test that the last request succeeds
         */
        f2.addFocusListener(listener1);
        Sysout.println("Stage 1.");
        test1();


        /*
         * Break the last LW request.
         * Test that focus is restored correctly.
         */
        f2.removeFocusListener(listener1);
        f2.addFocusListener(listener2);
        Sysout.println("Stage 2.");
        test2();

        Sysout.println("Test passed.");
    }

    void test1() {
        Util.clickOnComp(b, robot);
        Util.waitForIdle(robot);

        if (!tf2.hasFocus()) {
            throw new TestFailedException("target component didn't get focus!");
        }

        robot.keyPress(KeyEvent.VK_9);
        robot.delay(50);
        robot.keyRelease(KeyEvent.VK_9);

        synchronized (typed) {
            if (!Util.waitForCondition(typed, 2000)) {
                throw new TestFailedException("key char couldn't be typed!");
            }
        }

        Util.clickOnComp(tf3, robot);
        Util.waitForIdle(robot);

        if (!tf3.hasFocus()) {
            throw new Error("a text field couldn't be focused.");
        }

        typed.set(false);
        robot.keyPress(KeyEvent.VK_8);
        robot.delay(50);
        robot.keyRelease(KeyEvent.VK_8);

        synchronized (typed) {
            if (!Util.waitForCondition(typed, 2000)) {
                throw new TestFailedException("key char couldn't be typed!");
            }
        }
    }

    void test2() {
        Util.clickOnComp(b, robot);
        Util.waitForIdle(robot);

        if (!b.hasFocus()) {
            throw new TestFailedException("focus wasn't restored correctly!");
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
//        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }

    public static void createDialog( )
    {
        dialog = new TestDialog( new Frame(), "Instructions" );
        String[] defInstr = { "Instructions will appear here. ", "" } ;
        dialog.printInstructions( defInstr );
//        dialog.setVisible(true);
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

//        setVisible(true);
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
