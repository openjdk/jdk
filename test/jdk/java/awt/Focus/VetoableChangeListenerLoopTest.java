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
  @bug        5074189
  @summary    Tests that VetoableChangeListener doesn't initiate infinite loop.
  @key headful
  @run main VetoableChangeListenerLoopTest
*/
import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

public class VetoableChangeListenerLoopTest {
    static Button b1;
    static Button b2;
    static Frame frame;
    static Robot robot;

    static int counter = 0;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            EventQueue.invokeAndWait(() -> {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().
                    addVetoableChangeListener(new VetoableChangeListener () {
                        public void vetoableChange(PropertyChangeEvent evt)
                                               throws PropertyVetoException {
                            if (b1.equals(evt.getNewValue())) {
                                System.out.println("VETOING: " + (counter++));
                                if (counter > 2) {
                                    throw new RuntimeException("Test failed!");
                                }
                                throw new PropertyVetoException("Change in property", evt);
                            }
                        }
                    });

                Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                    public void eventDispatched(AWTEvent e) {
                        System.out.println(e.toString());
                    }
                }, FocusEvent.FOCUS_EVENT_MASK | WindowEvent.WINDOW_FOCUS_EVENT_MASK);

                b1 = new Button("Button 1");
                b2 = new Button("Button 2");
                Frame frame = new Frame();
                frame.add(b1);
                frame.add(b2);
                frame.setSize(200, 100);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            robot.delay(1000);
            test();
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    static void test() {
        b2.requestFocusInWindow();
        waitTillFocus(b2);
        b2.setVisible(false);
    }


    static void waitTillFocus(Component comp) {
        while (!checkFocusOwner(comp)) {
            robot.delay(100);
        }
    }

    static boolean checkFocusOwner(Component comp) {
        return (comp == KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    }
}
