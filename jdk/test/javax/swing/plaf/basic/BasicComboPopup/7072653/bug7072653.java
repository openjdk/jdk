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
 * @bug 7072653 8144161
 * @summary JComboBox popup mispositioned if its height exceeds the screen height
 * @run main bug7072653
 */
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Robot;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.Arrays;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class bug7072653 {

    private static JComboBox combobox;
    private static JFrame frame;
    private static Robot robot;
    private static volatile String errorString = "";

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.delay(100);
        UIManager.LookAndFeelInfo[] lookAndFeelArray
                = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo lookAndFeelItem : lookAndFeelArray) {
            executeCase(lookAndFeelItem.getClassName());
            robot.delay(1000);
        }
        if (!"".equals(errorString)) {

            throw new RuntimeException("Error Log:\n" + errorString);
        }
    }

    private static void executeCase(String lookAndFeelString) throws Exception {
        if (tryLookAndFeel(lookAndFeelString)) {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        setup(lookAndFeelString);
                        test();
                    } catch (Exception ex) {
                        errorString += "\n";
                        errorString += Arrays.toString(ex.getStackTrace());
                    }
                    finally {
                        frame.dispose();
                    }
                }
            });
        }

    }

    private static void setup(String lookAndFeelString)
            throws Exception {

        frame = new JFrame("JComboBox Test " + lookAndFeelString);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(320, 200);
        frame.getContentPane().setLayout(new FlowLayout());
        frame.setLocationRelativeTo(null);
        combobox = new JComboBox(new DefaultComboBoxModel() {
            @Override
            public Object getElementAt(int index) {
                return "Element " + index;
            }

            @Override
            public int getSize() {
                return 100;
            }
        });

        combobox.setMaximumRowCount(100);
        frame.getContentPane().add(combobox);
        frame.setVisible(true);
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
                GraphicsConfiguration gc
                        = combobox.getGraphicsConfiguration();
                Insets screenInsets = Toolkit.getDefaultToolkit()
                        .getScreenInsets(gc);
                int gcHeight = gc.getBounds().height;
                if (lookAndFeelString.contains("aqua")) {
                    gcHeight = gcHeight - screenInsets.top;
                    //For Aqua LAF
                } else {
                    gcHeight = gcHeight - screenInsets.top
                            - screenInsets.bottom;
                }
                if (height == gcHeight) {
                    return;
                }

                String exception = "Popup window height "
                        + "For LookAndFeel" + lookAndFeelString + " is wrong"
                        + "\nShould be " + height + "Actually " + gcHeight;
                errorString += exception;
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }

        });

    }

    private static void test() throws Exception {
        combobox.setPopupVisible(true);
        combobox.setPopupVisible(false);
    }

    private static boolean tryLookAndFeel(String lookAndFeelString)
            throws Exception {
        try {
            UIManager.setLookAndFeel(
                    lookAndFeelString);

        } catch (UnsupportedLookAndFeelException
                | ClassNotFoundException
                | InstantiationException
                | IllegalAccessException e) {
            return false;
        }
        return true;
    }
}
