/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6246503
  @summary Disabling a choice after selection locks keyboard, \
           mouse and makes the system unusable
  @key headful
  @run main OpenedChoiceHangs
*/

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

public class OpenedChoiceHangs implements ItemListener {
    static final Object FOCUS_LOCK = new Object();

    Frame frame;
    Choice ch = new Choice();
    Button b = new Button("A button");
    Robot robot;

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException {
        OpenedChoiceHangs openedChoiceHangs = new OpenedChoiceHangs();
        EventQueue.invokeAndWait(openedChoiceHangs::init);
        openedChoiceHangs.test();
    }

    public void init() {
        frame = new Frame();

        frame.setLayout(new FlowLayout());
        for (int i = 1; i < 10; i++) {
            ch.add("item " + i);
        }
        frame.add(ch);
        frame.add(b);
        ch.setBackground(new Color(255, 0, 0));
        ch.setForeground(new Color(255, 0, 0));
        ch.addItemListener(this);

        frame.setSize(200, 200);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
        frame.validate();
    }

    public void test() {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);
            robot.delay(1000);
            robot.mouseMove(ch.getLocationOnScreen().x + ch.getWidth() / 2,
                    ch.getLocationOnScreen().y + ch.getHeight() / 2);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(1000);
            if (!ch.isFocusOwner()) {
                synchronized (FOCUS_LOCK) {
                    FOCUS_LOCK.wait(3000);
                }
            }
            if (!ch.isFocusOwner()){
                throw new RuntimeException(
                        "Test failed. Choice has no focus after mouse press.");
            }
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.delay(1000);

            robot.keyPress(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_UP);
            robot.delay(1000);

            Color color = robot.getPixelColor(
                    ch.getLocationOnScreen().x + ch.getWidth() / 2,
                    ch.getLocationOnScreen().y + ch.getHeight() * 4);
            System.out.println("Color is " + color);
            if (color.equals(new Color(255, 0,0))){
                throw new RuntimeException(
                        "Test failed. Choice is disabled and still opened. ");
            }
        } catch (AWTException e) {
            throw new RuntimeException(
                    "Test interrupted due to AWTException. Robot=" + robot, e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Test interrupted. Robot=" + robot, e);
        } finally {
            EventQueue.invokeLater(frame::dispose);
        }

        System.out.println("Test passed: Choice became closed after disabling.");
    }

    public void itemStateChanged (ItemEvent ie) {
        System.out.println("Choice Item has changed: "+ie);
        ch.setEnabled(false);
    }
    public void focusGained(FocusEvent fEvent){
        System.out.println("focusGained"+fEvent);
        synchronized(FOCUS_LOCK){
            FOCUS_LOCK.notify();
        }
    }

    public void focusLost(FocusEvent fEvent){
        System.out.println("focusLost"+fEvent);
    }
}
