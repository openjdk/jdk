/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4530216
  @summary tests that DragSourceListeners are properly removed
  @key headful
  @run main RemoveDragSourceListenerTest
*/

import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;


public class RemoveDragSourceListenerTest {
    public static void main(String[] args) {
        class TestDragSourceAdapter extends DragSourceAdapter {}

        final DragSource dragSource = DragSource.getDefaultDragSource();

        final DragSourceAdapter listeners[] = {
                new TestDragSourceAdapter(),
                new TestDragSourceAdapter(),
                new TestDragSourceAdapter() // should be three or more listeners
        };

        for (int i = 0; i < listeners.length; i++) {
            dragSource.addDragSourceListener(listeners[i]);
        }

        DragSourceListener[] dragSourceListeners =
                dragSource.getDragSourceListeners();

        if (dragSourceListeners.length != listeners.length) {
            throw new RuntimeException("Unexpected length: " +
                    dragSourceListeners.length);
        }

        for (int i = 0; i < listeners.length; i++) {
            dragSource.removeDragSourceListener(listeners[i]);
        }

        for (int i = 0; i < listeners.length; i++) {
            dragSource.addDragSourceMotionListener(listeners[i]);
        }

        DragSourceMotionListener[] dragSourceMotionListeners =
                dragSource.getDragSourceMotionListeners();

        if (dragSourceMotionListeners.length != listeners.length) {
            throw new RuntimeException("Unexpected length: " +
                    dragSourceMotionListeners.length);
        }

        for (int i = 0; i < listeners.length; i++) {
            dragSource.removeDragSourceMotionListener(listeners[i]);
        }
    }
}
