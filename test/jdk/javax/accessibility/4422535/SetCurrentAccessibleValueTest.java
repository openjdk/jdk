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
 * @key headful
 * @bug 4422535
 * @summary setCurrentAccessibleValue returns true only for an Integer
 * @run main SetCurrentAccessibleValueTest
 */

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

public class SetCurrentAccessibleValueTest {

    public static void doTest() {

        JButton jButton = new JButton();
        JInternalFrame iFrame = new JInternalFrame();
        JSplitPane jSplitPane = new JSplitPane();
        JScrollBar jScrollBar = new JScrollBar();
        JProgressBar jProgressBar = new JProgressBar();
        JSlider jSlider = new JSlider();

        if (!jButton.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("JButton's Accessible Value"
                + " implementation doesn't accept Integer");
        }
        if (!jButton.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Float.valueOf(5))) {
            throw new RuntimeException("JButton's Accessible Value"
                + " implementation doesn't accept Float");
        }
        if (!jButton.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Double.valueOf(5))) {
            throw new RuntimeException("JButton's Accessible Value"
                + " implementation doesn't accept Double");
        }
        if (!jButton.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Long.valueOf(5))) {
            throw new RuntimeException("JButton's Accessible Value"
                + " implementation doesn't accept Long");
        }
        if (!jButton.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Short.valueOf((short) 123))) {
            throw new RuntimeException("JButton's Accessible Value"
                + " implementation doesn't accept Short");
        }

        if (!jButton.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(BigInteger.ONE)) {
            throw new RuntimeException("JButton's Accessible Value"
                + " implementation doesn't accept BigInteger.ONE");
        }

        if (!jButton.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(new BigDecimal(BigInteger.ONE))) {
            throw new RuntimeException("JButton's Accessible Value"
                + " implementation doesn't accept new "
		+ "BigDecimal(BigInteger.ONE)");
        }

        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("JScrollBar's Accessible Value"
                + " implementation doesn't accept Integer");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Float.valueOf(5))) {
            throw new RuntimeException("JScrollBar's Accessible Value"
                + " implementation doesn't accept Float");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Double.valueOf(5))) {
            throw new RuntimeException("JScrollBar's Accessible Value"
                + " implementation doesn't accept Double");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Long.valueOf(5))) {
            throw new RuntimeException("JScrollBar's Accessible Value"
                + " implementation doesn't accept Long");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Short.valueOf((short) 123))) {
            throw new RuntimeException("JScrollBar's Accessible Value"
                + " implementation doesn't accept Short");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(BigInteger.ONE)) {
            throw new RuntimeException("JScrollBar's Accessible Value"
                + " implementation doesn't accept BigInteger.ONE");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(new BigDecimal(BigInteger.ONE))) {
            throw new RuntimeException("JScrollBar's Accessible Value"
                + " implementation doesn't accept new "
		+ "BigDecimal(BigInteger.ONE)");
        }

        if (!iFrame.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("iFrame's Accessible Value"
                + " implementation doesn't accept Integer");
        }
        if (!iFrame.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Float.valueOf(5))) {
            throw new RuntimeException("iFrame's Accessible Value"
                + " implementation doesn't accept Float");
        }
        if (!iFrame.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Double.valueOf(5))) {
            throw new RuntimeException("iFrame's Accessible Value"
                + " implementation doesn't accept Double");
        }
        if (!iFrame.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Long.valueOf(5))) {
            throw new RuntimeException("iFrame's Accessible Value"
                + " implementation doesn't accept Long");
        }
        if (!iFrame.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Short.valueOf((short) 123))) {
            throw new RuntimeException("iFrame's Accessible Value"
                + " implementation doesn't accept Short");
        }
        if (!iFrame.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(BigInteger.ONE)) {
            throw new RuntimeException("iFrame's Accessible Value"
                + " implementation doesn't accept BigInteger.ONE");
        }
        if (!iFrame.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(new BigDecimal(BigInteger.ONE))) {
            throw new RuntimeException("iFrame's Accessible Value"
                + " implementation doesn't accept new "
		+"BigDecimal(BigInteger.ONE)");
        }

        if (!jSplitPane.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("jSplitPane's Accessible Value"
                + " implementation doesn't accept Integer");
        }
        if (!jSplitPane.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Float.valueOf(5))) {
            throw new RuntimeException("jSplitPane's Accessible Value"
                + " implementation doesn't accept Float");
        }
        if (!jSplitPane.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Double.valueOf(5))) {
            throw new RuntimeException("jSplitPane's Accessible Value"
                + " implementation doesn't accept Double");
        }
        if (!jSplitPane.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Long.valueOf(5))) {
            throw new RuntimeException("jSplitPane's Accessible Value"
                + " implementation doesn't accept Long");
        }
        if (!jSplitPane.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Short.valueOf((short) 123))) {
            throw new RuntimeException("jSplitPane's Accessible Value"
                + " implementation doesn't accept Short");
        }
        if (!jSplitPane.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(BigInteger.ONE)) {
            throw new RuntimeException("jSplitPane's Accessible Value"
                + " implementation doesn't accept BigInteger.ONE");
        }
        if (!jSplitPane.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(new BigDecimal(BigInteger.ONE))) {
            throw new RuntimeException("jSplitPane's Accessible Value"
                + " implementation doesn't accept new "
		+ "BigDecimal(BigInteger.ONE)");
        }

        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("jScrollBar's Accessible Value"
                + " implementation doesn't accept Integer");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Float.valueOf(5))) {
            throw new RuntimeException("jScrollBar's Accessible Value"
                + " implementation doesn't accept Float");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Double.valueOf(5))) {
            throw new RuntimeException("jScrollBar's Accessible Value"
                + " implementation doesn't accept Double");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Long.valueOf(5))) {
            throw new RuntimeException("jScrollBar's Accessible Value"
                + " implementation doesn't accept Long");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Short.valueOf((short) 123))) {
            throw new RuntimeException("jScrollBar's Accessible Value"
                + " implementation doesn't accept Short");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(BigInteger.ONE)) {
            throw new RuntimeException("jScrollBar's Accessible Value"
                + " implementation doesn't accept BigInteger.ONE");
        }
        if (!jScrollBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(new BigDecimal(BigInteger.ONE))) {
            throw new RuntimeException("jScrollBar's Accessible Value"
                + " implementation doesn't accept new "
		+ "BigDecimal(BigInteger.ONE)");
        }

        if (!jProgressBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("jProgressBar's Accessible Value"
                + " implementation doesn't accept Integer");
        }
        if (!jProgressBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Float.valueOf(5))) {
            throw new RuntimeException("jProgressBar's Accessible Value"
                + " implementation doesn't accept Float");
        }
        if (!jProgressBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Double.valueOf(5))) {
            throw new RuntimeException("jProgressBar's Accessible Value"
                + " implementation doesn't accept Double");
        }
        if (!jProgressBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Long.valueOf(5))) {
            throw new RuntimeException("jProgressBar's Accessible Value"
                + " implementation doesn't accept Long");
        }
        if (!jProgressBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Short.valueOf((short) 123))) {
            throw new RuntimeException("jProgressBar's Accessible Value"
                + " implementation doesn't accept Short");
        }
        if (!jProgressBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(BigInteger.ONE)) {
            throw new RuntimeException("jProgressBar's Accessible Value"
                + " implementation doesn't accept BigInteger.ONE");
        }
        if (!jProgressBar.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(new BigDecimal(BigInteger.ONE))) {
            throw new RuntimeException("jProgressBar's Accessible Value"
                + " implementation doesn't accept new "
		+ "BigDecimal(BigInteger.ONE)");
        }

        if (!jSlider.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("jSlider's Accessible Value"
                + " implementation doesn't accept Integer");
        }
        if (!jSlider.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Float.valueOf(5))) {
            throw new RuntimeException("jSlider's Accessible Value"
                + " implementation doesn't accept Float");
        }
        if (!jSlider.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Double.valueOf(5))) {
            throw new RuntimeException("jSlider's Accessible Value"
                + " implementation doesn't accept Double");
        }
        if (!jSlider.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Long.valueOf(5))) {
            throw new RuntimeException("jSlider's Accessible Value"
                + " implementation doesn't accept Long");
        }
        if (!jSlider.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(Short.valueOf((short) 123))) {
            throw new RuntimeException("jSlider's Accessible Value"
                + " implementation doesn't accept Short");
        }
        if (!jSlider.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(BigInteger.ONE)) {
            throw new RuntimeException("jSlider's Accessible Value"
                + " implementation doesn't accept BigInteger.ONE");
        }
        if (!jSlider.getAccessibleContext().getAccessibleValue()
            .setCurrentAccessibleValue(new BigDecimal(BigInteger.ONE))) {
            throw new RuntimeException("jSlider's Accessible Value"
                + " implementation doesn't accept new "
		+ "BigDecimal(BigInteger.ONE)");
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> doTest());
        System.out.println("Test Passed");
    }
}

