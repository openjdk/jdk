/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6510914
 * @summary  Verifies if JScrollBar.getMinimumSize() honours setMinimumSize()
 * @run main JScrollBarSizeTest
 */
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import java.awt.Dimension;

public class JScrollBarSizeTest {
    private static volatile Dimension min;
    private static volatile Dimension max;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JScrollBar bar = new JScrollBar(SwingConstants.HORIZONTAL);
            bar.setMinimumSize(new Dimension(75, 0));
            min = bar.getMinimumSize();
            bar.setMaximumSize(new Dimension(375, 0));
            max = bar.getMaximumSize();
        });
        if (min.width != 75) {
            throw new RuntimeException("Minimum width not same as previously set");
        }
        if (max.width != 375) {
            throw new RuntimeException("Maximum width not same as previously set");
        }
    }
}
