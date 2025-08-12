/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/*
 * @test
 * @bug 4473671
 * @summary Test to verify GraphicsEnvironment.getDefaultScreenDevice always
 *          returning first screen
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DefaultScreenDeviceTest
 */

public class DefaultScreenDeviceTest {
    private static Frame testFrame;

    public static void main(String[] args) throws Exception {
        GraphicsEnvironment ge = GraphicsEnvironment.
                getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();
        if (gds.length < 2) {
            System.out.println("Test requires at least 2 displays");
            return;
        }

        String INSTRUCTIONS = """
                1. The test is for systems which allows primary display
                   selection in multiscreen systems.
                   Set the system primary screen to be the rightmost
                   (i.e. the right screen in two screen configuration)
                   This can be done by going to OS Display Settings
                   selecting the screen and checking the 'Use this device
                   as primary monitor' checkbox.
                2. When done, click on 'Frame on Primary Screen' button and
                   see where the frame will pop up
                3. If Primary Frame pops up on the primary display,
                   the test passed, otherwise it failed
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    private static List<Frame> initialize() {
        Frame frame = new Frame("Default screen device test");
        GraphicsConfiguration gc =
                GraphicsEnvironment.getLocalGraphicsEnvironment().
                        getDefaultScreenDevice().getDefaultConfiguration();

        testFrame = new Frame("Primary screen frame", gc);
        frame.setLayout(new BorderLayout());
        frame.setSize(200, 200);

        Button b = new Button("Frame on Primary Screen");
        b.addActionListener(e -> {
            if (testFrame != null) {
                testFrame.setVisible(false);
                testFrame.dispose();
            }

            testFrame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e1) {
                    testFrame.setVisible(false);
                    testFrame.dispose();
                }
            });
            testFrame.add(new Label("This frame should be on the primary screen"));
            testFrame.setBackground(Color.red);
            testFrame.pack();
            Rectangle rect = gc.getBounds();
            testFrame.setLocation(rect.x + 100, rect.y + 100);
            testFrame.setVisible(true);
        });
        frame.add(b);
        return List.of(testFrame, frame);
    }
}
