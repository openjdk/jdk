/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
  @bug       6391688
  @summary   Tests that next mnemonic KeyTyped is consumed for a modal dialog.
  @author    anton.tarasov@sun.com: area=awt.focus
  @run       applet ConsumeForModalDialogTest.html
*/

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;

public class ConsumeForModalDialogTest extends Applet {
    Robot robot;
    JFrame frame = new JFrame("Test Frame");
    JDialog dialog = new JDialog((Window)null, "Test Dialog", Dialog.ModalityType.DOCUMENT_MODAL);
    JTextField text = new JTextField();
    static boolean passed = true;

    public static void main(String[] args) {
        ConsumeForModalDialogTest app = new ConsumeForModalDialogTest();
        app.init();
        app.start();
    }

    public void init() {
        try {
            robot = new Robot();
            robot.setAutoDelay(50);
        } catch (AWTException e) {
            throw new RuntimeException("Error: unable to create robot", e);
        }
        // Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());
        Sysout.createDialogWithInstructions(new String[]
            {"This is automatic test. Simply wait until it is done."
            });
    }

    public void start() {

        text.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    Sysout.println(e.toString());
                    passed = false;
                }
            });

        JMenuItem testItem = new JMenuItem();
        testItem.setMnemonic('s');
        testItem.setText("Test");

        testItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    dialog.setVisible(true);
            }
        });

        JMenu menu = new JMenu();
        menu.setMnemonic('f');
        menu.setText("File");
        menu.add(testItem);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);

        dialog.setSize(100, 100);
        dialog.add(text);

        frame.setJMenuBar(menuBar);
        frame.setSize(100, 100);
        frame.setVisible(true);

        robot.waitForIdle();

        if (!frame.isFocusOwner()) {
            Point loc = frame.getLocationOnScreen();
            Dimension size = frame.getSize();
            robot.mouseMove(loc.x + size.width/2, loc.y + size.height/2);
            robot.delay(10);
            robot.mousePress(MouseEvent.BUTTON1_MASK);
            robot.delay(10);
            robot.mouseRelease(MouseEvent.BUTTON1_MASK);

            robot.waitForIdle();

            int iter = 10;
            while (!frame.isFocusOwner() && iter-- > 0) {
                robot.delay(200);
            }
            if (iter <= 0) {
                Sysout.println("Test: the frame couldn't be focused!");
                return;
            }
        }

        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_F);
        robot.delay(10);
        robot.keyRelease(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_ALT);

        robot.waitForIdle();

        robot.keyPress(KeyEvent.VK_S);
        robot.delay(10);
        robot.keyRelease(KeyEvent.VK_S);

        robot.delay(1000);

        if (passed) {
            Sysout.println("Test passed.");
        } else {
            throw new RuntimeException("Test failed! Enexpected KeyTyped came into the JTextField.");
        }
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
