/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
  test
  @bug 5098433
  @summary REG: DnD of File-List between JVM is broken for non ASCII file names - Win32
  @author Denis Fokin: area=dnd
  @library    ../../regtesthelpers
  @library ../../regtesthelpers/process
  @build Util
  @build ProcessResults ProcessCommunicator


  @run applet/othervm DragUnicodeBetweenJVMTest.html
*/

/**
 * DragUnicodeBetweenJVMTest.java
 *
 * summary: The test drags a list of files (DataFlavor.javaFileListFlavor) from one jvm to another.
 *          The files have Unicode names. The list on target side must be equal to
 *          the list on the source side.
 */


import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;

import test.java.awt.regtesthelpers.process.ProcessCommunicator;
import test.java.awt.regtesthelpers.process.ProcessResults;
import test.java.awt.regtesthelpers.Util;
import static java.lang.Thread.sleep;

public class DragUnicodeBetweenJVMTest extends Applet
{

    public void init() {
        setLayout(new BorderLayout());
    }//End  init()

    public void start() {

        String toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        if (!toolkit.equals("sun.awt.windows.WToolkit")){
            System.out.println("This test is for Windows only. Passed.");
            return;
        }
        else{
            System.out.println("Toolkit = " + toolkit);
        }

        final Frame sourceFrame = new Frame("Source frame");
        final SourcePanel sourcePanel = new SourcePanel();
        sourceFrame.add(sourcePanel);
        sourceFrame.pack();
        sourceFrame.addWindowListener( new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sourceFrame.dispose();
            }
        });
        sourceFrame.setVisible(true);

        Util.waitForIdle(null);

        NextFramePositionCalculator positionCalculator = new NextFramePositionCalculator(sourceFrame);

        String [] args = new String [] {
                String.valueOf(positionCalculator.getNextLocationX()),
                String.valueOf(positionCalculator.getNextLocationY()),
                String.valueOf(AbsoluteComponentCenterCalculator.calculateXCenterCoordinate(sourcePanel)),
                String.valueOf(AbsoluteComponentCenterCalculator.calculateYCenterCoordinate(sourcePanel)),
        };


       ProcessResults processResults =
                // ProcessCommunicator.executeChildProcess(this.getClass()," -cp \"C:\\Documents and Settings\\df153228\\IdeaProjects\\UnicodeTestDebug\\out\\production\\UnicodeTestDebug\" -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 ", args);
                ProcessCommunicator.executeChildProcess(this.getClass(), args);

        verifyTestResults(processResults);

    }// start()



    private static void verifyTestResults(ProcessResults processResults) {
        if ( InterprocessMessages.FILES_ON_TARGET_ARE_CORRUPTED ==
                processResults.getExitValue())
        {
            processResults.printProcessErrorOutput(System.err);
            throw new RuntimeException("TEST IS FAILED: Target has recieved" +
                    " broken file list.");
        }
        processResults.verifyStdErr(System.err);
        processResults.verifyProcessExitValue(System.err);
        processResults.printProcessStandartOutput(System.out);
    }

    //We cannot make an instance of the applet without the default constructor
    public DragUnicodeBetweenJVMTest () {
        super();
    }

    //We need in this constructor to pass frame position between JVMs
    public DragUnicodeBetweenJVMTest (Point targetFrameLocation, Point dragSourcePoint)
            throws InterruptedException
    {
        final Frame targetFrame = new Frame("Target frame");
        final TargetPanel targetPanel = new TargetPanel(targetFrame);
        targetFrame.add(targetPanel);
        targetFrame.addWindowListener( new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                targetFrame.dispose();
            }
        });
        targetFrame.setLocation(targetFrameLocation);
        targetFrame.pack();
        targetFrame.setVisible(true);

        doTest(dragSourcePoint, targetPanel);
    }

    private void doTest(Point dragSourcePoint, TargetPanel targetPanel) {
        Util.waitForIdle(null);

        final Robot robot = Util.createRobot();

        robot.mouseMove((int)dragSourcePoint.getX(),(int)dragSourcePoint.getY());
        try {
            sleep(100);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            sleep(100);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Util.drag(robot, dragSourcePoint, new Point (AbsoluteComponentCenterCalculator.calculateXCenterCoordinate(targetPanel),
                AbsoluteComponentCenterCalculator.calculateYCenterCoordinate(targetPanel)),
                InputEvent.BUTTON1_MASK);
    }


    enum InterprocessArguments {
        TARGET_FRAME_X_POSITION_ARGUMENT,
        TARGET_FRAME_Y_POSITION_ARGUMENT,
        DRAG_SOURCE_POINT_X_ARGUMENT,
        DRAG_SOURCE_POINT_Y_ARGUMENT;

        int extract (String [] args) {
            return Integer.parseInt(args[this.ordinal()]);
        }
    }

    public static void main (String [] args) {
        Point dragSourcePoint = new Point(InterprocessArguments.DRAG_SOURCE_POINT_X_ARGUMENT.extract(args),
                InterprocessArguments.DRAG_SOURCE_POINT_Y_ARGUMENT.extract(args));
        Point targetFrameLocation = new Point(InterprocessArguments.TARGET_FRAME_X_POSITION_ARGUMENT.extract(args),
                InterprocessArguments.TARGET_FRAME_Y_POSITION_ARGUMENT.extract(args));

        try {
            new DragUnicodeBetweenJVMTest(targetFrameLocation, dragSourcePoint);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
