/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Utility routines that wait for a window to be displayed or for
colors to be visible
 * @summary com.apple.junit.utils
 */
package test.java.awt.regtesthelpers;

import java.awt.*;
import java.awt.event.*;

//import junit.framework.Assert;
public class VisibilityValidator {

    // Wait up to five seconds for our window events
    static final int SETUP_PERIOD = 5000;
    static final boolean DEBUG = false;

    volatile Window win = null;
    boolean activated = false;
    boolean opened = false;
    boolean focused = false;
    volatile boolean valid = false;

    //
    // Utility functions that encapsulates normal usage patterns
    //
    public static void setVisibleAndConfirm(Frame testframe) throws Exception {
        setVisibleAndConfirm(testframe, "Could not confirm test frame was "
                + "visible");
    }

    public static void setVisibleAndConfirm(Frame testframe, String msg)
            throws Exception {
        if (testframe.isVisible()) {
            throw new RuntimeException("Frame is already visible");
        }

        VisibilityValidator checkpoint = new VisibilityValidator(testframe);
        testframe.setVisible(true);
        checkpoint.requireVisible();
        if (!checkpoint.isValid()) {
            //System.err.println(msg);
            throw new Exception("Frame not visible after " + SETUP_PERIOD
                    + " milliseconds");
        }
    }

    //
    // Add listeners to the window
    //
    public VisibilityValidator(Window win) {
        this.win = win;
        WindowAdapter watcher = new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                doOpen();
            }

            public void windowActivated(WindowEvent e) {
                doActivate();
            }

            public void windowGainedFocus(WindowEvent e) {
                doGainedFocus();
            }
        };

        win.addWindowListener(watcher);
        win.addWindowFocusListener(watcher);
    }

    // Make the window visible
    //
    // The only way to make it through this routine is for the window to
    // generate BOTH a windowOpened, a windowActivated event and a
    // windowGainedFocus, or to timeout.
    //
    synchronized public void requireVisible() {
        int tries = 0;

        // wait for windowOpened and windowActivated events
        try {
            while ((opened == false)
                    || (activated == false)
                    || (focused == false)) {
                if (tries < 4) {
                    tries += 1;
                    wait(SETUP_PERIOD);
                } else {
                    break;
                }
            }

            if (opened && activated) {
                valid = true;
            } else {
                valid = false;
            }
        } catch (InterruptedException ix) {
            valid = false;
        }

        // Extra-super paranoid checks
        if (win.isVisible() == false) {
            valid = false;
        }

        if (win.isShowing() == false) {
            valid = false;
        }

        if (win.isFocused() == false) {
            valid = false;
        }

        if (DEBUG) {
            if (!isValid()) {
                System.out.println("\tactivated:" + new Boolean(activated));
                System.out.println("\topened:" + new Boolean(opened));
                System.out.println("\tfocused:" + new Boolean(focused));
                System.out.println("\tvalid:" + new Boolean(valid));
                System.out.println("\tisVisible():"
                        + new Boolean(win.isVisible()));
                System.out.println("\tisShowing():"
                        + new Boolean(win.isShowing()));
                System.out.println("\tisFocused():"
                        + new Boolean(win.isFocused()));
            }
        }

    }

    synchronized void doOpen() {
        opened = true;
        notify();
    }

    synchronized void doActivate() {
        activated = true;
        notify();
    }

    synchronized void doGainedFocus() {
        focused = true;
        notify();
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isClear() {
        return valid;
    }

    volatile static Robot robot = null;

    // utility function that waits until a Component is shown with the
    // appropriate color
    public static boolean waitForColor(Component c,
            Color expected) throws AWTException,
            InterruptedException {
        Dimension dim = c.getSize();
        int xOff = dim.width / 2;
        int yOff = dim.height / 2;
        return waitForColor(c, xOff, yOff, expected);
    }

    // utility function that waits for 5 seconds for Component to be shown with
    // the appropriate color
    public static boolean waitForColor(Component c,
            int xOff,
            int yOff,
            Color expected) throws AWTException, InterruptedException {
        return waitForColor(c, xOff, yOff, expected, 5000L);
    }

    // utility function that waits until a Component is up with the appropriate
    // color
    public static boolean waitForColor(Component c,
            int xOff,
            int yOff,
            Color expected,
            long timeout) throws AWTException, InterruptedException {
        Point p = c.getLocationOnScreen();
        int x = (int) p.getX() + xOff;
        int y = (int) p.getY() + yOff;
        return waitForColor(x, y, expected, timeout);
    }

    // utility function that waits until specific screen coords have the
    // appropriate color
    public static boolean waitForColor(int locX,
            int locY,
            Color expected,
            long timeout) throws AWTException, InterruptedException {
        if (robot == null) {
            robot = new Robot();
        }

        long endtime = System.currentTimeMillis() + timeout;
        while (endtime > System.currentTimeMillis()) {
            if (colorMatch(robot.getPixelColor(locX, locY), expected)) {
                return true;
            }
            Thread.sleep(50);
        }

        return false;
    }

    // utility function that asserts that two colors are similar to each other
    public static void assertColorEquals(final String message,
            final Color actual,
            final Color expected) {
        System.out.println("actual color: " + actual);
        System.out.println("expect color: " + expected);
        //Assert.assertTrue(message, colorMatch(actual, expected));
    }

    // determines if two colors are close in hue and brightness
    public static boolean colorMatch(final Color actual, final Color expected) {
        final float[] actualHSB = getHSB(actual);
        final float[] expectedHSB = getHSB(expected);

        final float actualHue = actualHSB[0];
        final float expectedHue = expectedHSB[0];
        final boolean hueMatched = closeMatchHue(actualHue, expectedHue, 0.17f);
        //System.out.println("hueMatched? " + hueMatched);
        final float actualBrightness = actualHSB[2];
        final float expectedBrightness = expectedHSB[2];
        final boolean brightnessMatched = closeMatch(actualBrightness,
                expectedBrightness, 0.15f);
        //System.out.println("brightnessMatched? " + brightnessMatched);

        // check to see if the brightness was so low or so high that the hue
        // got clamped to red
        if (brightnessMatched && !hueMatched) {
            return (expectedBrightness < 0.15f);
        }

        return brightnessMatched && hueMatched;
    }

    static float[] getHSB(final Color color) {
        final float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        return hsb;
    }

    // matches hues from 0.0 to 1.0, accounting for wrap-around at the 1.0/0.0
    // boundry
    static boolean closeMatchHue(final float actual,
            final float expected,
            final float tolerance) {
        if (closeMatch(actual, expected, tolerance)) {
            return true;
        }

        // all that remains is the overflow and underflow cases
        final float expectedHigh = expected + tolerance;
        final float expectedLow = expected - tolerance;

        if (expectedHigh > 1.0f) {
            // expected is too high, and actual was too low
            //System.out.println("\thue expected too high, actual too low");
            return closeMatch(actual + 0.5f, expected - 0.5f, tolerance);
        }

        if (expectedLow < 0.0f) {
            // expected is too low, and actual was too high
            //System.out.println("\thue expected too low, actual too high");
            return closeMatch(actual - 0.5f, expected + 0.5f, tolerance);
        }

        //System.out.println("\tcloseMatchHue? " + false);
        return false;
    }

    static boolean closeMatch(final float actual,
            final float expected,
            final float tolerance) {
        return (expected + tolerance) > actual && (expected - tolerance) < actual;
    }
}
