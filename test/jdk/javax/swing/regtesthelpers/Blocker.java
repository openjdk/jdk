/*
 * Copyright (c) 2006, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * Blocker can be used to block a thread until another thread
 * messages the blocker to stop. This is useful for regression
 * tests that use swing, as most of the work for testing a swing component
 * is done in the event dispatching thread and the testing harness ends when
 * main returns.
 * <p>The following shows the typical usable of this class:
 * <pre>
     public static void main(String[] args) throws Throwable {
         ... set up the gui ...
         Blocker blocker = new Blocker();
         blocker.blockTillDone();
     }

     public void actionPerformed(ActionEvent ae) {
         if (failed) {
             blocker.testFailed(new RuntimeException("FAILED!"));
         }
         else {
             blocker.testPassed();
         }
     }
   </pre>
 * When using jtreg you would include this class via something like:
 * <pre>
     @library ../../../regtesthelpers
     @build Blocker
     @run main YourTest
   </pre>
 *
 * <p>You can also use the method <code>createFrameWithPassFailButtons</code>
 * that will create a JFrame containing two buttons (pass and fail), with
 * the two buttons wired to pass/fail the test. Refer to the javadoc for
 * more info.
 */

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;

public class Blocker {
    private boolean       done;
    private Throwable     error;

    /**
     * Call this from the main thread to block the reciever. Then call
     * either <code>testPassed</code> or <code>testFailed</code>, from
     * another thread to unblock the receiver. This will only throw an
     * exception if <code>testFailed</code> is invoked.
     */
    public void blockTillDone() throws Throwable {
        synchronized(this) {
            while (!done) {
                wait();
            }
        }
        if (error != null) {
            throw error;
        }
    }

    /**
     * Invoke this to stop the blocker thread.  This does not change the
     * status of the test.  This is intended for cases where you don't
     * know if you've failed, but want to stop the test.
     */
    public void stopTest() {
        synchronized(this) {
            done = true;
            notifyAll();
        }
    }

    /**
     * Invoke if the test has suceeded. This will notify the main thread
     * causing it to stop waiting and continue, which will
     * cause the test to finish.
     */
    public void testPassed() {
        stopTest(null);
    }

    /**
     * Invoke if the test has failed. <code>error</code> gives the
     * exception that will be thrown from the main thread. This will notify
     * the main thread causing it to stop waiting and continue, which will
     * cause the test to finish. If <code>error</code> is null, this has
     * The same effect as calling <code>testPassed</code>.
     */
    public void testFailed(Throwable error) {
        stopTest(error);
    }

    /**
     * Both <code>testPassed</code> and <code>testFailed</code> call into
     * this. Sets the ivar, <code>done</code>, and notifies listeners
     * which will unblock the caller of <code>blockTillDone</code>.
     */
    protected void stopTest(Throwable error) {
        synchronized(this) {
            this.error = error;
            done = true;
            notify();
        }
    }

    /**
     * Creates and returns a JFrame with two button, one that says pass,
     * another that says fail. The fail button is wired to call
     * <code>uiTestFailed</code> with <code>failString</code> and the pass
     * button is wired to invoked <code>uiTestPassed</code>.
     * <p>The content pane of the JFrame uses a BorderLayout with the
     * buttons inside a horizontal box with filler between them and the
     * pass button on the left.
     * <p>The returned frame has not been packed, or made visible, it is
     * up to the caller to do that (after putting in some useful components).
     */
    public JFrame createFrameWithPassFailButtons(final String failString) {
        JFrame         retFrame = new JFrame("TEST");
        Box            buttonBox = Box.createHorizontalBox();
        JButton        passButton = new JButton("Pass");
        JButton        failButton = new JButton("Fail");

        passButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                uiTestPassed();
            }
        });
        failButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                uiTestFailed(failString);
            }
        });
        retFrame.getContentPane().setLayout(new BorderLayout());
        buttonBox.add(passButton);
        buttonBox.add(Box.createGlue());
        buttonBox.add(failButton);
        retFrame.getContentPane().add(buttonBox, BorderLayout.SOUTH);
        retFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        return retFrame;
    }

    /**
     * This is invoked from the pass button. It in turn invokes
     * <code>testPassed</code>.
     */
    protected void uiTestPassed() {
        testPassed();
    }

    /**
     * This is invoked from the fail button. It in turn invokes
     * <code>testFailed</code> with a RuntimeException, the contents of
     * which are <code>failString</code>.
     */
    protected void uiTestFailed(String failString) {
        testFailed(new RuntimeException(failString));
    }
}
