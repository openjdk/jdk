/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @key headful
  @bug 4531693 4636269 4681908 4688142 4691646 4721470
  @summary Showing modal dialog during dispatching SequencedEvent
  @run main ModalDialogInFocusEventTest
*/

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModalDialogInFocusEventTest
        implements ActionListener, Runnable, WindowListener,
        WindowFocusListener, FocusListener {
    static final int CLICK_DELAY = 50;
    static final int ACTIVATION_TIMEOUT = 2000;
    static final long STAGE_TIMEOUT = 3 * ACTIVATION_TIMEOUT;
    static final StageInfo[] stages = {
            new StageInfo(WindowEvent.WINDOW_ACTIVATED, "Window Activated", false),
            new StageInfo(WindowEvent.WINDOW_GAINED_FOCUS, "Window Gained Focus", false),
            new StageInfo(FocusEvent.FOCUS_GAINED, "Focus Gained", false),
            new StageInfo(FocusEvent.FOCUS_LOST, "Focus Lost", true),
            new StageInfo(WindowEvent.WINDOW_LOST_FOCUS, "Window Lost Focus", true),
            new StageInfo(WindowEvent.WINDOW_DEACTIVATED, "Window Deactivated", true)
    };
    static final int MAX_STAGE_NUM = stages.length;
    static final Object stageMonitor = new Object();

    static boolean isOnWayland;

    Robot robot = null;
    Frame frame;
    Frame oppositeFrame;
    Dialog dialog;
    Button closeButton;
    int nStage = MAX_STAGE_NUM;

    public void start() throws InterruptedException, InvocationTargetException {
        try {
            FocusListener focusEventTracker = new FocusListener() {
                public void focusGained(FocusEvent e) {
                    System.out.println(e);
                }

                public void focusLost(FocusEvent e) {
                    System.out.println(e);
                }
            };

            WindowAdapter windowEventTracker = new WindowAdapter() {
                public void windowActivated(WindowEvent e) {
                    System.out.println(e);
                }

                public void windowDeactivated(WindowEvent e) {
                    System.out.println(e);
                }

                public void windowGainedFocus(WindowEvent e) {
                    System.out.println(e);
                }

                public void windowLostFocus(WindowEvent e) {
                    System.out.println(e);
                }
            };
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("ModalDialogInFocusEventTest Main Frame");
                oppositeFrame = new Frame("ModalDialogInFocusEventTest Opposite Frame");
                dialog = new Dialog(frame, "ModalDialogInFocusEventTest Modal Dialog", true);
                closeButton = new Button("Close Button");
                closeButton.addActionListener(this);
                dialog.add(closeButton);
                dialog.setBounds(10, 200, 300, 100);

                dialog.addFocusListener(focusEventTracker);
                dialog.addWindowListener(windowEventTracker);
                dialog.addWindowFocusListener(windowEventTracker);
                oppositeFrame.addFocusListener(focusEventTracker);
                oppositeFrame.addWindowListener(windowEventTracker);
                oppositeFrame.addWindowFocusListener(windowEventTracker);

                frame.setName("ModalDialogInFocusEventTest MainFrame");
                frame.addFocusListener(this);
                frame.addWindowListener(this);
                frame.addWindowFocusListener(this);
                frame.setSize(300, 100);

                oppositeFrame.setName("ModalDialogInFocusEventTest OppositeName");
                oppositeFrame.setBounds(350, 200, 300, 100);
            });


            try {
                robot = new Robot();
                robot.setAutoDelay(CLICK_DELAY);

                for (int i = 0; i < MAX_STAGE_NUM; i++) {
                    StageInfo stage = stages[i];
                    if (stage.shouldActivateOpposite()) {
                        EventQueue.invokeAndWait(() -> {
                            oppositeFrame.setVisible(true);
                            frame.setVisible(true);
                        });
                        robot.delay(ACTIVATION_TIMEOUT);
                        AtomicBoolean isActive = new AtomicBoolean(false);
                        EventQueue.invokeAndWait(() -> {
                            isActive.set(frame.isActive());
                        });
                        if (!isActive.get()) {
                            clickOnFrameTitle(frame);
                            robot.delay(ACTIVATION_TIMEOUT);
                        }
                    } else {
                        EventQueue.invokeAndWait(() -> {
                            frame.setVisible(true);
                            oppositeFrame.setVisible(true);
                        });
                        robot.delay(ACTIVATION_TIMEOUT);
                        AtomicBoolean isActive = new AtomicBoolean(false);
                        EventQueue.invokeAndWait(() -> {
                            isActive.set(oppositeFrame.isActive());
                        });
                        if (!isActive.get()) {
                            clickOnFrameTitle(oppositeFrame);
                            robot.delay(ACTIVATION_TIMEOUT);
                        }
                    }

                    nStage = i;
                    System.out.println("Stage " + i + " started.");

                    synchronized (stageMonitor) {
                        if (stage.shouldActivateOpposite()) {
                            clickOnFrameTitle(oppositeFrame);
                        } else {
                            clickOnFrameTitle(frame);
                        }
                        stageMonitor.wait(STAGE_TIMEOUT);
                        if (!stage.isFinished()) {
                            throw new RuntimeException(stages[nStage].toString());
                        }
                    }
                    EventQueue.invokeAndWait(() -> {
                        oppositeFrame.setVisible(false);
                        frame.setVisible(false);
                    });
                    robot.delay(ACTIVATION_TIMEOUT);
                }
            } catch (AWTException e) {
                throw new RuntimeException("Some AWT-Robot problem occurred", e);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
                throw new RuntimeException("Test was interrupted");
            }
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
            if (oppositeFrame != null) {
                EventQueue.invokeAndWait(oppositeFrame::dispose);
            }
            if (dialog != null) {
                EventQueue.invokeAndWait(dialog::dispose);
            }
        }
        System.out.println("Test passed.");
    }

    void clickOnFrameTitle(Frame frame) throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(frame::toFront);
        if (!isOnWayland) {
            System.out.println("click on title of " + frame.getName());
            int[] point = new int[2];
            EventQueue.invokeAndWait(() -> {
                Point location = frame.getLocationOnScreen();
                Insets insets = frame.getInsets();
                int width = frame.getWidth();
                point[0] = location.x + width / 2;
                point[1] = location.y + insets.top / 2;
            });
            robot.mouseMove(point[0], point[1]);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
        EventQueue.invokeAndWait(frame::requestFocusInWindow);
    }

    void openAndCloseModalDialog() throws InterruptedException,
            InvocationTargetException {
        (new Thread(this)).start();
        dialog.setVisible(true);
    }

    void performStage(AWTEvent e) throws InterruptedException,
            InvocationTargetException {
        if (nStage < MAX_STAGE_NUM &&
                e.getID() == stages[nStage].getEventID() &&
                !stages[nStage].isStarted()) {
            stages[nStage].start();
            openAndCloseModalDialog();
            stages[nStage].finish();
            synchronized (stageMonitor) {
                stageMonitor.notifyAll();
            }
        }
    }

    public void actionPerformed(ActionEvent ae) {
        System.out.println(ae);
        dialog.setVisible(false);
    }

    public void run() {
        try {
            Thread.sleep(ACTIVATION_TIMEOUT);
            int[] point = new int[2];
            EventQueue.invokeAndWait(() -> {
                Point location = closeButton.getLocationOnScreen();
                Dimension dim = closeButton.getSize();
                point[0] = location.x + dim.width / 2;
                point[1] = location.y + dim.height / 2;
            });
            robot.mouseMove(point[0], point[1]);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            System.out.println("click");
        } catch (InterruptedException | InvocationTargetException ie) {
            throw new RuntimeException("Test was interrupted", ie);
        }
    }

    public void windowOpened(WindowEvent e) {
        /* Empty. Unneeded for this test */
    }

    public void windowClosing(WindowEvent e) {
        /* Empty. Unneeded for this test */
    }

    public void windowClosed(WindowEvent e) {
        /* Empty. Unneeded for this test */
    }

    public void windowIconified(WindowEvent e) {
        /* Empty. Unneeded for this test */
    }

    public void windowDeiconified(WindowEvent e) {
        /* Empty. Unneeded for this test */
    }

    public void windowActivated(WindowEvent e) {
        System.out.println(e);
        try {
            performStage(e);
        } catch (InterruptedException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void windowDeactivated(WindowEvent e) {
        System.out.println(e);
        try {
            performStage(e);
        } catch (InterruptedException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void windowGainedFocus(WindowEvent e) {
        System.out.println(e);
        try {
            performStage(e);
        } catch (InterruptedException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void windowLostFocus(WindowEvent e) {
        System.out.println(e);
        try {
            performStage(e);
        } catch (InterruptedException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void focusGained(FocusEvent e) {
        System.out.println(e);
        try {
            performStage(e);
        } catch (InterruptedException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void focusLost(FocusEvent e) {
        System.out.println(e);
        try {
            performStage(e);
        } catch (InterruptedException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        isOnWayland = System.getenv("WAYLAND_DISPLAY") != null;
        ModalDialogInFocusEventTest test = new ModalDialogInFocusEventTest();
        test.start();
    }
}

class StageInfo {
    private String name;
    private int eventID;
    private boolean started = false;
    private boolean finished = false;

    /*
     * whether we should activate opposite frame during this stage.
     * Note: we need to activate "another" frame BEFORE stage
     * i.e. if we should activate frame during  stage then we
     * need to activate oppositeFrame before it and vice versa.
     */
    private boolean activateOpposite;

    StageInfo(int eventID, String name, boolean activateOpposite) {
        this.eventID = eventID;
        this.name = name;
        this.activateOpposite = activateOpposite;
    }

    public String toString() {
        String str = "Stage [\"" + name + "\"";
        if (!started) {
            str += " not";
        }
        str += " started, ";
        if (!finished) {
            str += " not";
        }
        str += " finished";
        if (activateOpposite) {
            str += ", activate opposite";
        }
        str += "]";
        return str;
    }

    int getEventID() {
        return eventID;
    }

    boolean isStarted() {
        return started;
    }

    void start() {
        started = true;
        System.out.println(this.toString());
    }

    boolean isFinished() {
        return finished;
    }

    void finish() {
        finished = true;
        System.out.println(this.toString());
    }

    boolean shouldActivateOpposite() {
        return activateOpposite;
    }
}
