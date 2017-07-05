/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFrame;

/**
 * AWT/Swing overlapping test for {@link javax.swing.JCombobox } component.
 * <p>This test creates combobox and test if heavyweight component is drawn correctly then dropdown is shown.
 * <p>See base class for details.
 */
/*
@test
@summary Overlapping test for javax.swing.JScrollPane
@author sergey.grinev@oracle.com: area=awt.mixing
@run main JComboBoxOverlapping
 */
public class JComboBoxOverlapping extends OverlappingTestBase {

    private boolean lwClicked = false;
    private Point loc;
    private Point loc2;

    {testEmbeddedFrame = true;}

    protected void prepareControls() {
        final JFrame frame = new JFrame("Mixing : Dropdown Overlapping test");
        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
        frame.setSize(200, 200);
        frame.setVisible(true);

        final JComboBox cb = new JComboBox(petStrings);
        cb.setPreferredSize(new Dimension(frame.getContentPane().getWidth(), 20));
        cb.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == cb) {
                    lwClicked = true;
                }
            }
        });

        frame.add(cb);
        propagateAWTControls(frame);
        frame.setVisible(true);
        loc = cb.getLocationOnScreen();
        loc2 = frame.getContentPane().getLocationOnScreen();
    }

    @Override
    protected boolean performTest() {
        // run robot
        Robot robot = Util.createRobot();
        robot.setAutoDelay(ROBOT_DELAY);

        loc2.translate(75, 75);
        pixelPreCheck(robot, loc2, currentAwtControl);

        loc.translate(3, 3);
        clickAndBlink(robot, loc, false);

        clickAndBlink(robot, loc2, false);

        return lwClicked;
    }

    // this strange plumbing stuff is required due to "Standard Test Machinery" in base class
    public static void main(String args[]) throws InterruptedException {
        instance = new JComboBoxOverlapping();
        OverlappingTestBase.doMain(args);
    }
}
