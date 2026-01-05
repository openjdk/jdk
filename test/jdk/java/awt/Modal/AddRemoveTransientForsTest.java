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

import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/*
 * @test
 * @bug 6271779
 * @summary This test shows and hides a modal dialog several times without destroying its
 *          peer. Without the fix this may lead to application (or even WM) hang.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AddRemoveTransientForsTest
 */

public class AddRemoveTransientForsTest {

    private static Dialog d1;
    private static Dialog d2;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                When the test starts, a frame is shown with a button 'Show Dialog D1'.

                1. Press the button 'Show Dialog D1' to show a modal dialog D1 with a button
                'Show dialog D2'.

                2. Press the button 'Show dialog D2' to show another modal dialog D2 with a button
                'Close'.

                3. Press the button 'Close' to close dialog D2.

                4. Repeat steps 2 and 3 several times (at least 3-4 times).

                If the application is not hung, press Pass.

                NOTE: all the modal dialogs must be closed before pressing Pass button.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(AddRemoveTransientForsTest::init)
                .build()
                .awaitAndCheck();
    }

    public static Frame init() {
        Frame f = new Frame("AddRemoveTransientForsTest Frame");
        Button b = new Button("Show dialog D1");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                d1.setVisible(true);
            }
        });
        f.add(b);
        f.setSize(200, 100);

        WindowListener wl = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e)
            {
                e.getWindow().dispose();
            }
        };

        d1 = new Dialog(f, "D1", true);
        d1.setBounds(200, 200, 200, 100);
        d1.addWindowListener(wl);
        Button b1 = new Button("Show dialog D2");
        b1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                d2.setVisible(true);
            }
        });
        d1.add(b1);

        d2 = new Dialog(d1, "D2", true);
        d2.setBounds(300, 300, 200, 100);
        Button b2 = new Button("Close");
        b2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                d2.setVisible(false);
            }
        });
        d2.add(b2);

        return f;
    }
}
