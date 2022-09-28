/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6525850
  @library /java/awt/regtesthelpers
  @build PassFailJFrame
  @summary Iconified frame gets shown after pack()
  @run main/manual ShownOnPack
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;

public class ShownOnPack {

    private static Frame frame;

    public static void createTestUI() {
        frame = new Frame("Should NOT BE SHOWN");

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame,
                PassFailJFrame.Position.HORIZONTAL);

        frame.setExtendedState(Frame.ICONIFIED);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {

        if (!Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.ICONIFIED)) {
            System.out.println("Frame.ICONIFIED state is not supported on "
                    + System.getProperty("os.name"));
            return;
        }

        String testInstruction = """
                This test creates an invisible and iconified frame that
                should not become visible. If you observe the window titled
                'Should NOT BE SHOWN' in the taskbar, press FAIL,
                else press PASS.
                """;

        PassFailJFrame passFailJFrame = new PassFailJFrame("ShownOnPack " +
                "Test Instructions", testInstruction, 5, true, false);
        EventQueue.invokeAndWait(ShownOnPack::createTestUI);
        passFailJFrame.awaitAndCheck();
    }
}

