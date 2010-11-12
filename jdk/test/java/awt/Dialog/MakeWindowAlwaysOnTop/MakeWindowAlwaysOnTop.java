/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6829546
  @summary tests that an always-on-top modal dialog doesn't make any windows always-on-top
  @author artem.ananiev: area=awt.modal
  @library ../../regtesthelpers
  @build Util
  @run main MakeWindowAlwaysOnTop
*/

import java.awt.*;
import java.awt.event.*;

import test.java.awt.regtesthelpers.Util;

public class MakeWindowAlwaysOnTop
{
    private static Frame f;
    private static Dialog d;

    public static void main(String[] args) throws Exception
    {
        Robot r = Util.createRobot();
        Util.waitForIdle(r);

        // Frame
        f = new Frame("Test frame");
        f.setBounds(100, 100, 400, 300);
        f.setBackground(Color.RED);
        f.setVisible(true);
        r.delay(100);
        Util.waitForIdle(r);

        // Dialog
        d = new Dialog(null, "Modal dialog", Dialog.ModalityType.APPLICATION_MODAL);
        d.setBounds(500, 500, 160, 160);
        d.setAlwaysOnTop(true);
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                d.setVisible(true);
            }
        });
        // Wait until the dialog is shown
        EventQueue.invokeAndWait(new Runnable()
        {
            public void run()
            {
                // Empty
            }
        });
        r.delay(100);
        Util.waitForIdle(r);

        // Click on the frame to trigger modality
        Point p = f.getLocationOnScreen();
        r.mouseMove(p.x + f.getWidth() / 2, p.y + f.getHeight() / 2);
        Util.waitForIdle(r);
        r.mousePress(InputEvent.BUTTON1_MASK);
        Util.waitForIdle(r);
        r.mouseRelease(InputEvent.BUTTON1_MASK);
        Util.waitForIdle(r);

        r.delay(100);
        Util.waitForIdle(r);

        // Dispose dialog
        d.dispose();
        r.delay(100);
        Util.waitForIdle(r);

        // Show another frame at the same location
        Frame t = new Frame("Check");
        t.setBounds(100, 100, 400, 300);
        t.setBackground(Color.BLUE);
        t.setVisible(true);
        r.delay(100);
        Util.waitForIdle(r);

        // Bring it above the first frame
        t.toFront();
        r.delay(100);
        Util.waitForIdle(r);

        Color c = r.getPixelColor(p.x + f.getWidth() / 2, p.y + f.getHeight() / 2);
        System.out.println("Color = " + c);
        System.out.flush();
        // If the color is RED, then the first frame is now always-on-top
        if (Color.RED.equals(c))
        {
            throw new RuntimeException("Test FAILED: the frame is always-on-top");
        }
        else if (!Color.BLUE.equals(c))
        {
            throw new RuntimeException("Test FAILED: unknown window is on top of the frame");
        }
        else
        {
            System.out.println("Test PASSED");
            System.out.flush();
        }

        // Dispose all the windows
        t.dispose();
        f.dispose();
    }
}
