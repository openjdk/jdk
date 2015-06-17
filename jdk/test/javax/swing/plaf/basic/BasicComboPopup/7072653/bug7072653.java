/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 7072653
   @summary JComboBox popup mispositioned if its height exceeds the screen height
   @author Semyon Sadetsky
  */


import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.Toolkit;

public class bug7072653 {

    private static JComboBox combobox;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frame = new JFrame("JComboBox Test");
                    setup(frame);
                }
            });
            test();
        }
        finally {
            frame.dispose();
        }

    }

    static void setup(JFrame frame)  {


        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setSize(320, 200);

        frame.getContentPane().setLayout(new FlowLayout());

        combobox = new JComboBox(new DefaultComboBoxModel() {
            public Object getElementAt(int index) { return "Element " + index; }
            public int getSize() {
                return 1000;
            }
        });


        combobox.setMaximumRowCount(100);
        frame.getContentPane().add(combobox);

        frame.setVisible(true);

    }

    static void test() throws Exception{
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                combobox.addPopupMenuListener(new PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                        int height = 0;
                        for (Window window : JFrame.getWindows()) {
                            if (Window.Type.POPUP == window.getType()) {
                                height = window.getSize().height;
                                break;
                            }
                        }
                        GraphicsConfiguration gc =
                                combobox.getGraphicsConfiguration();
                        Insets screenInsets = Toolkit.getDefaultToolkit()
                                .getScreenInsets(gc);

                        if (height == gc.getBounds().height - screenInsets.top -
                                screenInsets.bottom ) {
                            System.out.println("ok");
                            return;
                        }
                        throw new RuntimeException(
                                "Popup window height is wrong " + height);
                    }

                    @Override
                    public void popupMenuCanceled(PopupMenuEvent e) {
                    }
                });
                combobox.setPopupVisible(true);
                combobox.setPopupVisible(false);
            }
        });
    }


}
