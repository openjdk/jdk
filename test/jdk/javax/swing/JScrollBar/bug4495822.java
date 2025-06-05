/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4495822
 * @summary AdjustmentEvent.getValueIsAdjusting() always returns false
 * @run main bug4495822
 */

public class bug4495822 {
    public static volatile boolean isAdjusted = false;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            JScrollBar scrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
            scrollBar.addAdjustmentListener(new AdjustmentListener() {
                public void adjustmentValueChanged(AdjustmentEvent e) {
                    if (e.getValueIsAdjusting() != scrollBar.getValueIsAdjusting()) {
                        throw new RuntimeException("The AdjustmentEvent has incorrect \"valueIsAdjusting\" value");
                    }

                    isAdjusted = true;
                }
            });

            scrollBar.setValueIsAdjusting(true);
        });
        Thread.sleep(1000);
        if (!isAdjusted) {
            throw new RuntimeException("adjustmentValueChanged() not invoked!");
        }
        System.out.println("Test Passed!");
    }
}
