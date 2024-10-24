/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4084997
 * @summary See if Window can be created without its size explicitly set
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ShowWindowTest
 */

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ShowWindowTest implements ActionListener
{
    private static Window window;
    private static Button showButton;
    private static Button hideButton;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            1. You should see a Frame with a "Show" and a "Hide" button in it.
            2. Click on the "Show" button. A window with a "Hello World" Label
            should appear
            3. If the window does not appear, the test failed, otherwise passed.
            """;

        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(ShowWindowTest::createUI)
            .build()
            .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("ShowWindowTest");
        frame.setLayout(new FlowLayout());
        frame.setSize(100,100);
        frame.add(showButton = new Button("Show"));
        frame.add(hideButton = new Button("Hide"));

        ActionListener handler = new ShowWindowTest();
        showButton.addActionListener(handler);
        hideButton.addActionListener(handler);

        window = new Window(frame);
        window.add("Center", new Label("Hello World"));
        window.setLocationRelativeTo(null);
        return frame;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == showButton) {
            window.pack();
            window.setVisible(true);
        } else if (e.getSource() == hideButton)
            window.setVisible(false);
    }
}
