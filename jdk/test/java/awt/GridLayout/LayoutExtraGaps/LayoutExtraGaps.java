/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
   @test
   @bug 4370316
   @summary GridLayout does not fill its Container
   @library ../../regtesthelpers
   @build Util
   @author Andrei Dmitriev : area=awt.layout
   @run main LayoutExtraGaps
*/

import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.Util;

public class LayoutExtraGaps extends Frame {
    final static int compCount = 30;

    public LayoutExtraGaps() {
        super("GridLayoutTest");
        Panel yellowPanel = new Panel(new GridLayout(compCount, 1, 3, 3));
        yellowPanel.setBackground(Color.yellow);

        for(int i = 0; i < compCount ; i++) {
            Label redLabel = new Label(""+i);
            redLabel.setBackground(Color.red);
            yellowPanel.add(redLabel);
        }

        Panel bluePanel = new Panel(new GridLayout(1, compCount, 3, 3));
        bluePanel.setBackground(Color.blue);

        for(int i = 0; i < compCount; i++) {
            Label greenLabel = new Label(""+i);
            greenLabel.setBackground(Color.green);
            bluePanel.add(greenLabel);
        }

        //RTL orientation
        Panel blackPanel = new Panel(new GridLayout(compCount, 1, 3, 3));
        blackPanel.setBackground(Color.black);
        blackPanel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        for(int i = 0; i < compCount ; i++) {
            Label redLabel = new Label(""+i);
            redLabel.setBackground(Color.red);
            blackPanel.add(redLabel);
        }

        Panel redPanel = new Panel(new GridLayout(1, compCount, 3, 3));
        redPanel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        redPanel.setBackground(Color.red);

        for(int i = 0; i < compCount; i++) {
            Label greenLabel = new Label(""+i);
            greenLabel.setBackground(Color.green);
            redPanel.add(greenLabel);
        }

        setLayout(new GridLayout(2, 2, 20, 20));

        add(yellowPanel);
        add(bluePanel);
        add(redPanel);
        add(blackPanel);
        pack();
        setSize((int)getPreferredSize().getWidth() + 50, (int)getPreferredSize().getHeight() + 50);
        setVisible(true);

        Util.waitForIdle(Util.createRobot());
        Rectangle r1 = yellowPanel.getComponent(0).getBounds();
        Rectangle r2 = bluePanel.getComponent(0).getBounds();
        Rectangle r3 = blackPanel.getComponent(0).getBounds();
        Rectangle r4 = redPanel.getComponent(0).getBounds();

        System.out.println("firstHorizLabel bounds  ="+r1);
        System.out.println("firstVertLabel bounds ="+r2);
        System.out.println("firstHorizLabel_RTL bounds ="+r3);
        System.out.println("firstVertLabel_RTL bounds ="+r4);
        if ((r1.getX() == 0 && r1.getY() == 0) ||
            (r2.getX() == 0 && r2.getY() == 0) ||
            (r3.getX() == 0 && r3.getY() == 0) ||
            // RTL only affects horizontal positioning and components lays out from top right corner
            (r4.getX() == blackPanel.getWidth() && r4.getY() == 0))
        {
            throw new RuntimeException("Test failed. GridLayout doesn't center component.");
        } else {
            System.out.println("Test passed.");
        }
    }

    public static void main(String[] args) {
        new LayoutExtraGaps();
    }
}
