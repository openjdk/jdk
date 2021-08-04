/*
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
  @test
  @bug 4179262
  @summary Confirm that transparent colors are printed correctly. The
    printout should show transparent rings with increasing darkness toward
    the center.
  @run main/manual XparColor
  @run main/manual/othervm -Dsun.java2d.metal=true XparColor
 */

import java.awt.Dialog;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Button;
import java.awt.TextArea;
import java.awt.Panel;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.geom.Ellipse2D;

public class XparColor implements Printable {

    private static void init() {
        String[] instructions =
                {
			"This testcase will be launched twice, once for opengl and once for metal.",
			"This test verify that the BullsEye rings are printed correctly.",
			"The printout should show transparent rings with increasing darkness toward the center"
                };
        Sysout.createDialog();
        Sysout.printInstructions(instructions);

        XparColor xc = new XparColor();
        PrinterJob printJob = PrinterJob.getPrinterJob();
        printJob.setPrintable(xc);
        if (printJob.printDialog()) {
            try {
                printJob.print();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public int print(Graphics g, PageFormat pf, int pi) throws PrinterException {
        if (pi >= 1) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pf.getImageableX(), pf.getImageableY());
        g2d.translate(pf.getImageableWidth() / 2, pf.getImageableHeight() / 2);

        Dimension d = new Dimension(400, 400);
        double scale = Math.min(pf.getImageableWidth() / d.width, pf.getImageableHeight() / d.height);
        if (scale < 1.0) {
            g2d.scale(scale, scale);
        }

        g2d.translate(-d.width / 2.0, -d.height / 2.0);
        Graphics2D g2 = (Graphics2D) g;
        drawDemo(d.width, d.height, g2);
        g2.dispose();
        return Printable.PAGE_EXISTS;
    }

    public void drawDemo(int w, int h, Graphics2D g2) {
        Color reds[] = {Color.red.darker(), Color.red};
        for (int N = 0; N < 18; N++) {
            float i = (N + 2) / 2.0f;
            float x = (float) (5 + i * (w / 2 / 10));
            float y = (float) (5 + i * (h / 2 / 10));
            float ew = (w - 10) - (i * w / 10);
            float eh = (h - 10) - (i * h / 10);
            float alpha = (N == 0) ? 0.1f : 1.0f / (19.0f - N);
            if (N >= 16) {
                g2.setColor(reds[N - 16]);
            } else {
                g2.setColor(new Color(0f, 0f, 0f, alpha));
            }
            g2.fill(new Ellipse2D.Float(x, y, ew, eh));
        }
    }


    /*****************************************************
     Standard Test Machinery Section
     DO NOT modify anything in this section -- it's a
     standard chunk of code which has all of the
     synchronisation necessary for the test harness.
     By keeping it the same in all tests, it is easier
     to read and understand someone else's test, as
     well as insuring that all tests behave correctly
     with the test harness.
     There is a section following this for test-defined
     classes
     ******************************************************/
    private static boolean theTestPassed = false;
    private static boolean testGeneratedInterrupt = false;
    private static String failureMessage = "";

    private static Thread mainThread = null;

    private static int sleepTime = 300000;

    public static void main(String args[]) throws InterruptedException {
        mainThread = Thread.currentThread();
        try {
            init();
        } catch (TestPassedException e) {
            //The test passed, so just return from main and harness will interpret this return as a pass
            return;
        }

        /*
            At this point, neither test passed nor test failed has been
            called -- either would have thrown an exception and ended the
            test, so we know we have multiple threads.
            Test involves other threads, so sleep and wait for them to
            called pass() or fail()
         */
        try {
            Thread.sleep(sleepTime);
            //Timed out, so fail the test
            throw new RuntimeException("Timed out after " + sleepTime / 1000 + " seconds");
        } catch (InterruptedException e) {
            if (!testGeneratedInterrupt) throw e;

            //reset flag in case hit this code more than once for some reason (just safety)
            testGeneratedInterrupt = false;
            if (theTestPassed == false) {
                throw new RuntimeException(failureMessage);
            }
        }

    }

    public static synchronized void setTimeoutTo(int seconds) {
        sleepTime = seconds * 1000;
    }

    public static synchronized void pass() {
        Sysout.println("The test passed.");
        Sysout.println("The test is over, hit  Ctl-C to stop Java VM");

        // first check if this is executing in main thread
        if (mainThread == Thread.currentThread()) {
            /*
             * Still in the main thread, so set the flag just for kicks,
             * and throw a test passed exception which will be caught
             * and end the test.
             */
            theTestPassed = true;
            throw new TestPassedException();
        }
        /*
         * pass was called from a different thread, so set the flag and interrupt the main thead.
         */
        theTestPassed = true;
        testGeneratedInterrupt = true;
        mainThread.interrupt();
    }

    public static synchronized void fail() {
        // test writer didn't specify why test failed, so give generic
        fail("it just plain failed! :-)");
    }

    public static synchronized void fail(String whyFailed) {
        Sysout.println("The test failed: " + whyFailed);
        Sysout.println("The test is over, hit  Ctl-C to stop Java VM");
        //check if this called from main thread
        if (mainThread == Thread.currentThread()) {
            //If main thread, fail now 'cause not sleeping
            throw new RuntimeException(whyFailed);
        }
        theTestPassed = false;
        testGeneratedInterrupt = true;
        failureMessage = whyFailed;
        mainThread.interrupt();
    }

}

/**
 * This exception is used to exit from any level of call nesting
 * when it's determined that the test has passed, and immediately
 * end the test.
 */
class TestPassedException extends RuntimeException {
}


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

    public static void createDialogWithInstructions(String[] instructions) {
        dialog = new TestDialog(new Frame(), "Instructions");
        dialog.printInstructions(instructions);
        dialog.show();
        println("Any messages for the tester will display here.");
    }

    public static void createDialog() {
        dialog = new TestDialog(new Frame(), "Instructions");
        String[] defInstr = {"Instructions will appear here. ", ""};
        dialog.printInstructions(defInstr);
        dialog.show();
        println("Any messages for the tester will display here.");
    }

    public static void printInstructions(String[] instructions) {
        dialog.printInstructions(instructions);
    }

    public static void println(String messageIn) {
        dialog.displayMessage(messageIn);
    }

}

/**
 * This is part of the standard test machinery.  It provides a place for the
 * test instructions to be displayed, and a place for interactive messages
 * to the user to be displayed.
 * To have the test instructions displayed, see Sysout.
 * To have a message to the user be displayed, see Sysout.
 * Do not call anything in this dialog directly.
 */
class TestDialog extends Dialog implements ActionListener {

    TextArea instructionsText;
    TextArea messageText;
    int maxStringLength = 80;
    Panel buttonP = new Panel();
    Button passB = new Button("pass");
    Button failB = new Button("fail");

    //DO NOT call this directly, go through Sysout
    public TestDialog(Frame frame, String name) {
        super(frame, name);
        int scrollBoth = TextArea.SCROLLBARS_BOTH;
        instructionsText = new TextArea("", 15, maxStringLength, scrollBoth);
        add("North", instructionsText);

        messageText = new TextArea("", 5, maxStringLength, scrollBoth);
        add("Center", messageText);

        passB = new Button("pass");
        passB.setActionCommand("pass");
        passB.addActionListener(this);
        buttonP.add("East", passB);

        failB = new Button("fail");
        failB.setActionCommand("fail");
        failB.addActionListener(this);
        buttonP.add("West", failB);

        add("South", buttonP);
        pack();
        setVisible(true);
    }

    //DO NOT call this directly, go through Sysout
    public void printInstructions(String[] instructions) {
        instructionsText.setText("");

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
                } else {
                    printStr = remainingStr;
                    remainingStr = "";
                }

                instructionsText.append(printStr + "\n");
            }
        }
    }

    //DO NOT call this directly, go through Sysout
    public void displayMessage(String messageIn) {
        messageText.append(messageIn + "\n");
    }

    /**
     * Catch presses of the passed and failed buttons. Wimply call the standard pass() or fail()
     * static methods of XparColor
     */
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand() == "pass") {
            XparColor.pass();
        } else {
            XparColor.fail();
        }
    }

}
