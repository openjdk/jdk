/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4187912
  @summary Test that incorrectly written DnD code cannot hang the app
  @key headful
  @run main SkipDropCompleteTest
*/

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;


public class SkipDropCompleteTest {
    SourceFrame sourceFrame;
    TargetFrame targetFrame;
    Point sourceLoc;
    Point targetLoc;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        SkipDropCompleteTest skipDropCompleteTest = new SkipDropCompleteTest();
        EventQueue.invokeAndWait(skipDropCompleteTest::init);
        skipDropCompleteTest.start();
    }

    public void init() {
        sourceFrame = new SourceFrame();
        targetFrame = new TargetFrame();

        sourceLoc = sourceFrame.getLocation();
        Dimension sourceSize = sourceFrame.getSize();
        sourceLoc.x += sourceSize.width / 2;
        sourceLoc.y += sourceSize.height / 2;

        targetLoc = targetFrame.getLocation();
        Dimension targetSize = targetFrame.getSize();
        targetLoc.x += targetSize.width / 2;
        targetLoc.y += targetSize.height / 2;
    }

    public void start() throws AWTException, InterruptedException,
            InvocationTargetException {
        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.delay(1000);
            robot.mouseMove(sourceLoc.x, sourceLoc.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (;!sourceLoc.equals(targetLoc);
                 sourceLoc.translate(sign(targetLoc.x - sourceLoc.x),
                                     sign(targetLoc.y - sourceLoc.y))) {
                robot.mouseMove(sourceLoc.x, sourceLoc.y);
                Thread.sleep(10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (sourceFrame != null) {
                    sourceFrame.dispose();
                }
                if (targetFrame != null) {
                    targetFrame.dispose();
                }
            });
        }

        System.out.println("test passed");
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }
}
