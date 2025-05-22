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
 *          was set to. The bug was that if setState(Frame.ICONIFIED) was
 *          called before setVisible(true) the Frame would be shown in NORMAL
 *          state instead of ICONIFIED.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameStateTest
 */

public class FrameStateTest implements ActionListener {

    private static final String INSTRUCTIONS = """
        <html><body><p>
        This test checks that when setState(Frame.ICONIFIED) is called before
        setVisible(true) the Frame is shown in the proper iconified state.
        The problem was that it did not honor the initial iconic state, but
        instead was shown in the NORMAL state.
        </p><hr/>
        Steps to try to reproduce this problem:
        <p>
        Select the different options for the Frame:
            <ul>
                <li><i>{Normal, Non-resizable}</i></li>
                <li><i>{Normal, Resizable}</i></li>
                <li><i>{Iconified, Resizable}</i></li>
                <li><i>{Iconified, Non-resizable}</i></li>
            </ul>
        After choosing the Frame's state click the
        Create Frame button.<br>
        After the Frame (Frame State Test (Window2)) comes up make sure the
        proper behavior occurred (Frame shown in proper state).<br>
        Click the Dispose button to close the Frame.<br>
        </p><hr/><p>

        Do the above steps for all the different Frame state combinations
        available.<br>
        For "Hide, Iconify and Show" case, the frame is hidden then iconified
        hence Window2 is not seen on-screen when shown as the frame is still
        in the ICONIFIED state. Window2 is visible on-screen when it is restored
        to NORMAL state as observed with "Hide, Iconify, Show and Restore" case.
        <br><br>

        If you observe the proper behavior for all the combinations,
        press PASS else FAIL.<br>
        </p><p>
        Note: In Frame State Test (Window2) you can also chose the different
        buttons to see different Frame behavior.<br>An example of a problem that
        has been seen, with the Frame non-resizable you can not iconify the Frame.
        </p>
        </body>
        </html>
        """;

    public static final int DELAY = 1000;

    Button btnCreate = new Button("Create Frame");
    Button btnDispose = new Button("Dispose Frame");
    CheckboxGroup cbgState = new CheckboxGroup();
    CheckboxGroup cbgResize = new CheckboxGroup();
    Checkbox cbIconState = new Checkbox("Frame State ICONIFIED", cbgState, true);
    Checkbox cbNormState = new Checkbox("Frame State NORMAL", cbgState, false);
    Checkbox cbNonResize = new Checkbox("Frame Non-Resizable", cbgResize, false);
    Checkbox cbResize = new Checkbox("Frame Resizable", cbgResize, true);

    CreateFrame icontst;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
                .builder()
                .title("Frame State and Size Test Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(10)
                .rows(27)
                .columns(70)
                .logArea(6)
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

        btnDispose.addActionListener(this);
        btnCreate.addActionListener(this);

        return panel;
    }

    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == btnCreate) {
            btnCreate.setEnabled(false);
            btnDispose.setEnabled(true);
            icontst = new CreateFrame(cbIconState.getState(), cbResize.getState());
            icontst.setVisible(true);
        } else if (evt.getSource() == btnDispose) {
            btnCreate.setEnabled(true);
            btnDispose.setEnabled(false);
            icontst.dispose();
        }
    }

    static class CreateFrame extends Frame
            implements ActionListener, WindowListener {

        Button b1, b2, b3, b4, b5, b6, b7;
        boolean isResizable;
        String name = "Frame State Test";

        CreateFrame(boolean iconified, boolean resizable) {
            setTitle("Test Window (Window 2)");

            isResizable = resizable;

            PassFailJFrame.log("CREATING FRAME - Initially " +
                    ((iconified) ? "ICONIFIED" : "NORMAL (NON-ICONIFIED)") + " and " +
                    ((isResizable) ? "RESIZABLE" : "NON-RESIZABLE"));

            setLayout(new FlowLayout());
            add(b1 = new Button("Resizable"));
            add(b2 = new Button("Resize"));
            add(b3 = new Button("Iconify"));
            add(b4 = new Button("Iconify and Restore"));
            add(b5 = new Button("Hide and Show"));
            add(b6 = new Button("Hide, Iconify and Show"));
            add(b7 = new Button("Hide, Iconify, Show and Restore"));
            b1.addActionListener(this);
            b2.addActionListener(this);
            b3.addActionListener(this);
            b4.addActionListener(this);
            b5.addActionListener(this);
            b6.addActionListener(this);
            b7.addActionListener(this);
            addWindowListener(this);

            setBounds(100, 2, 300, 200);
            setState(iconified ? Frame.ICONIFIED : Frame.NORMAL);
            setResizable(isResizable);
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

        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == b2) {
                Rectangle r = this.getBounds();
                r.width += 10;
                stateLog(" - button pressed - setting bounds on Frame to: " + r);
                setBounds(r);
                validate();
            } else if (e.getSource() == b1) {
                isResizable = !isResizable;
                stateLog(" - button pressed - setting Resizable to: " + isResizable);
                ((Frame) (b1.getParent())).setResizable(isResizable);
            } else if (e.getSource() == b3) {
                stateLog(" - button pressed - setting Iconic: ");
                ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                stateLog();
            } else if (e.getSource() == b4) {
                stateLog(" - button pressed - setting Iconic: ");
                ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                stateLog();
                delayedActions(() -> {
                    stateLog(" - now restoring: ");
                    ((Frame) (b1.getParent())).setState(Frame.NORMAL);
                    stateLog();
                });
            } else if (e.getSource() == b5) {
                stateLog(" - button pressed - hiding : ");
                b1.getParent().setVisible(false);
                stateLog();
                delayedActions(() -> {
                    stateLog(" - now reshowing: ");
                    b1.getParent().setVisible(true);
                    stateLog();
                });
            } else if (e.getSource() == b6) {
                stateLog(" - button pressed - hiding : ");
                b1.getParent().setVisible(false);
                stateLog();
                delayedActions(
                        () -> {
                            stateLog(" - setting Iconic: ");
                            ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                        },
                        () -> {
                            stateLog(" - now reshowing: ");
                            b1.getParent().setVisible(true);
                            stateLog();
                        }
                );
            } else if (e.getSource() == b7) {
                stateLog(" - button pressed - hiding : ");
                b1.getParent().setVisible(false);
                stateLog();

                delayedActions(
                        () -> {
                            stateLog(" - setting Iconic: ");
                            ((Frame) (b1.getParent())).setState(Frame.ICONIFIED);
                        },
                        () -> {
                            stateLog(" - now reshowing: ");
                            b1.getParent().setVisible(true);
                            stateLog();
                        },
                        () -> {
                            stateLog(" - now restoring: ");
                            ((Frame) (b1.getParent())).setState(Frame.NORMAL);
                            stateLog();
                        }
                );
            }
        }

        public void windowActivated(WindowEvent e) {
            stateLog("Activated");
        }

        public void windowClosed(WindowEvent e) {
            stateLog("Closed");
        }

        public void windowClosing(WindowEvent e) {
            ((Window) (e.getSource())).dispose();
            stateLog("Closing");
        }

        public void windowDeactivated(WindowEvent e) {
            stateLog("Deactivated");
        }

        public void windowDeiconified(WindowEvent e) {
            stateLog("Deiconified");
        }

        public void windowIconified(WindowEvent e) {
            stateLog("Iconified");
        }

        public void windowOpened(WindowEvent e) {
            stateLog("Opened");
        }

        public void stateLog(String message) {
            PassFailJFrame
                .log("[Current State = %d] %s %s".formatted(getState(), name, message));
        }

        public void stateLog() {
            PassFailJFrame.log("[Current State = " + getState() + "]");
        }
    }
}
