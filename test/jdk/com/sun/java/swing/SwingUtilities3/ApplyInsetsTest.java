/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Insets;
import java.awt.Rectangle;

import com.sun.java.swing.SwingUtilities3;

/*
 * @test
 * @bug 8365379
 * @summary Verify SwingUtilities3 insets return correct result
 *          independent of initial values
 * @modules java.desktop/com.sun.java.swing
 * @run main ApplyInsetsTest
 */

public class ApplyInsetsTest {
    public static void main(String[] args) {
        Rectangle rect = new Rectangle(10, 20, 60, 60);
        Insets insets = new Insets(5, 10, 15, 20);
        Rectangle expected =
                new Rectangle(rect.x + insets.left,
                              rect.y + insets.top,
                              rect.width - (insets.left + insets.right),
                              rect.height - (insets.top + insets.bottom));

        SwingUtilities3.applyInsets(rect, insets);
        if (!rect.equals(expected)) {
            throw new RuntimeException("Test failed: expected " + expected +
                                       " but got " + rect);
        }

        // Right to left test
        rect.setRect(10, 20, 60, 60);
        expected.x = rect.x + insets.right;
        SwingUtilities3.applyInsets(rect, insets, false);
        if (!rect.equals(expected)) {
            throw new RuntimeException("Right to left test failed: expected "
                                       + expected + " but got " + rect);
        }

        System.out.println("Test passed.");
    }
}
