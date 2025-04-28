/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4356756
 * @key multimon
 * @summary Return all screen devices for physical and virtual display devices
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @run main/manual FillThisScreen
 */

public class FillThisScreen {
    private static Frame f;
    private static Button b;
    private static Rectangle oldSize;
    private static boolean fillmode = true;
    static GraphicsDevice[] gs;

    public static void main(String[] args) throws Exception {
        gs = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (gs.length < 2) {
            throw new SkippedException("You have only one monitor in your system" +
                                       " - test skipped");
        }

        String INSTRUCTIONS = """
                This test is for testing the bounds of a multimonitor system.
                You will see a Frame with several buttons: one marked 'Fill
                This Screen' and an additional button for each display on your system.

                First, drag the Frame onto each display and click the
                'Fill This Screen' button.

                The Frame should resize to take up the entire display area
                of the screen it is on, and the button text changes to say,
                'Get Smaller'.

                Click the button again to restore the Frame.

                Next, use the 'Move to screen' buttons to move the Frame to
                each display and again click the 'Fill This Screen' button.

                If the number of 'Move to Screen' buttons is not equals to
                the number of screens on your system, the test fails.

                If the Frame always correctly resizes to take up ONLY the
                entire screen it is on (and not a different screen, or all
                screens), the test passes else it fails.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(FillThisScreen::init)
                .build()
                .awaitAndCheck();
    }

    public static Frame init() {
        Button tempBtn;

        f = new Frame("Drag Me Around");
        f.setLayout(new GridLayout(0, 1));

        b = new Button("Fill This Screen");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fillmode) {
                    oldSize = f.getBounds();
                    Rectangle r = f.getGraphicsConfiguration().getBounds();
                    f.setBounds(r);
                    b.setLabel("Get Smaller");
                } else {
                    f.setBounds(oldSize);
                    b.setLabel("Fill This Screen");
                }
                fillmode = !fillmode;
            }
        });
        f.add(b);

        for (int i = 0; i < gs.length; i++) {
            tempBtn = new Button("Move to screen:" + i);
            tempBtn.addActionListener(new WinMover(i));
            f.add(tempBtn);
        }
        f.setSize(300, 100);
        return f;
    }

    private static class WinMover implements ActionListener {
        int scrNum;

        public WinMover(int scrNum) {
            this.scrNum = scrNum;
        }

        public void actionPerformed(ActionEvent e) {
            Rectangle newBounds = gs[scrNum].getDefaultConfiguration().getBounds();
            f.setLocation(newBounds.x + newBounds.width / 2,
                    newBounds.y + newBounds.height / 2);
        }

    }
}
