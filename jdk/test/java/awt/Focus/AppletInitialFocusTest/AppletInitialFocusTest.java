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
  @bug     4041703 4096228 4025223 4260929
  @summary Ensures that appletviewer sets a reasonable default focus for an Applet on start
  @author  das area=appletviewer
  @run     applet AppletInitialFocusTest.html
*/

import java.applet.Applet;
import java.awt.Button;
import java.awt.Component;
import java.awt.Robot;
import java.awt.Window;
import test.java.awt.regtesthelpers.Util;

public class AppletInitialFocusTest extends Applet {
    Robot robot = Util.createRobot();
    Button button = new Button("Button");

    public void init() {
        add(button);
    }

    public void start() {
        new Thread(new Runnable() {
                public void run() {
                    Util.waitTillShown(button);
                    robot.delay(1000); // delay the thread to let EDT to start dispatching focus events
                    Util.waitForIdle(robot);
                    if (!button.hasFocus()) {
                        throw new RuntimeException("Appletviewer doesn't set default focus correctly.");
                    }
                }
            }).start();
    }
}
