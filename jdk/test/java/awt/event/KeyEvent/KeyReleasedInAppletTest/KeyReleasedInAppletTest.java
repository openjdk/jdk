/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.*;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import java.awt.*;
import java.awt.FileDialog;
import java.awt.Label;
import java.awt.event.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.FileWriter;
import java.lang.*;
import java.lang.Override;
import java.lang.String;
import java.lang.System;
import java.lang.Throwable;
import java.util.Hashtable;

/*
@test
@bug 8010009
@summary [macosx] Unable type into online word games on MacOSX
@author petr.pchelko : area=awt.keyboard
@run clean *
@run build TestApplet
@run applet/manual=yesno KeyReleasedInAppletTest.html
*/

public class KeyReleasedInAppletTest extends JApplet {
    private static final String TEST_HTML_NAME = "TestApplet.html";

    public void init() {
        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout(new BorderLayout());

        try {
            String testFilePath = System.getProperty("test.classes");
            FileWriter testHTML = null;
            try {
                testHTML = new FileWriter(testFilePath + "/" + TEST_HTML_NAME);
                testHTML.write("<html>\n" +
                        "<head>\n" +
                        "<title>KeyReleasedInAppletTest </title>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<h1>KeyReleasedInAppletTest<br>Bug ID:8010009 </h1>\n" +
                        "<p>Make sure the applet is focuced and type any character on the keyboard. <br>"+
                        "The applet should show keyPressed, keyTyped and keyReleased messages.</p>\n" +
                        "<APPLET CODE=\"TestApplet.class\" WIDTH=400 HEIGHT=200></APPLET>\n" +
                        "</body>");
            } finally {
                if (testHTML != null) {
                    testHTML.close();
                }
            }

            String[] instructions =
                    {
                            "(1) Install the tested JDK to be used by the Java Plugin.\n",
                            "(2) Open Java Preferences and set security level to minimal.\n",
                            "(3) Open the " + TEST_HTML_NAME + " in Firefox in firefox web browser\n" +
                                    " It is located at: " + testFilePath,
                            "(5) Continue the test according to the instructions in the applet.\n",
                    };
            Sysout.createDialogWithInstructions(instructions);
        } catch (Throwable e) {
            //Fail the test.
            throw new RuntimeException(e.getMessage());
        }

    }//End  init()

    public void start() {
    }// start()
}

/* Place other classes related to the test after this line */

/****************************************************
 Standard Test Machinery
 DO NOT modify anything below -- it's a standard
 chunk of code whose purpose is to make user
 interaction uniform, and thereby make it simpler
 to read and understand someone else's test.
 ****************************************************/

/**
 * This is part of the standard test machinery.
 * It creates a dialog (with the instructions), and is the interface
 * for sending text messages to the user.
 * To print the instructions, send an array of strings to Sysout.createDialog
 * WithInstructions method.  Put one line of instructions per array entry.
 * To display a message for the tester to see, simply call Sysout.println
 * with the string to be displayed.
 * This mimics System.out.println but works within the test harness as well
 * as standalone.
 */

class Sysout {
    private static TestDialog dialog;
    private static boolean numbering = false;
    private static int messageNumber = 0;

    public static void createDialogWithInstructions(String[] instructions) {
        dialog = new TestDialog(new Frame(), "Instructions");
        dialog.printInstructions(instructions);
        dialog.setVisible(true);
        println("Any messages for the tester will display here.");
    }

    public static void createDialog() {
        dialog = new TestDialog(new Frame(), "Instructions");
        String[] defInstr = {"Instructions will appear here. ", ""};
        dialog.printInstructions(defInstr);
        dialog.setVisible(true);
        println("Any messages for the tester will display here.");
    }

    /* Enables message counting for the tester. */
    public static void enableNumbering(boolean enable) {
        numbering = enable;
    }

    public static void printInstructions(String[] instructions) {
        dialog.printInstructions(instructions);
    }


    public static void println(String messageIn) {
        if (numbering) {
            messageIn = "" + messageNumber + " " + messageIn;
            messageNumber++;
        }
        dialog.displayMessage(messageIn);
    }

}// Sysout  class

/**
 * This is part of the standard test machinery.  It provides a place for the
 * test instructions to be displayed, and a place for interactive messages
 * to the user to be displayed.
 * To have the test instructions displayed, see Sysout.
 * To have a message to the user be displayed, see Sysout.
 * Do not call anything in this dialog directly.
 */
class TestDialog extends Dialog {

    TextArea instructionsText;
    TextArea messageText;
    int maxStringLength = 80;

    //DO NOT call this directly, go through Sysout
    public TestDialog(Frame frame, String name) {
        super(frame, name);
        int scrollBoth = TextArea.SCROLLBARS_BOTH;
        instructionsText = new TextArea("", 15, maxStringLength, scrollBoth);
        add("North", instructionsText);

        messageText = new TextArea("", 5, maxStringLength, scrollBoth);
        add("Center", messageText);

        pack();

        setVisible(true);
    }// TestDialog()

    //DO NOT call this directly, go through Sysout
    public void printInstructions(String[] instructions) {
        //Clear out any current instructions
        instructionsText.setText("");

        //Go down array of instruction strings

        String printStr, remainingStr;
        for (int i = 0; i < instructions.length; i++) {
            //chop up each into pieces maxSringLength long
            remainingStr = instructions[i];
            while (remainingStr.length() > 0) {
                //if longer than max then chop off first max chars to print
                if (remainingStr.length() >= maxStringLength) {
                    //Try to chop on a word boundary
                    int posOfSpace = remainingStr.
                            lastIndexOf(' ', maxStringLength - 1);

                    if (posOfSpace <= 0) posOfSpace = maxStringLength - 1;

                    printStr = remainingStr.substring(0, posOfSpace + 1);
                    remainingStr = remainingStr.substring(posOfSpace + 1);
                }
                //else just print
                else {
                    printStr = remainingStr;
                    remainingStr = "";
                }

                instructionsText.append(printStr + "\n");

            }// while

        }// for

    }//printInstructions()

    //DO NOT call this directly, go through Sysout
    public void displayMessage(String messageIn) {
        messageText.append(messageIn + "\n");
        System.out.println(messageIn);
    }

}// TestDialog  class
