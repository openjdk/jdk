/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8130390
   @summary Applet fails to launch on virtual desktop
   @author Semyon Sadetsky
  */

import sun.awt.AWTAccessor;

import java.applet.Applet;
import java.awt.*;

public class AppletFlipBuffer {
    public static void main(String[] args) throws Exception {
        Applet applet = new Applet();
        AWTAccessor.ComponentAccessor componentAccessor
                = AWTAccessor.getComponentAccessor();
        BufferCapabilities caps = new BufferCapabilities(
                new ImageCapabilities(true), new ImageCapabilities(true),
                BufferCapabilities.FlipContents.BACKGROUND);
        Frame frame = new Frame();
        try {
            frame.add(applet);
            frame.setUndecorated(true);
            frame.setVisible(true);
            componentAccessor.createBufferStrategy(applet, 2, caps);
            System.out.println("ok");
        }
        finally {
            frame.dispose();
        }
    }
}
