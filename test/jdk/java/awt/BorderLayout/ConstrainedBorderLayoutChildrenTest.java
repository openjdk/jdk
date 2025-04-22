/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4811995
  @summary This makes sure that children in a BorderLayout are given valid bounds.
*/

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * When a BorderLayout is too small to fully accommodate its children's preferred size,
 * we need to make sure the children's bounds are allocated responsibly. This means
 * they should never have a negative (x,y) position or a negative width/height. (They
 * may have a width=0 or height=0, though.
 */
public class ConstrainedBorderLayoutChildrenTest {

    public static void main(String[] args) {
        new ConstrainedBorderLayoutChildrenTest().run();
    }

    private void run() {
        boolean test1 = test1_southChildHasPositiveY();
        boolean test2 = test2_centerChildHasPositiveHeight();
        boolean test3 = test3_competingNorthAndSouthHeights();
        boolean test4 = test4_northChildHasPositiveY_largeInsets();
        boolean test5 = test5_northChildConstrainsHeightForLargeBorder();
        boolean test6 = test6_northChildHasPositiveWidth_largeBorder();

        if (!(test1 && test2 && test3 && test4 && test5 && test6))
            throw new Error("Test failed");
    }

    /**
     * This makes sure the SOUTH component isn't assigned a negative y-value.
     *
     * @return true if the test passes, false if it fails.
     */
    private boolean test1_southChildHasPositiveY() {
        try {
            JPanel container = new JPanel(new BorderLayout());
            JPanel southPanel = createStub(200, 240);
            container.add(southPanel, BorderLayout.SOUTH);
            container.setSize(25, 50);
            container.getLayout().layoutContainer(container);

            // specifically one failure we were seeing is: southPanel's y values was -190.
            assertTrue(southPanel.getY() >= 0,
                    "southPanel.getY() = " + southPanel.getY());

            // Let's also codify the expected bounds. This is not necessarily
            // required/defined in the specs, so it may be OK to change this
            // assertion someday. Here we're expecting southPanel to get the
            // full height of the container but NOT to overflow:
            assertEquals(new Rectangle(0, 0, 25, 50), southPanel.getBounds());

            return true;
        } catch(Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /**
     * This makes sure the CENTER component isn't assigned a negative height.
     * The SOUTH component is going to get the container's full height, and
     * nothing will be leftover for the CENTER component.
     *
     * @return true if the test passes, false if it fails.
     */
    private boolean test2_centerChildHasPositiveHeight() {
        try {
            JPanel container = new JPanel(new BorderLayout());
            JPanel southPanel = createStub(200, 240);
            container.add(southPanel, BorderLayout.SOUTH);
            JPanel centerPanel = createStub(200, 250);
            container.add(centerPanel, BorderLayout.CENTER);
            container.setSize(100, 200);
            container.getLayout().layoutContainer(container);

            // this is the core criteria for this test:
            assertTrue(centerPanel.getHeight() >= 0,
                    "centerPanel.getHeight() = " + centerPanel.getHeight());

            // Let's also codify the expected bounds. This is not necessarily
            // required/defined in the specs, so it may be OK to change this
            // assertion someday. Here we're expecting southPanel to get the
            // full height of the container, and for centerPanel to get a
            // height of zero.
            assertEquals(new Rectangle(0, 0, 100, 200), southPanel.getBounds());
            assertEquals(new Rectangle(0, 0, 100, 0), centerPanel.getBounds());

            return true;
        } catch(Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /**
     * This explores what happens if our NORTH and SOUTH components may overlap when given their
     * preferred sizes.
     *
     * @return true if the test passes, false if it fails.
     */
    private boolean test3_competingNorthAndSouthHeights() {
        try {
            JPanel container = new JPanel(new BorderLayout());
            JPanel northPanel = createStub(200, 200);
            container.add(northPanel, BorderLayout.NORTH);
            JPanel southPanel = createStub(200, 200);
            container.add(southPanel, BorderLayout.SOUTH);
            container.setSize(300, 300);
            container.getLayout().layoutContainer(container);

            // this is the core criteria for this test:
            assertTrue(!northPanel.getBounds().intersects(southPanel.getBounds()),
                    "northPanel.getBounds() = " + northPanel.getBounds() +
                    ", southPanel.getBounds() = " + southPanel.getBounds());

            // Let's also codify the expected bounds. This is not necessarily
            // required/defined in the specs, so it may be OK to change this
            // assertion someday. Here we're expecting northPanel to get its
            // full preferred height, and southPanel gets what is leftover:
            assertEquals(new Rectangle(0,0,300,200), northPanel.getBounds());
            assertEquals(new Rectangle(0,200,300,100), southPanel.getBounds());

            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /**
     * This explores what happens if our container is so small that
     * 2 * border > container.getHeight()
     *
     * @return true if the test passes, false if it fails.
     */
    private boolean test4_northChildHasPositiveY_largeInsets() {
        try {
            JPanel container = new JPanel(new BorderLayout());
            container.setBorder(new EmptyBorder(100, 0, 100, 0));
            JPanel northPanel = createStub(50, 50);
            container.add(northPanel, BorderLayout.NORTH);
            container.setSize(150, 150);
            container.getLayout().layoutContainer(container);

            // if we respect the border: there's 0 height leftover for us:
            assertTrue(northPanel.getHeight() == 0,
                    "northPanel.getHeight() = " + northPanel.getHeight());

            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /**
     * This explores what happens if our container is so small that
     * 2 * border + prefHeight > container.getHeight()
     *
     * @return true if the test passes, false if it fails.
     */
    private boolean test5_northChildConstrainsHeightForLargeBorder() {
        try {
            JPanel container = new JPanel(new BorderLayout());
            container.setBorder(new EmptyBorder(100, 0, 100, 0));
            JPanel northPanel = createStub(50, 50);
            container.add(northPanel, BorderLayout.NORTH);
            container.setSize(150, 225);
            container.getLayout().layoutContainer(container);

            // if we respect the border: we only get 25 pixels
            assertEquals(new Rectangle(0, 100, 150, 25), northPanel.getBounds());

            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /**
     * This explores what happens if our container has large left/right
     * border insets that prevent our NORTH component from showing at all
     *
     * @return true if the test passes, false if it fails.
     */
    private boolean test6_northChildHasPositiveWidth_largeBorder() {
        try {
            JPanel container = new JPanel(new BorderLayout());
            container.setBorder(new EmptyBorder(0, 100, 0, 100));
            JPanel northPanel = createStub(50, 50);
            container.add(northPanel, BorderLayout.NORTH);
            container.setSize(150, 500);
            container.getLayout().layoutContainer(container);

            // if we respect our left/right border: there's no width leftover for us
            assertTrue(northPanel.getWidth() == 0,
                    "northPanel.getWidth() = " + northPanel.getWidth());

            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private void assertTrue(boolean actualValue, String msg) {
        if (!actualValue) {
            throw new AssertionError(msg);
        }
    }

    private void assertEquals(Object expectedValue, Object actualValue) {
        if (!expectedValue.toString().equals(actualValue.toString())) {
            throw new AssertionError("expected = " + expectedValue +
                    ", actual = " + actualValue);
        }
    }

    private JPanel createStub(int prefWidth, int prefHeight) {
        JPanel comp = new JPanel();
        comp.setPreferredSize(new Dimension(prefWidth, prefHeight));
        return comp;
    }
}
