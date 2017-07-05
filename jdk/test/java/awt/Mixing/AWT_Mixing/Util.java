/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Frame;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.Button;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.IllegalComponentStateException;
import java.awt.AWTException;
import java.awt.AWTEvent;

import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowFocusListener;
import java.awt.event.FocusListener;
import java.awt.event.ActionListener;

import java.awt.peer.FramePeer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.security.PrivilegedAction;
import java.security.AccessController;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>This class contains utilities useful for regression testing.
 * <p>When using jtreg you would include this class into the build
 * list via something like:
 * <pre>
     &amp;library ../../../../share/lib/AWT_Mixing/src/regtesthelpers/
     &amp;build Util
     &amp;run main YourTest
   </pre>
 * Note that if you are about to create a test based on
 * Applet-template, then put those lines into html-file, not in java-file.
 * <p> And put an
 * import regtesthelpers.Util;
 * into the java source of test.
*/
public final class Util {
    private Util() {} // this is a helper class with static methods :)

    /*
     * @throws RuntimeException when creation failed
     */
    public static Robot createRobot() {
        try {
            return new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Error: unable to create robot", e);
        }
    }

    public static Frame createEmbeddedFrame(final Frame embedder)
        throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException,
               InstantiationException, InvocationTargetException
    {
        Toolkit tk = Toolkit.getDefaultToolkit();
        FramePeer frame_peer = (FramePeer) embedder.getPeer();
        System.out.println("frame's peer = " + frame_peer);
        if ("sun.awt.windows.WToolkit".equals(tk.getClass().getName())) {
            Class comp_peer_class =
                Class.forName("sun.awt.windows.WComponentPeer");
            System.out.println("comp peer class = " + comp_peer_class);
            Field hwnd_field = comp_peer_class.getDeclaredField("hwnd");
            hwnd_field.setAccessible(true);
            System.out.println("hwnd_field =" + hwnd_field);
            long hwnd = hwnd_field.getLong(frame_peer);
            System.out.println("hwnd = " + hwnd);

            Class clazz = Class.forName("sun.awt.windows.WEmbeddedFrame");
            Constructor constructor = clazz.getConstructor (new Class [] {Long.TYPE});
            return (Frame) constructor.newInstance (new Object[] {hwnd});
        } else if ("sun.awt.X11.XToolkit".equals(tk.getClass().getName())) {
            Class x_base_window_class = Class.forName("sun.awt.X11.XBaseWindow");
            System.out.println("x_base_window_class = " + x_base_window_class);
            Method get_window = x_base_window_class.getMethod("getWindow", new Class[0]);
            System.out.println("get_window = " + get_window);
            long window = (Long) get_window.invoke(frame_peer, new Object[0]);
            System.out.println("window = " + window);
            Class clazz = Class.forName("sun.awt.X11.XEmbeddedFrame");
            Constructor constructor = clazz.getConstructor (new Class [] {Long.TYPE, Boolean.TYPE});
            return (Frame) constructor.newInstance (new Object[] {window, true});
        }

        throw new RuntimeException("Unexpected toolkit - " + tk);
    }

    /**
     * Makes the window visible and waits until it's shown.
     */
    public static void showWindowWait(Window win) {
        win.setVisible(true);
        waitTillShown(win);
    }

    /**
     * Moves mouse pointer in the center of given {@code comp} component
     * using {@code robot} parameter.
     */
    public static void pointOnComp(final Component comp, final Robot robot) {
        Rectangle bounds = new Rectangle(comp.getLocationOnScreen(), comp.getSize());
        robot.mouseMove(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    /**
     * Moves mouse pointer in the center of a given {@code comp} component
     * and performs a left mouse button click using the {@code robot} parameter
     * with the {@code delay} delay between press and release.
     */
    public static void clickOnComp(final Component comp, final Robot robot, int delay) {
        pointOnComp(comp, robot);
        robot.delay(delay);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.delay(delay);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
    }

    /**
     * Moves mouse pointer in the center of a given {@code comp} component
     * and performs a left mouse button click using the {@code robot} parameter
     * with the default delay between press and release.
     */
    public static void clickOnComp(final Component comp, final Robot robot) {
        clickOnComp(comp, robot, 50);
    }

    /*
     * Clicks on a title of Frame/Dialog.
     * WARNING: it may fail on some platforms when the window is not wide enough.
     */
    public static void clickOnTitle(final Window decoratedWindow, final Robot robot) {
        Point p = decoratedWindow.getLocationOnScreen();
        Dimension d = decoratedWindow.getSize();

        if (decoratedWindow instanceof Frame || decoratedWindow instanceof Dialog) {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + (int)decoratedWindow.getInsets().top/2);
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
        }
    }

    public static void waitForIdle(final Robot robot) {
        // we do not use robot for now, use SunToolkit.realSync() instead
        ((sun.awt.SunToolkit)Toolkit.getDefaultToolkit()).realSync();
    }

    public static Field getField(final Class klass, final String fieldName) {
        return AccessController.doPrivileged(new PrivilegedAction<Field>() {
            public Field run() {
                try {
                    Field field = klass.getDeclaredField(fieldName);
                    assert (field != null);
                    field.setAccessible(true);
                    return field;
                } catch (SecurityException se) {
                    throw new RuntimeException("Error: unexpected exception caught!", se);
                } catch (NoSuchFieldException nsfe) {
                    throw new RuntimeException("Error: unexpected exception caught!", nsfe);
                }
            }
        });
    }

    /*
     * Waits for a notification and for a boolean condition to become true.
     * The method returns when the above conditions are fullfilled or when the timeout
     * occurs.
     *
     * @param condition the object to be notified and the booelan condition to wait for
     * @param timeout the maximum time to wait in milliseconds
     * @param catchExceptions if {@code true} the method catches InterruptedException
     * @return the final boolean value of the {@code condition}
     * @throws InterruptedException if the awaiting proccess has been interrupted
     */
    public static boolean waitForConditionEx(final AtomicBoolean condition, long timeout)
      throws InterruptedException
        {
            synchronized (condition) {
                long startTime = System.currentTimeMillis();
                while (!condition.get()) {
                    condition.wait(timeout);
                    if (System.currentTimeMillis() - startTime >= timeout ) {
                        break;
                    }
                }
            }
            return condition.get();
        }

    /*
     * The same as {@code waitForConditionEx(AtomicBoolean, long)} except that it
     * doesn't throw InterruptedException.
     */
    public static boolean waitForCondition(final AtomicBoolean condition, long timeout) {
        try {
            return waitForConditionEx(condition, timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error: unexpected exception caught!", e);
        }
    }

    /*
     * The same as {@code waitForConditionEx(AtomicBoolean, long)} but without a timeout.
     */
    public static void waitForConditionEx(final AtomicBoolean condition)
      throws InterruptedException
        {
            synchronized (condition) {
                while (!condition.get()) {
                    condition.wait();
                }
            }
        }

    /*
     * The same as {@code waitForConditionEx(AtomicBoolean)} except that it
     * doesn't throw InterruptedException.
     */
    public static void waitForCondition(final AtomicBoolean condition) {
        try {
            waitForConditionEx(condition);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error: unexpected exception caught!", e);
        }
    }

    public static void waitTillShownEx(final Component comp) throws InterruptedException {
        while (true) {
            try {
                Thread.sleep(100);
                comp.getLocationOnScreen();
                break;
            } catch (IllegalComponentStateException e) {}
        }
    }
    public static void waitTillShown(final Component comp) {
        try {
            waitTillShownEx(comp);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error: unexpected exception caught!", e);
        }
    }

    /**
     * Drags from one point to another with the specified mouse button pressed.
     *
     * @param robot a robot to use for moving the mouse, etc.
     * @param startPoint a start point of the drag
     * @param endPoint an end point of the drag
     * @param button one of {@code InputEvent.BUTTON1_MASK},
     *     {@code InputEvent.BUTTON2_MASK}, {@code InputEvent.BUTTON3_MASK}
     *
     * @throws IllegalArgumentException if {@code button} is not one of
     *     {@code InputEvent.BUTTON1_MASK}, {@code InputEvent.BUTTON2_MASK},
     *     {@code InputEvent.BUTTON3_MASK}
     */
    public static void drag(Robot robot, Point startPoint, Point endPoint, int button) {
        if (!(button == InputEvent.BUTTON1_MASK || button == InputEvent.BUTTON2_MASK
                || button == InputEvent.BUTTON3_MASK))
        {
            throw new IllegalArgumentException("invalid mouse button");
        }

        robot.mouseMove(startPoint.x, startPoint.y);
        robot.mousePress(button);
        try {
            mouseMove(robot, startPoint, endPoint);
        } finally {
            robot.mouseRelease(button);
        }
    }

    /**
     * Moves the mouse pointer from one point to another.
     * Uses Bresenham's algorithm.
     *
     * @param robot a robot to use for moving the mouse
     * @param startPoint a start point of the drag
     * @param endPoint an end point of the drag
     */
    public static void mouseMove(Robot robot, Point startPoint, Point endPoint) {
        int dx = endPoint.x - startPoint.x;
        int dy = endPoint.y - startPoint.y;

        int ax = Math.abs(dx) * 2;
        int ay = Math.abs(dy) * 2;

        int sx = signWOZero(dx);
        int sy = signWOZero(dy);

        int x = startPoint.x;
        int y = startPoint.y;

        int d = 0;

        if (ax > ay) {
            d = ay - ax/2;
            while (true){
                robot.mouseMove(x, y);
                robot.delay(50);

                if (x == endPoint.x){
                    return;
                }
                if (d >= 0){
                    y = y + sy;
                    d = d - ax;
                }
                x = x + sx;
                d = d + ay;
            }
        } else {
            d = ax - ay/2;
            while (true){
                robot.mouseMove(x, y);
                robot.delay(50);

                if (y == endPoint.y){
                    return;
                }
                if (d >= 0){
                    x = x + sx;
                    d = d - ay;
                }
                y = y + sy;
                d = d + ax;
            }
        }
    }

    private static int signWOZero(int i){
        return (i > 0)? 1: -1;
    }

    private static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    /** Returns {@code WindowListener} instance that diposes {@code Window} on
     *  "window closing" event.
     *
     * @return    the {@code WindowListener} instance that could be set
     *            on a {@code Window}. After that
     *            the {@code Window} is disposed when "window closed"
     *            event is sent to the {@code Window}
     */
    public static WindowListener getClosingWindowAdapter() {
        return new WindowAdapter () {
            public void windowClosing(WindowEvent e) {
                e.getWindow().dispose();
            }
        };
    }

    /*
     * The values directly map to the ones of
     * sun.awt.X11.XWM & sun.awt.motif.MToolkit classes.
     */
    public final static int
        UNDETERMINED_WM = 1,
        NO_WM = 2,
        OTHER_WM = 3,
        OPENLOOK_WM = 4,
        MOTIF_WM = 5,
        CDE_WM = 6,
        ENLIGHTEN_WM = 7,
        KDE2_WM = 8,
        SAWFISH_WM = 9,
        ICE_WM = 10,
        METACITY_WM = 11,
        COMPIZ_WM = 12,
        LG3D_WM = 13;

    /*
     * Returns -1 in case of not X Window or any problems.
     */
    public static int getWMID() {
        Class clazz = null;
        try {
            if ("sun.awt.X11.XToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) {
                clazz = Class.forName("sun.awt.X11.XWM");
            } else if ("sun.awt.motif.MToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) {
                clazz = Class.forName("sun.awt.motif.MToolkit");
            }
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
        }
        if (clazz == null) {
            return -1;
        }

        try {
            final Class _clazz = clazz;
            Method m_getWMID = (Method)AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
                        try {
                            Method method = _clazz.getDeclaredMethod("getWMID", new Class[] {});
                            if (method != null) {
                                method.setAccessible(true);
                            }
                            return method;
                        } catch (NoSuchMethodException e) {
                            assert false;
                        } catch (SecurityException e) {
                            assert false;
                        }
                        return null;
                    }
                });
            return ((Integer)m_getWMID.invoke(null, new Object[] {})).intValue();
        } catch (IllegalAccessException iae) {
            iae.printStackTrace();
        } catch (InvocationTargetException ite) {
            ite.printStackTrace();
        }
        return -1;
    }


    ////////////////////////////
    // Some stuff to test focus.
    ////////////////////////////

    private static WindowGainedFocusListener wgfListener = new WindowGainedFocusListener();
    private static FocusGainedListener fgListener = new FocusGainedListener();
    private static ActionPerformedListener apListener = new ActionPerformedListener();

    private abstract static class EventListener {
        AtomicBoolean notifier = new AtomicBoolean(false);
        Component comp;
        boolean printEvent;

        public void listen(Component comp, boolean printEvent) {
            this.comp = comp;
            this.printEvent = printEvent;
            notifier.set(false);
            setListener(comp);
        }

        public AtomicBoolean getNotifier() {
            return notifier;
        }

        abstract void setListener(Component comp);

        void printAndNotify(AWTEvent e) {
            if (printEvent) {
                System.err.println(e);
            }
            synchronized (notifier) {
                notifier.set(true);
                notifier.notifyAll();
            }
        }
    }

    private static class WindowGainedFocusListener extends EventListener implements WindowFocusListener {

        void setListener(Component comp) {
            ((Window)comp).addWindowFocusListener(this);
        }

        public void windowGainedFocus(WindowEvent e) {

            ((Window)comp).removeWindowFocusListener(this);
            printAndNotify(e);
        }

        public void windowLostFocus(WindowEvent e) {}
    }

    private static class FocusGainedListener extends EventListener implements FocusListener {

        void setListener(Component comp) {
            comp.addFocusListener(this);
        }

        public void focusGained(FocusEvent e) {
            comp.removeFocusListener(this);
            printAndNotify(e);
        }

        public void focusLost(FocusEvent e) {}
    }

    private static class ActionPerformedListener extends EventListener implements ActionListener {

        void setListener(Component comp) {
            ((Button)comp).addActionListener(this);
        }

        public void actionPerformed(ActionEvent e) {
            ((Button)comp).removeActionListener(this);
            printAndNotify(e);
        }
    }

    private static boolean trackEvent(int eventID, Component comp, Runnable action, int time, boolean printEvent) {
        EventListener listener = null;

        switch (eventID) {
        case WindowEvent.WINDOW_GAINED_FOCUS:
            listener = wgfListener;
            break;
        case FocusEvent.FOCUS_GAINED:
            listener = fgListener;
            break;
        case ActionEvent.ACTION_PERFORMED:
            listener = apListener;
            break;
        }

        listener.listen(comp, printEvent);
        action.run();
        return Util.waitForCondition(listener.getNotifier(), time);
    }

    /*
     * Tracks WINDOW_GAINED_FOCUS event for a window caused by an action.
     * @param window the window to track the event for
     * @param action the action to perform
     * @param time the max time to wait for the event
     * @param printEvent should the event received be printed or doesn't
     * @return true if the event has been received, otherwise false
     */
    public static boolean trackWindowGainedFocus(Window window, Runnable action, int time, boolean printEvent) {
        return trackEvent(WindowEvent.WINDOW_GAINED_FOCUS, window, action, time, printEvent);
    }

    /*
     * Tracks FOCUS_GAINED event for a component caused by an action.
     * @see #trackWindowGainedFocus
     */
    public static boolean trackFocusGained(Component comp, Runnable action, int time, boolean printEvent) {
        return trackEvent(FocusEvent.FOCUS_GAINED, comp, action, time, printEvent);
    }

    /*
     * Tracks ACTION_PERFORMED event for a button caused by an action.
     * @see #trackWindowGainedFocus
     */
    public static boolean trackActionPerformed(Button button, Runnable action, int time, boolean printEvent) {
        return trackEvent(ActionEvent.ACTION_PERFORMED, button, action, time, printEvent);
    }

    /*
     * Requests focus on the component provided and waits for the result.
     * @return true if the component has been focused, false otherwise.
     */
    public static boolean focusComponent(Component comp, int time) {
        return focusComponent(comp, time, false);
    }
    public static boolean focusComponent(final Component comp, int time, boolean printEvent) {
        return trackFocusGained(comp,
                                new Runnable() {
                                    public void run() {
                                        comp.requestFocus();
                                    }
                                },
                                time, printEvent);

    }
}
