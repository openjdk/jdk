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
  @bug       6346690
  @summary   Tests that key_typed is consumed after mnemonic key_pressed is handled for a menu item.
  @author    anton.tarasov@sun.com: area=awt-focus
  @library   ../../../../lib/testlibrary
  @build jdk.testlibrary.OSInfo
  @run       applet ConsumeNextMnemonicKeyTypedTest.html
*/

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.applet.Applet;


public class ConsumeNextMnemonicKeyTypedTest extends Applet {
    Robot robot;
    JFrame frame = new JFrame("Test Frame");
    JTextField text = new JTextField();
    JMenuBar bar = new JMenuBar();
    JMenu menu = new JMenu("Menu");
    JMenuItem item = new JMenuItem("item");

    public static void main(String[] args) {
        ConsumeNextMnemonicKeyTypedTest app = new ConsumeNextMnemonicKeyTypedTest();
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
            {"Automatic test. Simply wait until it's done."});
    }

    public void start() {
        menu.setMnemonic('f');
        item.setMnemonic('i');
        menu.add(item);
        bar.add(menu);

        frame.add(text);
        frame.setJMenuBar(bar);
        frame.pack();

        frame.setLocation(800, 0);
        frame.setVisible(true);

        test();
    }

    void test() {

        robot.waitForIdle();

        if (!text.isFocusOwner()) {
            robot.mouseMove(text.getLocationOnScreen().x + 5, text.getLocationOnScreen().y + 5);
            robot.delay(100);
            robot.mousePress(MouseEvent.BUTTON1_MASK);
            robot.delay(100);
            robot.mouseRelease(MouseEvent.BUTTON1_MASK);

            int iter = 10;
            while (!text.isFocusOwner() && iter-- > 0) {
                robot.delay(200);
            }
            if (iter <= 0) {
                Sysout.println("Test: text field couldn't be focused!");
                return;
            }
        }

        robot.keyPress(KeyEvent.VK_A);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_A);

        robot.waitForIdle();

        String charA = text.getText();
        System.err.println("Test: character typed with VK_A: " + charA);

        robot.keyPress(KeyEvent.VK_BACK_SPACE);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_BACK_SPACE);

        robot.waitForIdle();

        if (jdk.testlibrary.OSInfo.getOSType() == jdk.testlibrary.OSInfo.OSType.MACOSX) {
            robot.keyPress(KeyEvent.VK_CONTROL);
        }
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_F);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_ALT);
        if (jdk.testlibrary.OSInfo.getOSType() == jdk.testlibrary.OSInfo.OSType.MACOSX) {
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }

        robot.waitForIdle();

        String string = text.getText();

        robot.keyPress(KeyEvent.VK_I);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_I);

        robot.waitForIdle();

        Sysout.println("Test: character typed after mnemonic key press: " + text.getText());

        if (!text.getText().equals(string)) {
            throw new RuntimeException("Test failed!");
        }

        robot.keyPress(KeyEvent.VK_A);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_A);

        robot.waitForIdle();

        System.err.println("Test: chracter typed with VK_A: " + text.getText());

        if (!charA.equals(text.getText())) {
            throw new RuntimeException("Test failed!");
        }

        Sysout.println("Test passed.");
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
