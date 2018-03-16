/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary setLocationRelativeTo stopped working in Ubuntu 13.10 (Unity)
 * @key headful
 * @bug 8036915 8161273
 * @run main/othervm -Dsun.java2d.uiScale=1 GetScreenLocationTest
 * @run main/othervm -Dsun.java2d.uiScale=2 GetScreenLocationTest
 */
import java.awt.*;

public class GetScreenLocationTest {

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        Window frame = null;
        for(int i = 0; i < 30; i++) {
            if(frame != null) frame.dispose();
            frame = new Dialog((Frame)null);
            frame.setBounds(0, 0, 200, 100);
            frame.setVisible(true);
            robot.waitForIdle();
            robot.delay(200);
            frame.setLocation(321, 121);
            robot.waitForIdle();
            robot.delay(200);
            Dimension size = frame.getSize();
            if(size.width != 200 || size.height != 100) {
                frame.dispose();
                throw new RuntimeException("getSize() is wrong " + size);
            }
            Rectangle r = frame.getBounds();
            frame.dispose();
            if(r.x != 321 || r.y != 121) {
                throw new RuntimeException("getLocation() returns " +
                        "wrong coordinates " + r.getLocation());
            }
            if(r.width != 200 || r.height != 100) {
                throw new RuntimeException("getSize() is wrong " + r.getSize());
            }
        }
        System.out.println("ok");
    }

}
