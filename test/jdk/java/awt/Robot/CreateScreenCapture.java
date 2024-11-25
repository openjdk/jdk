/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4292503
 * @summary OutOfMemoryError with lots of Robot.createScreenCapture
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "linux")
 * @run main/manual CreateScreenCapture
*/

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextArea;

public class CreateScreenCapture {

    static TextArea messageText;

    private static final String INSTRUCTIONS = """
         This test is linux only!
         Once you see these instructions, run 'top' program.
         Watch for java process.
         The memory size used by this process should stop growing after several steps.
         Numbers of steps test is performing are displayed in output window.
         After 5-7 steps the size taken by the process should become stable.
         If this happens, then test passed otherwise test failed.

         Small oscillations of the memory size are, however, acceptable.""";

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        PassFailJFrame passFail = new PassFailJFrame(INSTRUCTIONS);
        Dialog dialog = new Dialog(new Frame(), "Instructions");
        messageText = new TextArea("", 5, 80, TextArea.SCROLLBARS_BOTH);
        dialog.add(messageText);
        PassFailJFrame.addTestWindow(dialog);
        PassFailJFrame.positionTestWindow(dialog, PassFailJFrame.Position.HORIZONTAL);
        dialog.setSize(200, 300);
        dialog.setVisible(true);
        Rectangle rect = new Rectangle(0, 0, 1000, 1000);
        for (int i = 0; i < 100; i++) {
            Image image = robot.createScreenCapture(rect);
            image.flush();
            image = null;
            robot.delay(200);
            log("step #" + i);
        }
        passFail.awaitAndCheck();
    }

    private static void log(String messageIn) {
        messageText.append(messageIn + "\n");
    }
}

