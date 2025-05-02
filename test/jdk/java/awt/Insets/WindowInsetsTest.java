/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5089312
  @summary Bottom inset must not change after a second pack call.
  @key headful
  @run main WindowInsetsTest
*/
import java.awt.EventQueue;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JWindow;

public class WindowInsetsTest {
    static JFrame frame;
    static JWindow window;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                frame = new JFrame("Window Test");
                frame.setBounds(100, 100, 400, 300);
                frame.setVisible(true);

                JButton button = new JButton("A Button");
                window = new JWindow(frame);
                window.getContentPane().add(button);
                window.pack();
                window.setLocation(200, 200);
                window.show();
                double h0 = window.getSize().getHeight();
                window.pack();
                double h1 = window.getSize().getHeight();
                if( Math.abs(h1 - h0) > 0.5 ) {
                    throw new RuntimeException("Test failed: Bad insets.");
                }
                System.out.println("Test Passed.");
            } finally {
                if (window != null) {
                    window.dispose();
                }
                if (frame != null) {
                    frame.dispose();
                }
            }
        });
    }
}
