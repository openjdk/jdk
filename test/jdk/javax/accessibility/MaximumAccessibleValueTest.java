/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4422362
 * @summary Wrong Max Accessible Value with BoundedRangeModel components
 * @run main MaximumAccessibleValueTest
 */

import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

public class MaximumAccessibleValueTest {

    public static void doTest() {

        JScrollBar jScrollBar = new JScrollBar();
        JProgressBar jProgressBar = new JProgressBar();
        JSlider jSlider = new JSlider();

        if (((Integer) jScrollBar.getAccessibleContext().getAccessibleValue()
            .getMaximumAccessibleValue()).intValue() != jScrollBar.getMaximum()
            - jScrollBar.getVisibleAmount()) {
            throw new RuntimeException(
                "Wrong MaximumAccessibleValue returned by JScrollBar");
        }

        if (((Integer) jProgressBar.getAccessibleContext().getAccessibleValue()
            .getMaximumAccessibleValue().intValue()) != (jProgressBar
                .getMaximum() - jProgressBar.getModel().getExtent())) {
            throw new RuntimeException(
                "Wrong MaximumAccessibleValue returned by JProgressBar");
        }

        if (((Integer) jSlider.getAccessibleContext().getAccessibleValue()
            .getMaximumAccessibleValue()).intValue() != jSlider.getMaximum()
            - jSlider.getModel().getExtent()) {
            throw new RuntimeException(
                "Wrong MaximumAccessibleValue returned by JSlider");
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> doTest());
        System.out.println("Test Passed");
    }
}

