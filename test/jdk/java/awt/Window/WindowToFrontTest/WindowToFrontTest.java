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

/*
 * @test
 * @bug 4488209
 * @summary JFrame toFront causes the entire frame to be repainted, causes UI
 * to flash
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual WindowToFrontTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class WindowToFrontTest implements ActionListener {
    static Frame frame;
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            1) Click the "toFront" button, this causes the
            "WindowToFrontTest" frame to move front and gets repainted
            completely.
            2) Move "WindowToFrontTest" window and continue to click on "toFront
            multiple times. If the "WindowToFrontTest" Frame content is not
            drawn properly and continues to blink, test is failed
            otherwise passed.
            """;

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .rows(13)
            .columns(40)
            .build();
        EventQueue.invokeAndWait(() -> createUI());
        passFailJFrame.awaitAndCheck();
    }

    private static void createUI() {
        frame = new Frame("WindowToFrontTest");
        frame.setLayout(new BorderLayout());
        frame.setSize(512, 512);
        PassFailJFrame.addTestWindow(frame);
        frame.setVisible(true);

        Frame buttonFrame = new Frame("Test Button");
        Button push = new Button("toFront");
        push.addActionListener(new WindowToFrontTest());
        buttonFrame.add(push);
        buttonFrame.pack();
        PassFailJFrame.addTestWindow(buttonFrame);
        buttonFrame.setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        frame.toFront();
    }
}
