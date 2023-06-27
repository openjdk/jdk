/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4930685
  @summary Test effect of GridBagConstraints on padding / layout
  @key headful
  @run main GridBagLayoutButtonsOverlapTest
*/

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

public class GridBagLayoutButtonsOverlapTest {
    public static GridBagLayout gridbag;
    public static GridBagConstraints c;
    public static Button b1;
    public static Button b2;
    public static Button b4;
    public static Button b5;
    public static Button b6;
    public static Button b7;
    public static Button b8;
    public static Button b9;
    public static Button b10;
    public static Frame frame;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                createUI();
                test();
            } finally {
                if (frame != null) {
                    frame.dispose();
                }
            }
        });
    }

    public static void createUI() {
        frame = new Frame();
        gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        frame.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        frame.setLayout(gridbag);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        b1 = makeButton("button1", gridbag, c);
        b2 = makeButton("button2", gridbag, c);

        c.gridwidth = GridBagConstraints.REMAINDER;
        b4 = makeButton("button4", gridbag, c);

        c.weightx = 0.0;
        b5 = makeButton("button5", gridbag, c);

        c.gridwidth = GridBagConstraints.RELATIVE;
        b6 = makeButton("button6", gridbag, c);

        c.gridwidth = GridBagConstraints.REMAINDER;
        b7 = makeButton("button7", gridbag, c);

        c.gridwidth = 1;
        c.gridheight = 2;
        c.weighty = 1.0;
        b8 = makeButton("button8", gridbag, c);

        c.weighty = 0.0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.gridheight = 1;
        b9 = makeButton("button9", gridbag, c);
        b10 = makeButton("button10", gridbag, c);
    }

    public static void test() {
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();

        int b1Corner = b1.getLocation().y + b1.getHeight();
        int b5Corner = b5.getLocation().y;
        if (b1Corner > b5Corner) { //they are equals each other in best case
            throw new RuntimeException("Buttons are overlapped when container is small enough");
        }
        System.out.println("Test passed.");
    }

    protected static Button makeButton(String name,
                                 GridBagLayout gridbag,
                                 GridBagConstraints c) {
        Button button = new Button(name);
        gridbag.setConstraints(button, c);
        frame.add(button);
        return button;
    }

}// class
