/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4343344
  @summary Tests key modifiers when ALT_GRAPH key is pressed by Robot.
  @key headful
  @requires (os.family != "mac")
  @run main AltGraphModifier
*/

import java.awt.AWTException;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;


public class AltGraphModifier {
    static Frame frame;

    static final int[] modifierKeys = {
        KeyEvent.VK_ALT_GRAPH
    };

    static final int[] inputMasks = {
        InputEvent.ALT_GRAPH_DOWN_MASK
    };

    static boolean[] modifierPress = new boolean[modifierKeys.length];

    static volatile boolean modKeys;
    static int modKeyCount;
    static volatile boolean failed = false;


    public static void main (String args[]) throws
            InterruptedException, InvocationTargetException, AWTException {

        EventQueue.invokeAndWait(() -> {
            frame = new Frame("Modifier Robot Key BUG");
            frame.setLayout(new FlowLayout());
            frame.setSize(200, 200);
            frame.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                }

                @Override
                public void keyPressed(KeyEvent kp){
                    System.out.println(kp);
                    if (modKeys == true) {
                        for (int i=0; i < modifierKeys.length; i++) {
                            if (modifierPress[i] == true) {
                                if ((kp.getModifiersEx() & inputMasks[i]) == inputMasks[i]) {
                                } else {
                                    failed = true;
                                }
                            }
                        }
                    }
                }

                @Override
                public void keyReleased(KeyEvent kr){
                }
            });
            frame.setBackground(Color.blue);
            frame.setVisible(true);
        });

        try {
            Robot robot = new Robot();

            robot.delay(1000);
            robot.mouseMove((int) (frame.getLocationOnScreen().getX()
                + frame.getWidth() / 2),
                (int) (frame.getLocationOnScreen().getY()
                + frame.getHeight() / 2));
            robot.delay(1000);
            robot.setAutoDelay(1000);

            //Imbed operations here
            modKeys = true;

            for (modKeyCount = 0; modKeyCount < modifierKeys.length; modKeyCount++) {
                //Press the Modifier Key
                modifierPress[modKeyCount] = true;
                robot.keyPress(modifierKeys[modKeyCount]);

                frame.requestFocus();
                robot.delay(1000);

                //Press the Modifier Key
                modifierPress[modKeyCount] = false;
                robot.keyRelease(modifierKeys[modKeyCount]);
                robot.delay(1000);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (failed) {
            throw new RuntimeException("FAIL MESSAGE ---- Modifier "
                    +" Mask is not set when the Key : "
                    +"AltGraph"
                    + " Key is pressed by Robot.\n");
        }
    }
}
