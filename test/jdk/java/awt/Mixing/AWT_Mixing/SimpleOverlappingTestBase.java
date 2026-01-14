/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SpringLayout;

import test.java.awt.regtesthelpers.Util;

/**
 * Base class for testing overlapping of Swing and AWT component put into one frame.
 * Validates drawing and event delivery at the components intersection.
 * <p> See base class for usage
 *
 * @author Sergey Grinev
*/
public abstract class SimpleOverlappingTestBase extends OverlappingTestBase {

    {
        testEmbeddedFrame = true;
    }

    /**
     * Event delivery validation. If set to true (default) tested lightweight component will be provided
     * with mouse listener which should be called in order for test to pass.
     */
    protected final boolean useDefaultClickValidation;

    /**
     * Constructor which sets {@link SimpleOverlappingTestBase#useDefaultClickValidation }
     * @param defaultClickValidation
     */
    protected SimpleOverlappingTestBase(boolean defaultClickValidation) {
        super();
        this.useDefaultClickValidation = defaultClickValidation;
    }

    protected boolean isMultiFramesTest(){
        return true;
    }

    public SimpleOverlappingTestBase() {
        this(true);
    }

    //overridables
    /**
     * Successors override this method providing swing component for testing
     * @return swing component to test
     */
    protected abstract JComponent getSwingComponent();

    /**
     * For tests debugging. Please, ignore.
     */
    protected static final boolean debug = false;

    /**
     * Should be set to true if test isn't using {@link SimpleOverlappingTestBase#useDefaultClickValidation }
     */
    protected volatile boolean wasLWClicked = false;

    /**
     * Current tested lightweight component
     * @see SimpleOverlappingTestBase#getSwingComponent()
     */
    protected JComponent testedComponent;

    /**
     * Setups simple frame with lightweight component returned by {@link SimpleOverlappingTestBase#getSwingComponent() }
     * Called by base class.
     */
    protected void prepareControls() {
        wasLWClicked = false;

        final JFrame f = new JFrame("Mixing : Simple Overlapping test");
        f.setLayout(new SpringLayout());
        f.setSize(200, 200);

        testedComponent = getSwingComponent();

        if (useDefaultClickValidation) {
            testedComponent.addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    wasLWClicked = true;
                    f.setVisible(false);
                }
            });
        }

        if (!debug) {
            f.add(testedComponent);
        } else {
            System.err.println("Warning: DEBUG MODE");
        }

        propagateAWTControls(f);

        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    /**
     * AWT Robot instance. Shouldn't be used in most cases.
     */
    protected Robot robot;

    /**
     * Run test by {@link OverlappingTestBase#clickAndBlink(java.awt.Robot, java.awt.Point) } validation for current lightweight component.
     * <p>Called by base class.
     * @return true if test passed
     */
    protected boolean performTest() {
        testedComponent.requestFocus();

        // run robot
        robot = Util.createRobot();
        robot.setAutoDelay(20);

        // get coord
        Point lLoc = !debug ? testedComponent.getLocationOnScreen() : new Point(70, 30);
        Util.waitForIdle(robot);
        /* this is a workaround for certain jtreg(?) focus issue:
           tests fail starting after failing mixing tests but always pass alone.
         */
        JFrame ancestor = (JFrame) (testedComponent.getTopLevelAncestor());
        if (ancestor != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            ancestor.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) {
                    latch.countDown();
                }
            });
            ancestor.requestFocus();
            try {
                if (!latch.await(1L, TimeUnit.SECONDS)) {
                    throw new RuntimeException(
                            "Ancestor frame didn't receive focus");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        clickAndBlink(robot, lLoc);
        if (ancestor != null && isMultiFramesTest()) {
            ancestor.dispose();
        }

        return wasLWClicked;
    }

    public boolean isOel7orLater() {
        if (System.getProperty("os.name").toLowerCase().contains("linux") &&
            System.getProperty("os.version").toLowerCase().contains("el")) {
            Pattern p = Pattern.compile("el(\\d+)");
            Matcher m = p.matcher(System.getProperty("os.version"));
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1)) >= 7;
                } catch (NumberFormatException nfe) {}
            }
        }
        return false;
    }
}
