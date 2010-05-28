/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6426186
  @summary XToolkit: List throws ArrayIndexOutOfBoundsException on double clicking when the List is empty
  @author Dmitry Cherepanov area=awt-list
  @library ../../regtesthelpers
  @build Util
  @run main ActionAfterRemove
*/

import java.awt.*;
import java.awt.event.*;
import sun.awt.SunToolkit;
import test.java.awt.regtesthelpers.Util;

public class ActionAfterRemove
{
    private static volatile boolean passed = true;

    // handle the uncaught exception
    public void handle(Throwable e) {
        e.printStackTrace();
        passed = false;
    }

    public static final void main(String args[])
    {
        final Frame frame = new Frame();
        final List list = new List();
        Robot robot = null;

        // In order to handle all uncaught exceptions in the EDT
        System.setProperty("sun.awt.exception.handler", "ActionAfterRemove");

        list.add("will be removed");
        frame.add(list);

        frame.setLayout(new FlowLayout());
        frame.setBounds(100,100,300,300);
        frame.setVisible(true);

        list.select(0);
        list.remove(0);

        try{
            robot = new Robot();
        }catch(AWTException e){
            throw new RuntimeException(e);
        }

        Util.clickOnComp(list, robot);
        ((SunToolkit)Toolkit.getDefaultToolkit()).realSync();
        Util.clickOnComp(list, robot);
        ((SunToolkit)Toolkit.getDefaultToolkit()).realSync();

        if (!passed){
            throw new RuntimeException("Test failed: exception was thrown on EDT.");
        }

    }//End  init()
}
