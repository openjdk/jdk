/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.bugspot;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import sun.jvm.hotspot.ui.*;

/** The main class for the BugSpot debugger. */

public class Main {
  public static void main(String[] args) {
    JFrame frame = new JFrame("BugSpot");
    frame.setSize(800, 600);
    BugSpot db = new BugSpot();
    db.setMDIMode(true);
    db.build();
    frame.setJMenuBar(db.getMenuBar());
    frame.getContentPane().add(db);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    GraphicsUtilities.reshapeToAspectRatio(frame,
                                           4.0f/3.0f, 0.85f, Toolkit.getDefaultToolkit().getScreenSize());
    GraphicsUtilities.centerInContainer(frame,
                                        Toolkit.getDefaultToolkit().getScreenSize());
    frame.setVisible(true);
  }
}
