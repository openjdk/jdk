/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6176814
  @summary  Metalworks frame maximizes after the move
  @author Andrei.Dmitriev area=Event
  @run applet MaximizedFrameTest.html
*/

import java.applet.Applet;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;

public class MaximizedFrameTest extends Applet
{
    final int ITERATIONS_COUNT = 20;
    Robot robot;
    Point framePosition;
    Point newFrameLocation;
    JFrame  frame;
    Rectangle gcBounds;
    public static Object LOCK = new Object();

    public void init()
    {
        String[] instructions =
        {
            "This is an AUTOMATIC test",
            "simply wait until it is done"
        };
        JFrame.setDefaultLookAndFeelDecorated(true);
        frame = new JFrame("JFrame Maximization Test");
        frame.pack();
        frame.setSize(450, 260);
    }//End  init()

    public void start ()
    {
        frame.setVisible(true);
        validate();
        JLayeredPane lPane = frame.getLayeredPane();
        //        System.out.println("JFrame's LayeredPane " + lPane );
        Component titleComponent = null;
        boolean titleFound = false;
        for (int j=0; j < lPane.getComponentsInLayer(JLayeredPane.FRAME_CONTENT_LAYER.intValue()).length; j++){
            titleComponent = lPane.getComponentsInLayer(JLayeredPane.FRAME_CONTENT_LAYER.intValue())[j];
            if (titleComponent.getClass().getName().equals("javax.swing.plaf.metal.MetalTitlePane")){
                titleFound = true;
                break;
            }
        }
        if ( !titleFound ){
            throw new RuntimeException("Test Failed. Unable to determine title's size.");
        }
        //--------------------------------
        // it is sufficient to get maximized Frame only once.
        Point tempMousePosition;
        framePosition = frame.getLocationOnScreen();
        try {
            robot = new Robot();
            tempMousePosition = new Point(framePosition.x +
                                          frame.getWidth()/2,
                                          framePosition.y +
                                          titleComponent.getHeight()/2);
            robot.mouseMove(tempMousePosition.x, tempMousePosition.y);
            for (int iteration=0; iteration < ITERATIONS_COUNT; iteration++){
                robot.mousePress(InputEvent.BUTTON1_MASK);
                gcBounds =
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0].getConfigurations()[0].getBounds();
                //Moving a mouse pointer less than a few pixels
                //leads to rising a double click event.
                //We have to use exceeded the AWT_MULTICLICK_SMUDGE
                //const value (which is 4 by default on GNOME) to test that.
                tempMousePosition.x += 5;
                robot.mouseMove(tempMousePosition.x, tempMousePosition.y);
                robot.delay(70);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                if ( frame.getExtendedState() != 0 ){
                    throw new RuntimeException ("Test failed. JFrame was maximized. ExtendedState is : "+frame.getExtendedState());
                }
                robot.delay(500);
            } //for iteration

            }catch(AWTException e) {
                throw new RuntimeException("Test Failed. AWTException thrown.");
            }
        System.out.println("Test passed.");
    }// start()
}// class
