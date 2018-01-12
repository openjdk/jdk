/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8155740
 * @key headful
 * @summary See <rdar://problem/3429130>: Events: actionPerformed() method not
 *          called when it is button is clicked (system load related)
 * @summary com.apple.junit.java.awt.Frame
 * @library ../../../regtesthelpers
 * @build VisibilityValidator
 * @build Util
 * @build Waypoint
 * @run main NestedModalDialogTest
 */

//////////////////////////////////////////////////////////////////////////////
//  NestedModalDialogTest.java
// The test launches a parent frame. From this parent frame it launches a modal
// dialog. From the modal dialog it launches a second modal dialog with a text
// field in it and tries to write into the text field. The test succeeds if you
// are successfully able to write into this second Nested Modal Dialog
//////////////////////////////////////////////////////////////////////////////
// classes necessary for this test

import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;

import test.java.awt.regtesthelpers.Waypoint;
import test.java.awt.regtesthelpers.VisibilityValidator;
import test.java.awt.regtesthelpers.Util;

public class NestedModalDialogTest {

    Waypoint[] event_checkpoint = new Waypoint[3];
    VisibilityValidator[] win_checkpoint = new VisibilityValidator[2];

    IntermediateDialog interDiag;
    TextDialog txtDiag;

    // Global variables so the robot thread can locate things.
    Button[] robot_button = new Button[2];
    TextField robot_text = null;
    static Robot _robot = null;

    /*
     * @throws InterruptedException
     * @throws WaypointException
     */
    public void testModalDialogs() throws Exception {
        Frame frame = null;
        String result = "";
        Robot robot = getRobot();

        event_checkpoint[0] = new Waypoint(); // "-Launch 1-"
        event_checkpoint[1] = new Waypoint(); // "-Launch 2-"

        // Thread.currentThread().setName("NestedModalDialogTest Thread");
        // launch first frame with firstButton
        frame = new StartFrame();
        VisibilityValidator.setVisibleAndConfirm(frame);
        Util.clickOnComp(robot_button[0], robot);

        // Dialog must be created and onscreen before we proceed.
        //   The event_checkpoint waits for the Dialog to be created.
        //   The win_checkpoint waits for the Dialog to be visible.
        event_checkpoint[0].requireClear("TestFrame actionPerformed() never "
                + "called, see <rdar://problem/3429130>");
        win_checkpoint[0].requireVisible();
        Util.clickOnComp(robot_button[1], robot);

        // Again, the Dialog must be created and onscreen before we proceed.
        //   The event_checkpoint waits for the Dialog to be created.
        //   The win_checkpoint waits for the Dialog to be visible.
        event_checkpoint[1].requireClear("IntermediateDialog actionPerformed() "
                + "never called, see <rdar://problem/3429130>");
        win_checkpoint[1].requireVisible();
        Util.clickOnComp(robot_text, robot);

        // I'm really not sure whether the click is needed for focus
        // but since it's asynchronous, as is the actually gaining of focus
        // we might as well do our best
        try {
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                }
            });
        } catch (Exception e) {
        }

        robot.keyPress(KeyEvent.VK_SHIFT);

        robot.keyPress(KeyEvent.VK_H);
        robot.waitForIdle();
        robot.keyRelease(KeyEvent.VK_H);

        robot.keyRelease(KeyEvent.VK_SHIFT);

        robot.keyPress(KeyEvent.VK_E);
        robot.waitForIdle();
        robot.keyRelease(KeyEvent.VK_E);

        robot.keyPress(KeyEvent.VK_L);
        robot.waitForIdle();
        robot.keyRelease(KeyEvent.VK_L);

        robot.keyPress(KeyEvent.VK_L);
        robot.waitForIdle();
        robot.keyRelease(KeyEvent.VK_L);

        robot.keyPress(KeyEvent.VK_O);
        robot.waitForIdle();
        robot.keyRelease(KeyEvent.VK_O);

        //
        // NOTE THAT WE MAY HAVE MORE SYNCHRONIZATION WORK TO DO HERE.
        // CURRENTLY THERE IS NO GUARANTEE THAT THE KEYEVENT THAT THAT
        // TYPES THE 'O' HAS BEEN PROCESSED BEFORE WE GET THE RESULT
        //
        // This is a (lame) attempt at waiting for the last typeKey events to
        // propagate. It's not quite right because robot uses
        // CGRemoteOperations, which are asynchronous. But that's why I put in
        // the pause
        try {
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                }
            });
        } catch (Exception e) {
        }

        // Need to call this before the dialog that robot_text is in is disposed
        result = robot_text.getText();

        Thread.sleep(50); // shouldn't need this, but pause adds stability
        // Click Close box of modal dialog with textField
        Util.clickOnComp(txtDiag, robot);

        Thread.sleep(50); // shouldn't need this, but pause adds stability
        // Click Close box of intermediate modal dialog
        Util.clickOnComp(interDiag, robot);

        Thread.sleep(50); // shouldn't need this, but pause adds stability
        // Click Close box of intermediate modal dialog
        Util.clickOnComp(frame, robot);

        String expected = "Hello";
    }

    private static Robot getRobot() {
        if (_robot == null) {
            try {
                _robot = new Robot();
            } catch (AWTException e) {
                throw new RuntimeException("Robot creation failed");
            }
        }
        return _robot;
    }

    //////////////////// Start Frame ///////////////////
    /**
     * Launches the first frame with a button in it
     */
    class StartFrame extends Frame {

        /**
         * Constructs a new instance.
         */
        public StartFrame() {
            super("First Frame");
            setLayout(new GridBagLayout());
            setLocation(375, 200);
            setSize(271, 161);
            Button but = new Button("Make Intermediate");
            but.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    interDiag = new IntermediateDialog(StartFrame.this);
                    win_checkpoint[0] = new VisibilityValidator(interDiag);
                    interDiag.setSize(300, 200);

                    // may need listener to watch this move.
                    interDiag.setLocation(getLocationOnScreen());
                    interDiag.pack();
                    event_checkpoint[0].clear();
                    interDiag.setVisible(true);
                }
            });
            Panel pan = new Panel();
            pan.add(but);
            add(pan);
            robot_button[0] = but;
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    setVisible(false);
                    dispose();
                }
            });
        }
    }

    ///////////////////////////// MODAL DIALOGS /////////////////////////////
    /* A Dialog that launches a sub-dialog */
    class IntermediateDialog extends Dialog {

        Dialog m_parent;

        public IntermediateDialog(Frame parent) {
            super(parent, "Intermediate Modal", true /*Modal*/);
            m_parent = this;
            Button but = new Button("Make Text");
            but.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    txtDiag = new TextDialog(m_parent);
                    win_checkpoint[1] = new VisibilityValidator(txtDiag);
                    txtDiag.setSize(300, 100);
                    event_checkpoint[1].clear();
                    txtDiag.setVisible(true);
                }
            });
            Panel pan = new Panel();
            pan.add(but);
            add(pan);
            pack();
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    setVisible(false);
                    dispose();
                }
            });

            // The robot needs to know about us, so set global
            robot_button[1] = but;
        }
    }

    /* A Dialog that just holds a text field */
    class TextDialog extends Dialog {

        public TextDialog(Dialog parent) {
            super(parent, "Modal Dialog", true /*Modal*/);
            TextField txt = new TextField("", 10);
            Panel pan = new Panel();
            pan.add(txt);
            add(pan);
            pack();
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    setVisible(false);
                    dispose();
                }
            });

            // The robot needs to know about us, so set global
            robot_text = txt;
        }
    }

    public static void main(String[] args) throws RuntimeException, Exception {
        try {
            new NestedModalDialogTest().testModalDialogs();
        } catch (Exception e) {
            throw new RuntimeException("NestedModalDialogTest object creation "
                    + "failed");
        }
    }
}
