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

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.GraphicsDevice;
import java.awt.Toolkit;
import java.awt.Point;
import java.awt.MouseInfo;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * ExtendedRobot is a subclass of {@link java.awt.Robot}. It provides some convenience methods that are
 * ought to be moved to {@link java.awt.Robot} class.
 * <p>
 * ExtendedRobot uses delay {@link #getSyncDelay()} to make syncing threads with {@link #waitForIdle()}
 * more stable. This delay can be set once on creating object and could not be changed throughout object
 * lifecycle. Constructor reads vm integer property {@code java.awt.robotdelay} and sets the delay value
 * equal to the property value. If the property was not set 500 milliseconds default value is used.
 * <p>
 * When using jtreg you would include this class via something like:
 * <pre>
 * {@literal @}library ../../../../lib/testlibrary
 * {@literal @}build ExtendedRobot
 * </pre>
 *
 * @author      Dmitriy Ermashov
 * @since       9
 */

public class ExtendedRobot extends Robot {
    private static int DEFAULT_SYNC_DELAY = 500; // Default Additional delay for waitForIdle()

    private final int syncDelay = DEFAULT_SYNC_DELAY;

    /**
     * Constructs an ExtendedRobot object in the coordinate system of the primary screen.
     *
     * @throws  AWTException if the platform configuration does not allow low-level input
     *          control. This exception is always thrown when
     *          GraphicsEnvironment.isHeadless() returns true
     *
     * @see     java.awt.GraphicsEnvironment#isHeadless
     */
    public ExtendedRobot() throws AWTException {
        super();
    }

    /**
     * Creates an ExtendedRobot for the given screen device. Coordinates passed
     * to ExtendedRobot method calls like mouseMove and createScreenCapture will
     * be interpreted as being in the same coordinate system as the specified screen.
     * Note that depending on the platform configuration, multiple screens may either:
     * <ul>
     * <li>share the same coordinate system to form a combined virtual screen</li>
     * <li>use different coordinate systems to act as independent screens</li>
     * </ul>
     * This constructor is meant for the latter case.
     * <p>
     * If screen devices are reconfigured such that the coordinate system is
     * affected, the behavior of existing ExtendedRobot objects is undefined.
     *
     * @param   screen  A screen GraphicsDevice indicating the coordinate
     *                  system the Robot will operate in.
     * @throws  AWTException if the platform configuration does not allow low-level input
     *          control. This exception is always thrown when
     *          GraphicsEnvironment.isHeadless() returns true.
     * @throws  IllegalArgumentException if {@code screen} is not a screen
     *          GraphicsDevice.
     *
     * @see     java.awt.GraphicsEnvironment#isHeadless
     * @see     GraphicsDevice
     */
    public ExtendedRobot(GraphicsDevice screen) throws AWTException {
        super(screen);
    }

    /**
     * Returns delay length for {@link #waitForIdle()} method
     *
     * @return  Current delay value
     *
     * @see     #waitForIdle()
     */
    public int getSyncDelay() {
        return this.syncDelay;
    }

    /**
     * Moves mouse pointer to given screen coordinates.
     *
     * @param   position    Target position
     *
     * @see     java.awt.Robot#mouseMove(int, int)
     */
    public synchronized void mouseMove(Point position) {
        mouseMove(position.x, position.y);
    }

    /**
     * Emulate native drag and drop process using {@code InputEvent.BUTTON1_DOWN_MASK}.
     * The method successively moves mouse cursor to point with coordinates
     * ({@code fromX}, {@code fromY}), presses mouse button 1, drag mouse to
     * point with coordinates ({@code toX}, {@code toY}) and releases mouse
     * button 1 at last.
     *
     * @param   fromX   Source point x coordinate
     * @param   fromY   Source point y coordinate
     * @param   toX     Destination point x coordinate
     * @param   toY     Destination point y coordinate
     *
     * @see     #mousePress(int)
     * @see     #glide(int, int, int, int)
     */
    public void dragAndDrop(int fromX, int fromY, int toX, int toY){
        mouseMove(fromX, fromY);
        mousePress(InputEvent.BUTTON1_DOWN_MASK);
        waitForIdle();
        glide(toX, toY);
        mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        waitForIdle();
    }

    /**
     * Emulate native drag and drop process using {@code InputEvent.BUTTON1_DOWN_MASK}.
     * The method successively moves mouse cursor to point {@code from},
     * presses mouse button 1, drag mouse to point {@code to} and releases
     * mouse button 1 at last.
     *
     * @param   from    Source point
     * @param   to      Destination point
     *
     * @see     #mousePress(int)
     * @see     #glide(int, int, int, int)
     * @see     #dragAndDrop(int, int, int, int)
     */
    public void dragAndDrop(Point from, Point to){
        dragAndDrop(from.x, from.y, to.x, to.y);
    }
}
