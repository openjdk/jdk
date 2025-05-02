/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4411368
  @summary tests the app doesn't crash if the child drop target is removed
           after the parent drop target is removed
  @key headful
  @run main RemoveParentChildDropTargetTest
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.lang.reflect.InvocationTargetException;


public class RemoveParentChildDropTargetTest {

    static Frame frame;
    static Panel panel;
    static Label label;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("RemoveParentChildDropTargetTest");
            panel = new Panel();
            label = new Label("Label");
            panel.add(label);
            frame.add(panel);
            frame.pack();

            panel.setDropTarget(new DropTarget(panel, new DropTargetAdapter() {
                public void drop(DropTargetDropEvent dtde) {}
            }));
            label.setDropTarget(new DropTarget(label, new DropTargetAdapter() {
                public void drop(DropTargetDropEvent dtde) {}
            }));
            panel.setDropTarget(null);
            frame.setVisible(true);

            label.setDropTarget(null);
        });

        EventQueue.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }
}
