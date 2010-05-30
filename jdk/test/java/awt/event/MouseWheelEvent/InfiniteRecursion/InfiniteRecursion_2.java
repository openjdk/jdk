/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6480024
  @library ../../../regtesthelpers
  @build Util Sysout AbstractTest
  @summary stack overflow on mouse wheel rotation within Applet
  @author Andrei Dmitriev: area=awt.event
  @run applet InfiniteRecursion_2.html
*/

/**
 * InfiniteRecursion_2.java
 *
 * summary: put a JButton into JPanel and then put JPanel into Applet.
 * Add MouseWheelListener to Applet.
 * Add MouseListener to JPanel.
 * Rotating a wheel over the JButton would result in stack overflow.

 * summary: put a JButton into JApplet.
 * Add MouseWheelListener to JApplet.
 * Rotating a wheel over the JButton would result in stack overflow.
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import test.java.awt.regtesthelpers.Util;
import test.java.awt.regtesthelpers.AbstractTest;
import test.java.awt.regtesthelpers.Sysout;

import java.applet.Applet;

public class InfiniteRecursion_2 extends Applet {
    final static Robot robot = Util.createRobot();
    final static int MOVE_COUNT = 5;
    static int actualEvents = 0;

    public void init()
    {
        setLayout (new BorderLayout ());
    }//End  init()

    public void start ()
    {
        JPanel outputBox = new JPanel();
        JButton jButton = new JButton();

        this.setSize(200, 200);
        this.addMouseWheelListener(new MouseWheelListener() {
                public void mouseWheelMoved(MouseWheelEvent e)
                {
                    System.out.println("Wheel moved on APPLET : "+e);
                    actualEvents++;
                }
            });

        outputBox.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e)
                {
                    System.out.println("MousePressed on OUTBOX : "+e);
                }

            });
        this.add(outputBox);
        outputBox.add(jButton);

        this.setVisible(true);
        this.validate();


        Util.waitForIdle(robot);

        Util.pointOnComp(jButton, robot);
        Util.waitForIdle(robot);

        for (int i = 0; i < MOVE_COUNT; i++){
            robot.mouseWheel(1);
            robot.delay(10);
        }

        for (int i = 0; i < MOVE_COUNT; i++){
            robot.mouseWheel(-1);
            robot.delay(10);
        }

        Util.waitForIdle(robot);
        if (actualEvents != MOVE_COUNT * 2) {
            AbstractTest.fail("Expected events count: "+ MOVE_COUNT+" Actual events count: "+ actualEvents);
        }
    }// start()
}
