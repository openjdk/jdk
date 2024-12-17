/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4177156
 * @key headful
 * @summary Tests that multiple level of window ownership doesn't cause
 * NullPointerException when showing a Window
 * @run main OwnedWindowShowTest
 */

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;

public class OwnedWindowShowTest {
    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(OwnedWindowShowTest::runTest);
    }

    static void runTest() {
        Frame parent = new Frame("OwnedWindowShowTest");
        try {
            Window owner = new Window(parent);
            Window window = new Window(owner);
            // Showing a window with multiple levels of ownership
            // should not throw NullPointerException
            window.setVisible(true);
        } finally {
            parent.dispose();
        }
    }
}
