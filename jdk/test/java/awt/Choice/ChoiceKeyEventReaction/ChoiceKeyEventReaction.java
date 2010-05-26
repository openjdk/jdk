/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
  test
  @bug 6252982
  @summary PIT: Keyboard FocusTraversal not working when choice's drop-down is visible, on XToolkit
  @author andrei.dmitriev : area=awt.choice
  @run applet ChoiceKeyEventReaction.html
*/

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.Util;

public class ChoiceKeyEventReaction extends Applet
{
    Robot robot;
    Choice choice1 = new Choice();
    Point pt;
    TextField tf = new TextField("Hi");

    boolean keyTypedOnTextField = false;
    boolean itemChanged = false;
    String toolkit;

    public void init()
    {
        toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        System.out.println("Current toolkit is :" +toolkit);
        for (int i = 1; i<20; i++){
            choice1.add("item-0"+i);
        }
        tf.addKeyListener(new KeyAdapter(){
                public void keyPressed(KeyEvent ke) {
                    keyTypedOnTextField = true;
                    System.out.println(ke);
                }
            });


        choice1.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    itemChanged = true;
                    System.out.println(e);
                }
            });

        choice1.setFocusable(false);
        add(tf);
        add(choice1);
        setLayout (new FlowLayout());
    }//End  init()

    public void start ()
    {
        setSize (200,200);
        setVisible(true);
        validate();
        try{
            robot = new Robot();
            Util.waitForIdle(robot);
            moveFocusToTextField();
            testKeyOnChoice(InputEvent.BUTTON1_MASK, KeyEvent.VK_UP);
        } catch (Throwable e) {
            throw new RuntimeException("Test failed. Exception thrown: "+e);
        }
    }// start()

    public void testKeyOnChoice(int button, int key){
        pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        Util.waitForIdle(robot);
        robot.mousePress(button);
        robot.delay(10);
        robot.mouseRelease(button);
        Util.waitForIdle(robot);

        robot.keyPress(key);
        robot.keyRelease(key);

        Util.waitForIdle(robot);

        System.out.println("keyTypedOnTextField = "+keyTypedOnTextField +": itemChanged = " + itemChanged);

        if (itemChanged){
                throw new RuntimeException("Test failed. ItemChanged event occur on Choice.");
        }

        // We may just write
        // if (toolkit.equals("sun.awt.windows.WToolkit") == keyTypedOnTextField) {fail;}
        // but  must report differently in these cases so put two separate if statements for simplicity.
        if (toolkit.equals("sun.awt.windows.WToolkit") &&
            !keyTypedOnTextField)
        {
            throw new RuntimeException("Test failed. (Win32) KeyEvent wasn't addressed to TextField. ");
        }

        if (!toolkit.equals("sun.awt.windows.WToolkit") &&
            keyTypedOnTextField)
        {
            throw new RuntimeException("Test failed. (XToolkit/MToolkit). KeyEvent was addressed to TextField.");
        }

        System.out.println("Test passed. Unfocusable Choice doesn't react on keys.");
        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        Util.waitForIdle(robot);
    }

    public void moveFocusToTextField(){
        pt = tf.getLocationOnScreen();
        robot.mouseMove(pt.x + tf.getWidth()/2, pt.y + tf.getHeight()/2);
        Util.waitForIdle(robot);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.delay(10);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        Util.waitForIdle(robot);
    }
}//:~
