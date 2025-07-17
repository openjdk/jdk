/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;

/*
 * @test
 * @bug 6225472 6682536
 * @requires (os.family != "linux")
 * @summary Tests that non-focusable Frame in full-screen mode overlaps the task bar.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NonfocusableFrameFullScreenTest
 */

public class NonfocusableFrameFullScreenTest extends JPanel {
    boolean fullscreen = false;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                1. Press "Show Frame" button to show a Frame with two buttons.

                2. Press the button "To Full Screen" to bring the frame to
                   full-screen mode:

                        The frame should overlap the taskbar

                3. Press "To Windowed" button:
                        The frame should return to its original size.
                        The frame shouldn't be alwaysOnTop.

                4. Press "Set Always On Top" button and make sure the frame
                   is alwaysOnTop, then press "To Full Screen" button
                   and then "To Windowed" button:

                        The frame should return to its original size keeping alwaysOnTop
                        state on.

                Press Pass if everything is as expected.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(NonfocusableFrameFullScreenTest::new)
                .build()
                .awaitAndCheck();
    }

    private NonfocusableFrameFullScreenTest() {
        Button b = new Button("Show Frame");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showFrame();
            }
        });
        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 100);
    }

    public void showFrame() {
        Frame frame = new Frame("Test Frame");

        Button button = new Button("To Full Screen");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fullscreen) {
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().
                            setFullScreenWindow(null);
                    button.setLabel("To Full Screen");
                    fullscreen = false;
                } else {
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().
                            setFullScreenWindow(frame);
                    button.setLabel("To Windowed");
                    fullscreen = true;
                }
                frame.validate();
            }
        });

        Button button2 = new Button("Set Always On Top");
        button2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (frame.isAlwaysOnTop()) {
                    button2.setLabel("Set Always On Top");
                    frame.setAlwaysOnTop(false);
                } else {
                    button2.setLabel("Set Not Always On Top");
                    frame.setAlwaysOnTop(true);
                }
                frame.validate();
            }
        });

        frame.setLayout(new BorderLayout());
        frame.add(button, BorderLayout.WEST);
        frame.add(button2, BorderLayout.EAST);
        frame.setBounds(400, 200, 350, 100);
        frame.setFocusableWindowState(false);
        frame.setVisible(true);
    }
}
