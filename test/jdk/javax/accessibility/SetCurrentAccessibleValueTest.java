/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4422535
 * @summary setCurrentAccessibleValue returns true only for an Integer
 * @run main SetCurrentAccessibleValueTest
 */

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

public class SetCurrentAccessibleValueTest {

    public static void doTest() {
        JComponent[] jComponents =
        { new JButton(), new JInternalFrame(), new JSplitPane(),
            new JScrollBar(), new JProgressBar(), new JSlider() };

        for (JComponent jComponent : jComponents) {
            testIt(jComponent, (Float.valueOf(5)));
            testIt(jComponent, (Double.valueOf(37.266)));
            testIt(jComponent, (Integer.valueOf(10)));
            testIt(jComponent, (Long.valueOf(123L)));
            testIt(jComponent, (Short.valueOf((short) 123)));
            testIt(jComponent, (BigInteger.ONE));
            testIt(jComponent, (new BigDecimal(BigInteger.ONE)));
        }

    }

    static void testIt(JComponent jComponent, Number number) {
        if (!jComponent.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(number)) {
            throw new RuntimeException(jComponent.getClass().getName()
                + " Accessible Value implementation doesn't accept "
                + number.getClass().getName());
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> doTest());
        System.out.println("Test Passed");
    }
}

