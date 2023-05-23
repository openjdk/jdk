/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4087971
  @summary Insets does not layout a component correctly
  @key headful
  @run main InsetsTest
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public class InsetsTest {
    private int leftInsetValue;
    private InsetsClass IC;

    public static void main(String[] args) throws Exception {
        InsetsTest test = new InsetsTest();
        test.start();
    }

    public void start() throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                IC = new InsetsClass();
                IC.setLayout(new BorderLayout());
                IC.setSize(200, 200);
                IC.setVisible(true);

                leftInsetValue = IC.returnLeftInset();
                if (leftInsetValue != 30) {
                    throw new RuntimeException("Test Failed - Left inset" +
                            "is not taken correctly");
                }
            } finally {
                if (IC != null) {
                    IC.dispose();
                }
            }
        });
    }
}

class InsetsClass extends JFrame {
    private int value;
    private JPanel panel;

    public InsetsClass() {
        super("TestFrame");
        setBackground(Color.lightGray);

        panel = new JPanel();
        panel.setBorder(new EmptyBorder(new Insets(30, 30, 30, 30)));
        panel.add(new JButton("Test Button"));

        getContentPane().add(panel);
        pack();
        setVisible(true);
    }

    public int returnLeftInset() {
        // Getting the left inset value
        Insets insets = panel.getInsets();
        value = insets.left;
        return value;
    }
}
