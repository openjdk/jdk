/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * FrameStateTest.java
 *
 * summary: Checks that when setState(Frame.ICONIFIED) is called before
 *      setVisible(true) the Frame is shown in the proper iconified state.
 *      The problem was that it did not honor the initial iconic state, but
 *      instead was shown in the NORMAL state.
 */

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import javax.swing.JPanel;
import javax.swing.Timer;

/*
 * @test
 * @bug 4157271
 * @summary Checks that when a Frame is created it honors the state it
 *       was set to. The bug was that if setState(Frame.ICONIFIED) was
 *       called before setVisible(true) the Frame would be shown in NORMAL
 *       state instead of ICONIFIED.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameStateTest
 */

public class FrameStateTest implements ActionListener, ItemListener {

    private static final String INSTRUCTIONS = """
            <html><body>
            
            <h1>FrameStateTest<br>Bug ID: 4157271</h1>
            <p>This test checks that when setState(Frame.ICONIFIED) is called before
              	setVisible(true) the Frame is shown in the proper iconified state.<br>
              	The problem was that it did not honor the initial iconic state, but
             	instead was shown in the NORMAL state.</p>
            <h3>Steps to try to reproduce this problem:</h3>
            When this test is run an Applet Viewer window will display. In the
            Applet Viewer window select the different options for the Frame (i.e.
            {Normal, Non-resizalbe}, {Normal, Resizable}, {Iconified, Resizable},
            {Iconified, Non-resizalbe}). After chosing the Frame's state click the
            Create Frame button. After the Frame (Frame State Test (Window2)) comes
            up make sure the proper behavior occurred (Frame shown in proper state).
            Click the Dispose button to close the Frame. Do the above steps for all
            the different Frame state combinations available. If you observe the
            proper behavior the test has passed, Press the Pass button. Otherwise
            the test has failed, Press the Fail button.
            Note: In Frame State Test (Window2) you can also chose the different
            buttons to see different Frame behavior. An example of a problem that
            has been seen, With the Frame non-resizable you can not iconify the Frame.
            </body>
            </html>
            """;

    public static final int DELAY = 1000;

    Button btnCreate = new Button("Create Frame");
    Button btnDispose = new Button("Dispose Frame");
    CheckboxGroup cbgState = new CheckboxGroup();
    CheckboxGroup cbgResize = new CheckboxGroup();
    Checkbox cbIconState = new Checkbox("Frame state ICONIFIED", cbgState, false);
    Checkbox cbNormState = new Checkbox("Frame state NORMAL", cbgState, true);
    Checkbox cbNonResize = new Checkbox("Frame non-resizable", cbgResize, false);
    Checkbox cbResize = new Checkbox("Frame Resizable", cbgResize, true);

    int iState = 0;
    boolean bResize = true;
    CreateFrame icontst;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
                .builder()
                .title("GetBoundsResizeTest Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(10)
                .rows(20)
                .columns(70)
                .logArea(10)
                .splitUIBottom(() -> new FrameStateTest().createPanel())
                .build()
                .awaitAndCheck();
    }

    public JPanel createPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 3));
        btnDispose.setEnabled(false);

        panel.add(cbIconState);
        panel.add(cbResize);
        panel.add(btnCreate);
        panel.add(cbNormState);
        panel.add(cbNonResize);
        panel.add(btnDispose);

        // Add Listeners
        btnDispose.addActionListener(this);
        btnCreate.addActionListener(this);
        cbNormState.addItemListener(this);
        cbResize.addItemListener(this);
        cbIconState.addItemListener(this);
        cbNonResize.addItemListener(this);

        return panel;
    }

    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == btnCreate) {
            btnCreate.setEnabled(false);
            btnDispose.setEnabled(true);
            icontst = new CreateFrame(iState, bResize);
            icontst.setVisible(true);
        } else if (evt.getSource() == btnDispose) {
            btnCreate.setEnabled(true);
            btnDispose.setEnabled(false);
            icontst.dispose();
        }
    }

    public void itemStateChanged(ItemEvent evt) {
        if (cbNormState.getState()) iState = 0;
        if (cbIconState.getState()) iState = 1;
        if (cbResize.getState()) bResize = true;
        if (cbNonResize.getState()) bResize = false;
    }

    static class CreateFrame extends Frame
            implements ActionListener, WindowListener {

        Button b1, b2, b3, b4, b5, b6, b7;
        boolean resizable = true;
        boolean iconic = false;
        String name = "Frame State Test";

        CreateFrame(int iFrameState, boolean bFrameResizable) {

            setTitle("Frame State Test (Window 2)");

            if (iFrameState == 1) {
                iconic = true;
            }

            if (!(bFrameResizable)) {
                resizable = false;
            }

            PassFailJFrame.log("CREATING FRAME - Initially " +
                    ((iconic) ? "ICONIFIED" : "NORMAL (NON-ICONIFIED)") + " and " +
                    ((resizable) ? "RESIZABLE" : "NON-RESIZABLE"));

            setLayout(new FlowLayout());
            b1 = new Button("resizable");
            add(b1);
            b2 = new Button("resize");
            add(b2);
            b3 = new Button("iconify");
            add(b3);
            b4 = new Button("iconify and restore");
            add(b4);
            b5 = new Button("hide and show");
            add(b5);
            b6 = new Button("hide, iconify and show");
            add(b6);
            b7 = new Button("hide, iconify, show, and restore");
            add(b7);
            b1.addActionListener(this);
            b2.addActionListener(this);
            b3.addActionListener(this);
            b4.addActionListener(this);
            b5.addActionListener(this);
            b6.addActionListener(this);
            b7.addActionListener(this);
            addWindowListener(this);

            setBounds(100, 2, 200, 200);
            setState(iconic ? Frame.ICONIFIED : Frame.NORMAL);
            setResizable(resizable);
            pack();
            setVisible(true);
        }

        /**
         * Calls all runnables on EDT with a {@code DELAY} delay before each run.
         * @param runnables to run
         */
        private static void delayedActions(Runnable... runnables) {
            setTimer(new ArrayDeque<>(Arrays.asList(runnables)));
        }

        private static void setTimer(Deque<Runnable> deque) {
            if (deque == null || deque.isEmpty()) return;

            Timer timer = new Timer(DELAY, e -> {
                deque.pop().run();
                setTimer(deque);
            });
            timer.setRepeats(false);
            timer.start();
        }

        private void log(String message) {
            PassFailJFrame.log(message);
        }

        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == b2) {
                Rectangle r = this.getBounds();
                r.width += 10;
                log(" - button pressed - setting bounds on Frame to: " + r);
                setBounds(r);
                validate();
            } else if (e.getSource() == b1) {
                resizable = !resizable;
                log(" - button pressed - setting Resizable to: " + resizable);
                ((Frame) (b1.getParent())).setResizable(resizable);
            } else if (e.getSource() == b3) {
                log(" - button pressed - setting Iconic: ");
                dolog();
                ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                dolog();
            } else if (e.getSource() == b4) {
                log(" - button pressed - setting Iconic: ");
                dolog();
                ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                dolog();
                delayedActions(() -> {
                    log(" - now restoring: ");
                    ((Frame) (b1.getParent())).setState(Frame.NORMAL);
                    dolog();
                });
            } else if (e.getSource() == b5) {
                log(" - button pressed - hiding : ");
                dolog();
                ((Frame) (b1.getParent())).setVisible(false);
                dolog();
                delayedActions(() -> {
                    log(" - now reshowing: ");
                    ((Frame) (b1.getParent())).setVisible(true);
                    dolog();
                });
            } else if (e.getSource() == b6) {
                log(" - button pressed - hiding : ");
                dolog();
                ((Frame) (b1.getParent())).setVisible(false);
                dolog();
                delayedActions(
                        () -> {
                            log(" - setting Iconic: ");
                            dolog();
                            ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                        },
                        () -> {
                            log(" - now reshowing: ");
                            ((Frame) (b1.getParent())).setVisible(true);
                            dolog();
                        }
                );
            } else if (e.getSource() == b7) {
                log(" - button pressed - hiding : ");
                dolog();
                ((Frame) (b1.getParent())).setVisible(false);
                dolog();

                delayedActions(
                        () -> {
                            log(" - setting Iconic: ");
                            dolog();
                            ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                        },
                        () -> {
                            log(" - now reshowing: ");
                            ((Frame) (b1.getParent())).setVisible(true);
                            dolog();
                        },
                        () -> {
                            log(" - now restoring: ");
                            ((Frame) (b1.getParent())).setState(Frame.NORMAL);
                            dolog();
                        }
                );
            }
        }

        public void windowActivated(WindowEvent e) {
            log(name + " Activated");
            dolog();
        }

        public void windowClosed(WindowEvent e) {
            log(name + " Closed");
            dolog();
        }

        public void windowClosing(WindowEvent e) {
            ((Window) (e.getSource())).dispose();
            log(name + " Closing");
            dolog();
        }

        public void windowDeactivated(WindowEvent e) {
            log(name + " Deactivated");
            dolog();
        }

        public void windowDeiconified(WindowEvent e) {
            log(name + " Deiconified");
            dolog();
        }

        public void windowIconified(WindowEvent e) {
            log(name + " Iconified");
            dolog();
        }

        public void windowOpened(WindowEvent e) {
            log(name + " Opened");
            dolog();
        }

        public void dolog() {
            String message = " getState returns: " + getState();
            System.out.println(message);
//            sysout.append(message + "\n");
        }
    }
}
