/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4209511
 * @summary Regression test DestroyMenuTest.java Failing
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DestroyMenuTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.TextField;

public class DestroyMenuTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Create many windows by randomly clicking 'Show Menu Test 1',
                  'Show Menu Test 2', 'Show Menu Test 3' buttons.
                2. Ignore the contents of the windows.
                   Go to the windows created and select menu items inside the menus.
                3. Close the windows by selecting menu item File--> Quit.
                4. Do the above menu item selections as quickly as possible.
                   If the program crashes when you select File--> Quit,
                   then the test FAILS. Otherwise the test is PASS.
                      """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(38)
                .testUI(DestroyMenuTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame f = new Frame("Destroy Menu Test");
        Button launchButton = new Button("Show Menu Test 1...");
        Button launchButton2 = new Button("Show Menu Test 2...");
        Button launchButton3 = new Button("Show Menu Test 3...");
        f.setLayout(new FlowLayout());
        f.add(launchButton);
        f.add(launchButton2);
        f.add(launchButton3);

        launchButton.addActionListener(event -> {
            MenuTest frame = new MenuTest("Menu Test 1");
            frame.setBounds(300, 300, 300, 300);
            frame.setVisible(true);
        });

        launchButton2.addActionListener(event -> {
            MenuTest frame = new MenuTest("Menu Test 2");

            Button closeButton = new Button("Close");

            Panel X = new Panel();
            X.setLayout(new BorderLayout());

            Panel topPanel = new Panel();
            Panel bottomPanel = new Panel();

            bottomPanel.add(closeButton);

            Scrollbar vScrollbar = new Scrollbar(Scrollbar.VERTICAL);
            Scrollbar hScrollbar = new Scrollbar(Scrollbar.HORIZONTAL);
            hScrollbar.setValues(hScrollbar.getValue(), 0, 0, 50);
            vScrollbar.setValues(vScrollbar.getValue(), 0, 0, 50);
            topPanel.setLayout(new BorderLayout());
            topPanel.add(vScrollbar, BorderLayout.EAST);
            topPanel.add(hScrollbar, BorderLayout.SOUTH);

            X.add(topPanel, BorderLayout.NORTH);
            X.add(bottomPanel, BorderLayout.SOUTH);
            frame.add(X, BorderLayout.SOUTH);
            frame.setBounds(350, 350, 300, 250);
            frame.setVisible(true);
        });

        launchButton3.addActionListener(event -> {
            MenuTest frame = new MenuTest("Menu Test 3");
            frame.setBounds(400, 400, 300, 300);

            mySimpleCanvas clock = new mySimpleCanvas();
            frame.add(clock, BorderLayout.CENTER);

            Panel p = new Panel();
            Button closeButton = new Button("Close");
            p.add(closeButton);

            p.add(new Label("Label"));
            TextField textField = new TextField(8);
            p.add(textField);
            f.add(p, BorderLayout.EAST);

            frame.add(p, BorderLayout.SOUTH);
            frame.setVisible(true);
        });
        f.pack();
        return f;
    }

    static class mySimpleCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
            g.drawOval(0, 0, 100, 100);
            g.drawOval(2, 2, 100, 100);
            g.drawOval(4, 4, 100, 100);
        }
    }
}
