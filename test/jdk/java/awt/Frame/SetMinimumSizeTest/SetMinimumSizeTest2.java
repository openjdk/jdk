/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify frame resizes back to minimumSize on calling pack
 * @run main SetMinimumSizeTest2
 */

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;

public class SetMinimumSizeTest2 {

    private static Frame frame;
    private static volatile Dimension dimension;
    private static volatile Dimension actualDimension;

    public static void createGUI() {
        frame = new Frame();
        frame.add(new Button("Button"));
        frame.setMinimumSize(new Dimension(140, 140));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void doTest() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> createGUI());

            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                dimension = frame.getSize();
            });

            EventQueue.invokeAndWait(() -> {
                frame.setSize(dimension.width + 20, dimension.height + 20);
                frame.invalidate();
                frame.validate();
            });

            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                frame.pack();
                frame.invalidate();
                frame.validate();
            });

            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                actualDimension = frame.getSize();
            });

            if (!actualDimension.equals(dimension)) {
                throw new RuntimeException("Test Failed\n"
                    + "expected dimension:(" + dimension.width + "," + dimension.height +")\n"
                    + "actual dimension:(" + actualDimension.width + "," + actualDimension.height + ")");
            }
        } finally {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }
    }

    public static void main(String[] args) throws Exception {
        doTest();
    }
}

