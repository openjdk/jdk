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

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.ComponentOrientation;

/*
 * @test
 * @bug 4467063
 * @summary JScrollPane.setCorner() causes IllegalArgumentException. (invalid corner key)
 * @run main bug4467063
 */

public class bug4467063 {

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane sp = new JScrollPane();

            //Test corners for left-to-right orientation
            sp.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            sp.setCorner(JScrollPane.LOWER_LEADING_CORNER, new JButton("0"));
            sp.setCorner(JScrollPane.LOWER_TRAILING_CORNER, new JButton("1"));
            sp.setCorner(JScrollPane.UPPER_LEADING_CORNER, new JButton("2"));
            sp.setCorner(JScrollPane.UPPER_TRAILING_CORNER, new JButton("3"));

            if (!sp.getCorner(JScrollPane.LOWER_LEADING_CORNER).equals(
                    sp.getCorner(JScrollPane.LOWER_LEFT_CORNER))) {
                throw new RuntimeException("Incorrect LOWER_LEADING_CORNER value");
            }

            if (!sp.getCorner(JScrollPane.LOWER_TRAILING_CORNER).equals(
                    sp.getCorner(JScrollPane.LOWER_RIGHT_CORNER))) {
                throw new RuntimeException("Incorrect LOWER_TRAILING_CORNER value");
            }

            if (!sp.getCorner(JScrollPane.UPPER_LEADING_CORNER).equals(
                    sp.getCorner(JScrollPane.UPPER_LEFT_CORNER))) {
                throw new RuntimeException("Incorrect UPPER_LEADING_CORNER value");
            }

            if (!sp.getCorner(JScrollPane.UPPER_TRAILING_CORNER).equals(
                    sp.getCorner(JScrollPane.UPPER_RIGHT_CORNER))) {
                throw new RuntimeException("Incorrect UPPER_TRAILING_CORNER value");
            }

            //Test corners for right-to-left orientation
            sp.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            sp.setCorner(JScrollPane.LOWER_LEADING_CORNER, new JButton("0"));
            sp.setCorner(JScrollPane.LOWER_TRAILING_CORNER, new JButton("1"));
            sp.setCorner(JScrollPane.UPPER_LEADING_CORNER, new JButton("2"));
            sp.setCorner(JScrollPane.UPPER_TRAILING_CORNER, new JButton("3"));

            if (!sp.getCorner(JScrollPane.LOWER_LEADING_CORNER).equals(
                    sp.getCorner(JScrollPane.LOWER_RIGHT_CORNER))) {
                throw new RuntimeException("Incorrect LOWER_LEADING_CORNER value");
            }

            if (!sp.getCorner(JScrollPane.LOWER_TRAILING_CORNER).equals(
                    sp.getCorner(JScrollPane.LOWER_LEFT_CORNER))) {
                throw new RuntimeException("Incorrect LOWER_TRAILING_CORNER value");
            }

            if (!sp.getCorner(JScrollPane.UPPER_LEADING_CORNER).equals(
                    sp.getCorner(JScrollPane.UPPER_RIGHT_CORNER))) {
                throw new RuntimeException("Incorrect UPPER_LEADING_CORNER value");
            }

            if (!sp.getCorner(JScrollPane.UPPER_TRAILING_CORNER).equals(
                    sp.getCorner(JScrollPane.UPPER_LEFT_CORNER))) {
                throw new RuntimeException("Incorrect UPPER_TRAILING_CORNER value");
            }
        });
        System.out.println("Test Passed!");
    }
}
